package af.muhsiny.docstudio

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.media3.common.util.UnstableApi
import java.util.Locale

@UnstableApi
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var repo: ProjectRepository
    private lateinit var current: DocumentaryProject
    private lateinit var exportEngine: ExportEngine

    private lateinit var panelHost: FrameLayout
    private lateinit var status: TextView
    private var projectTitle: EditText? = null
    private var script: EditText? = null
    private var projectSpinner: Spinner? = null
    private var scenePreview: TextView? = null
    private var mediaPreview: TextView? = null
    private var musicPreview: TextView? = null
    private var narrationPreview: TextView? = null
    private var voiceSpinner: Spinner? = null
    private var rateSeek: SeekBar? = null
    private var pitchSeek: SeekBar? = null
    private var exportProgress: ProgressBar? = null
    private var openOutputButton: Button? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val persianVoices = mutableListOf<android.speech.tts.Voice>()
    private var suppressProjectSelection = false

    private val pickMediaCode = 5101
    private val pickMusicCode = 5102
    private val pickNarrationCode = 5103

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 12, 17)
        window.navigationBarColor = Color.rgb(8, 12, 17)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        repo = ProjectRepository(this)
        current = repo.currentOrCreate()
        exportEngine = ExportEngine(this)
        setContentView(buildUi())
        tts = TextToSpeech(this, this)
        showPanel(0)
    }

    override fun onPause() {
        syncFieldsToProject()
        repo.save(current)
        super.onPause()
    }

    override fun onDestroy() {
        exportEngine.cancel()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 12, 17))
            setPadding(dp(12), dp(14), dp(12), dp(10))
        }
        root.addView(label("استدیوی مستند", 26, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        root.addView(label("نسخهٔ مستقل ۳ · پروژه، نریشن، رسانه و MP4 روی خود گوشی", 12, Color.rgb(165, 176, 187), false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(10))
        })

        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("پروژه", "صحنه‌ها", "رسانه", "صدا", "خروجی").forEachIndexed { index, title ->
            tabs.addView(tabButton(title) { showPanel(index) }, LinearLayout.LayoutParams(dp(106), dp(48)).apply { marginEnd = dp(6) })
        }
        tabScroll.addView(tabs)
        root.addView(tabScroll)

        panelHost = FrameLayout(this)
        root.addView(panelHost, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        status = label("آماده", 12, Color.rgb(224, 190, 112), true).apply { setPadding(dp(6), dp(8), dp(6), dp(4)) }
        root.addView(status)
        return root
    }

    private fun showPanel(index: Int) {
        syncFieldsToProject()
        repo.save(current)
        projectTitle = null
        script = null
        panelHost.removeAllViews()
        panelHost.addView(when (index) {
            0 -> projectPanel()
            1 -> scenesPanel()
            2 -> mediaPanel()
            3 -> voicePanel()
            else -> exportPanel()
        })
    }

    private fun projectPanel(): View = scrollPanel {
        addView(section("پروژه‌ها"))
        projectSpinner = Spinner(this@MainActivity)
        addView(projectSpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply { bottomMargin = dp(8) })
        refreshProjectSpinner()
        projectSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressProjectSelection) return
                val items = repo.list()
                val selected = items.getOrNull(position) ?: return
                if (selected.id != current.id) {
                    syncFieldsToProject()
                    repo.save(current)
                    current = selected
                    repo.setCurrent(current.id)
                    projectTitle?.setText(current.title)
                    script?.setText(current.script)
                    show("پروژه بارگذاری شد")
                }
            }
        }
        addView(row(
            button("پروژهٔ جدید") {
                syncFieldsToProject(); repo.save(current)
                current = repo.create("مستند جدید")
                showPanel(0)
            },
            button("حذف پروژه") {
                val old = current.id
                repo.delete(old)
                current = repo.currentOrCreate()
                showPanel(0)
            }
        ))

        addView(section("سناریو"))
        projectTitle = edit("نام مستند", false).apply { setText(current.title) }
        script = edit("متن کامل مستند را اینجا بنویس…", true).apply { setText(current.script); minLines = 12 }
        addView(projectTitle)
        addView(script)
        addView(button("ذخیرهٔ پروژه") {
            syncFieldsToProject(); repo.save(current); refreshProjectSpinner(); show("پروژه در فایل JSON داخلی ذخیره شد ✓")
        })
    }

    private fun scenesPanel(): View = scrollPanel {
        addView(section("صحنه‌بندی"))
        val scenes = ScenePlanner.scenes(current.script)
        addView(info("${scenes.size} صحنه از متن ساخته شده است. نریشن طولانی نیز خودکار به قطعات زیر ۳۰۰۰ کاراکتر شکسته می‌شود."))
        scenePreview = info(if (scenes.isEmpty()) "متنی برای صحنه‌بندی وجود ندارد." else scenes.joinToString("\n\n") { "${it.index}. ${it.text}" }).apply { minHeight = dp(280) }
        addView(scenePreview)
        addView(button("بازسازی صحنه‌ها") { showPanel(1) })
    }

    private fun mediaPanel(): View = scrollPanel {
        addView(section("عکس و ویدیو"))
        addView(button("افزودن چند عکس یا ویدیو") { pickMedia() })
        addView(button("حذف همهٔ رسانه‌ها") {
            current.mediaUris.clear(); repo.save(current); updateMediaPreview(); show("رسانه‌ها پاک شدند")
        })
        mediaPreview = info("")
        addView(mediaPreview)
        updateMediaPreview()

        addView(section("موسیقی پس‌زمینه"))
        addView(row(button("انتخاب موزیک") { pickMusic() }, button("حذف موزیک") {
            current.musicUri = null; repo.save(current); updateMusicPreview(); show("موزیک حذف شد")
        }))
        musicPreview = info("")
        addView(musicPreview)
        updateMusicPreview()
    }

    private fun voicePanel(): View = scrollPanel {
        addView(section("نریشن"))
        narrationPreview = info("")
        addView(narrationPreview)
        updateNarrationPreview()
        addView(row(button("انتخاب MP3/WAV آماده") { pickNarration() }, button("حذف نریشن آماده") {
            current.narrationUri = null; repo.save(current); updateNarrationPreview(); show("نریشن آماده حذف شد")
        }))

        addView(section("نریشن فارسی Android"))
        addView(info("اگر فایل نریشن آماده انتخاب نشده باشد، متن با TTS خود گوشی ساخته می‌شود. متن بلند به چند قطعه تقسیم می‌شود تا از سقف TextToSpeech عبور نکند."))
        voiceSpinner = Spinner(this@MainActivity)
        addView(voiceSpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(8) })
        refreshVoiceSpinner()
        addView(label("سرعت", 13, Color.WHITE, true))
        rateSeek = SeekBar(this@MainActivity).apply { max = 80; progress = 32 }
        addView(rateSeek)
        addView(label("زیر و بمی", 13, Color.WHITE, true))
        pitchSeek = SeekBar(this@MainActivity).apply { max = 80; progress = 40 }
        addView(pitchSeek)
        addView(row(button("تست صدا") { previewVoice() }, button("تازه‌سازی صداها") { loadPersianVoices() }))
    }

    private fun exportPanel(): View = scrollPanel {
        addView(section("خروجی MP4"))
        addView(info("مسیر واقعی: نریشن آماده یا TTS تکه‌ای → تصویر/ویدیو → موزیک اختیاری → Media3 Transformer → H.264/AAC → Movies/DocStudio. زیرنویس SRT نیز بر اساس طول واقعی نریشن ساخته می‌شود."))
        exportProgress = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        addView(exportProgress)
        addView(button("ساخت مستند روی همین گوشی") { startExport() }.apply { textSize = 17f; minHeight = dp(66) })
        openOutputButton = button("باز کردن آخرین MP4") { openLastOutput() }.apply {
            isEnabled = !current.lastOutputUri.isNullOrBlank()
            alpha = if (isEnabled) 1f else .45f
        }
        addView(openOutputButton)
    }

    override fun onInit(initStatus: Int) {
        if (initStatus == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.setLanguage(Locale("fa", "IR"))
            loadPersianVoices()
            show("موتور گفتار Android آماده است")
        } else {
            ttsReady = false
            show("موتور گفتار Android راه‌اندازی نشد")
        }
    }

    private fun loadPersianVoices() {
        persianVoices.clear()
        persianVoices += tts?.voices.orEmpty().filter { it.locale.language.equals("fa", true) }.sortedBy { it.name }
        refreshVoiceSpinner()
        if (persianVoices.isNotEmpty()) show("${persianVoices.size} صدای فارسی روی گوشی پیدا شد")
    }

    private fun refreshVoiceSpinner() {
        val spinner = voiceSpinner ?: return
        val labels = if (persianVoices.isEmpty()) listOf("صدای پیش‌فرض فارسی Android") else persianVoices.map { "${it.name} · ${it.locale.displayName}" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
    }

    private fun applyVoiceSettings() {
        val engine = tts ?: return
        val spinner = voiceSpinner
        if (persianVoices.isNotEmpty() && spinner != null) persianVoices.getOrNull(spinner.selectedItemPosition)?.let { engine.voice = it }
        else engine.language = Locale("fa", "IR")
        val rate = 0.65f + ((rateSeek?.progress ?: 32) / 80f) * 0.75f
        val pitch = 0.75f + ((pitchSeek?.progress ?: 40) / 80f) * 0.55f
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)
    }

    private fun previewVoice() {
        if (!ttsReady) return show("TTS آماده نیست")
        applyVoiceSettings()
        val sample = current.script.ifBlank { "این یک نمونهٔ نریشن فارسی برای مستند است. روایت باید آرام، روشن و طبیعی شنیده شود." }.take(420)
        tts?.speak(sample, TextToSpeech.QUEUE_FLUSH, Bundle(), "preview")
        show("پخش آزمایشی…")
    }

    private fun startExport() {
        syncFieldsToProject(); repo.save(current)
        if (current.script.isBlank()) return show("متن مستند خالی است")
        if (current.mediaUris.isEmpty()) return show("حداقل یک عکس یا ویدیو انتخاب کن")
        exportProgress?.progress = 1

        val external = current.narrationUri?.let(Uri::parse)
        if (external != null) {
            val duration = NarrationEngine.audioDurationMs(this, external)
            if (duration <= 0) return show("مدت نریشن آماده قابل خواندن نیست")
            beginExport(listOf(external), duration)
            return
        }

        if (!ttsReady || tts == null) return show("TTS فارسی آماده نیست؛ یک MP3/WAV نریشن انتخاب کن")
        applyVoiceSettings()
        show("در حال ساخت نریشن تکه‌ای…")
        NarrationEngine(this, tts!!).synthesize(
            current.script,
            onProgress = { done, total -> runOnUiThread {
                exportProgress?.progress = (done * 25 / total).coerceAtLeast(2)
                show("نریشن: $done از $total")
            }},
            onDone = { result -> runOnUiThread { beginExport(result.uris, result.totalDurationMs) } },
            onError = { message -> runOnUiThread { exportProgress?.progress = 0; show(message) } }
        )
    }

    private fun beginExport(narrationUris: List<Uri>, narrationDurationMs: Long) {
        saveSrt(narrationDurationMs)
        show("نریشن آماده شد؛ رندر ویدیو آغاز شد…")
        exportEngine.export(
            current,
            narrationUris,
            narrationDurationMs,
            onProgress = { value -> runOnUiThread { exportProgress?.progress = value.coerceAtLeast(25) } },
            onDone = { uri -> runOnUiThread {
                current.lastOutputUri = uri.toString(); repo.save(current)
                exportProgress?.progress = 100
                openOutputButton?.isEnabled = true; openOutputButton?.alpha = 1f
                show("MP4 در Movies/DocStudio ذخیره شد ✓")
            }},
            onError = { message -> runOnUiThread { exportProgress?.progress = 0; show("رندر ناموفق: $message") } }
        )
    }

    private fun saveSrt(totalDurationMs: Long) {
        val srt = ScenePlanner.buildSrt(current.script, totalDurationMs)
        if (srt.isBlank()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName(current.title) + ".srt")
                    put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocStudio")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(srt) }
            } else {
                val f = java.io.File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), safeName(current.title) + ".srt")
                f.writeText(srt, Charsets.UTF_8)
            }
        }
    }

    private fun pickMedia() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, pickMediaCode)
    }

    private fun pickMusic() = startActivityForResult(audioIntent(), pickMusicCode)
    private fun pickNarration() = startActivityForResult(audioIntent(), pickNarrationCode)

    private fun audioIntent() = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "audio/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            pickMediaCode -> {
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) addMediaUri(clip.getItemAt(i).uri)
                } ?: data.data?.let(::addMediaUri)
                repo.save(current); updateMediaPreview(); show("${current.mediaUris.size} رسانه در پروژه است")
            }
            pickMusicCode -> data.data?.let {
                persistPermission(it); current.musicUri = it.toString(); repo.save(current); updateMusicPreview(); show("موزیک انتخاب شد")
            }
            pickNarrationCode -> data.data?.let {
                persistPermission(it); current.narrationUri = it.toString(); repo.save(current); updateNarrationPreview(); show("نریشن آماده انتخاب شد")
            }
        }
    }

    private fun addMediaUri(uri: Uri) {
        persistPermission(uri)
        val s = uri.toString()
        if (s !in current.mediaUris) current.mediaUris += s
    }

    private fun persistPermission(uri: Uri) {
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun refreshProjectSpinner() {
        val spinner = projectSpinner ?: return
        val items = repo.list()
        suppressProjectSelection = true
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items.map { it.title.ifBlank { "بدون عنوان" } })
        val pos = items.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        spinner.setSelection(pos, false)
        suppressProjectSelection = false
    }

    private fun syncFieldsToProject() {
        projectTitle?.let { current.title = it.text.toString().ifBlank { "مستند جدید" } }
        script?.let { current.script = it.text.toString() }
    }

    private fun updateMediaPreview() {
        mediaPreview?.text = if (current.mediaUris.isEmpty()) "هیچ رسانه‌ای انتخاب نشده است." else current.mediaUris.mapIndexed { i, raw -> "${i + 1}. ${displayName(Uri.parse(raw))}" }.joinToString("\n")
    }

    private fun updateMusicPreview() {
        musicPreview?.text = current.musicUri?.let { "موزیک: ${displayName(Uri.parse(it))}" } ?: "موزیک انتخاب نشده است."
    }

    private fun updateNarrationPreview() {
        narrationPreview?.text = current.narrationUri?.let {
            val uri = Uri.parse(it)
            val duration = NarrationEngine.audioDurationMs(this, uri)
            "نریشن آماده: ${displayName(uri)} · ${duration / 1000} ثانیه"
        } ?: "نریشن آماده انتخاب نشده؛ هنگام خروجی از TTS فارسی گوشی استفاده می‌شود."
    }

    private fun openLastOutput() {
        val uri = current.lastOutputUri?.let(Uri::parse) ?: return show("هنوز خروجی وجود ندارد")
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure { show("باز کردن ویدیو ممکن نشد") }
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) return c.getString(i)
            }
        }
        return uri.lastPathSegment ?: "media"
    }

    private fun scrollPanel(builder: LinearLayout.() -> Unit): View {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(24))
            builder()
        }
        scroll.addView(box)
        return scroll
    }

    private fun tabButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 13f; isAllCaps = false
        setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(25, 35, 44)); setOnClickListener { action() }
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 14f; isAllCaps = false
        setTextColor(Color.rgb(8, 13, 18)); setBackgroundColor(Color.rgb(221, 186, 108)); setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply { bottomMargin = dp(8) }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { index, view -> addView(view, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
            if (index == 0) marginEnd = dp(4) else marginStart = dp(4); bottomMargin = dp(8)
        }) }
    }

    private fun edit(hint: String, multi: Boolean) = EditText(this).apply {
        this.hint = hint; setHintTextColor(Color.rgb(112, 124, 136)); setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(20, 28, 36)); setPadding(dp(13), dp(12), dp(13), dp(12)); gravity = Gravity.TOP or Gravity.START
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, if (multi) dp(270) else dp(56)).apply { bottomMargin = dp(9) }
    }

    private fun section(text: String) = label(text, 17, Color.rgb(221, 186, 108), true).apply { setPadding(0, dp(10), 0, dp(8)) }

    private fun info(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(Color.rgb(223, 229, 234)); setBackgroundColor(Color.rgb(20, 28, 36))
        setPadding(dp(13), dp(12), dp(13), dp(12)); gravity = Gravity.START
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(9) }
    }

    private fun label(text: String, sp: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = sp.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun safeName(text: String) = text.replace(Regex("[^\\p{L}\\p{N}_ -]"), "").trim().replace(' ', '_').take(60).ifBlank { "Mostanad" }
    private fun show(text: String) { status.text = text }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
