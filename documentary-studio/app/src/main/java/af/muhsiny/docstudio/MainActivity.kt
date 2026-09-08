package af.muhsiny.docstudio

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
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
    private lateinit var pocketNarration: PersianPocketNarration

    private lateinit var panelHost: FrameLayout
    private lateinit var status: TextView
    private var projectTitle: EditText? = null
    private var script: EditText? = null
    private var projectSpinner: Spinner? = null
    private var mediaPreview: TextView? = null
    private var musicPreview: TextView? = null
    private var narrationPreview: TextView? = null
    private var clonePreview: TextView? = null
    private var modelStatus: TextView? = null
    private var voiceModeGroup: RadioGroup? = null
    private var voiceSpinner: Spinner? = null
    private var rateSeek: SeekBar? = null
    private var pitchSeek: SeekBar? = null
    private var exportProgress: ProgressBar? = null
    private var openOutputButton: Button? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val persianVoices = mutableListOf<android.speech.tts.Voice>()
    private var player: MediaPlayer? = null
    private var suppressProjectSelection = false

    private val pickMediaCode = 5101
    private val pickMusicCode = 5102
    private val pickNarrationCode = 5103
    private val pickCloneVoiceCode = 5104

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 12, 17)
        window.navigationBarColor = Color.rgb(8, 12, 17)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        repo = ProjectRepository(this)
        current = repo.currentOrCreate()
        exportEngine = ExportEngine(this)
        pocketNarration = PersianPocketNarration(this)
        setContentView(buildUi())
        tts = TextToSpeech(this, this)
        showPanel(0)
    }

    override fun onPause() {
        syncFieldsToProject(); repo.save(current); super.onPause()
    }

    override fun onDestroy() {
        pocketNarration.cancel(); exportEngine.cancel(); player?.release(); player = null
        tts?.stop(); tts?.shutdown(); super.onDestroy()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 12, 17))
            setPadding(dp(12), dp(14), dp(12), dp(10))
        }
        root.addView(label("استدیوی مستند", 26, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        root.addView(label("نسخهٔ ۵ · مستقل · کلون فارسی روی خود دستگاه · رندر MP4", 12, Color.rgb(165,176,187), false).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(3), 0, dp(10))
        })
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("پروژه", "صحنه‌ها", "رسانه", "صدا", "خروجی").forEachIndexed { index, title ->
            tabs.addView(tabButton(title) { showPanel(index) }, LinearLayout.LayoutParams(dp(106), dp(48)).apply { marginEnd = dp(6) })
        }
        tabScroll.addView(tabs); root.addView(tabScroll)
        panelHost = FrameLayout(this)
        root.addView(panelHost, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        status = label("آماده", 12, Color.rgb(224,190,112), true).apply { setPadding(dp(6), dp(8), dp(6), dp(4)) }
        root.addView(status)
        return root
    }

    private fun showPanel(index: Int) {
        syncFieldsToProject(); repo.save(current)
        projectTitle = null; script = null
        panelHost.removeAllViews()
        panelHost.addView(when (index) {
            0 -> projectPanel(); 1 -> scenesPanel(); 2 -> mediaPanel(); 3 -> voicePanel(); else -> exportPanel()
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
                val selected = repo.list().getOrNull(position) ?: return
                if (selected.id != current.id) {
                    syncFieldsToProject(); repo.save(current)
                    current = selected; repo.setCurrent(current.id)
                    projectTitle?.setText(current.title); script?.setText(current.script)
                    show("پروژه بارگذاری شد")
                }
            }
        }
        addView(row(
            button("پروژهٔ جدید") { syncFieldsToProject(); repo.save(current); current = repo.create(); showPanel(0) },
            button("حذف پروژه") { repo.delete(current.id); current = repo.currentOrCreate(); showPanel(0) }
        ))
        addView(section("سناریو"))
        projectTitle = edit("نام مستند", false).apply { setText(current.title) }
        script = edit("متن کامل مستند را اینجا بنویس…", true).apply { setText(current.script); minLines = 12 }
        addView(projectTitle); addView(script)
        addView(button("ذخیرهٔ پروژه") { syncFieldsToProject(); repo.save(current); refreshProjectSpinner(); show("پروژه ذخیره شد ✓") })
    }

    private fun scenesPanel(): View = scrollPanel {
        addView(section("صحنه‌بندی"))
        val scenes = ScenePlanner.scenes(current.script)
        addView(info("${scenes.size} صحنه. برای نریشن داخلی، متن به قطعه‌های کوتاه مناسب PocketTTS تقسیم می‌شود."))
        addView(info(if (scenes.isEmpty()) "متنی برای صحنه‌بندی وجود ندارد." else scenes.joinToString("\n\n") { "${it.index}. ${it.text}" }).apply { minHeight = dp(280) })
        addView(button("بازسازی صحنه‌ها") { showPanel(1) })
    }

    private fun mediaPanel(): View = scrollPanel {
        addView(section("عکس و ویدیو"))
        addView(button("افزودن چند عکس یا ویدیو") { pickMedia() })
        addView(button("حذف همهٔ رسانه‌ها") { current.mediaUris.clear(); repo.save(current); updateMediaPreview(); show("رسانه‌ها پاک شدند") })
        mediaPreview = info(""); addView(mediaPreview); updateMediaPreview()
        addView(section("موسیقی پس‌زمینه"))
        addView(row(button("انتخاب موزیک") { pickMusic() }, button("حذف موزیک") { current.musicUri = null; repo.save(current); updateMusicPreview() } ))
        musicPreview = info(""); addView(musicPreview); updateMusicPreview()
    }

    private fun voicePanel(): View = scrollPanel {
        addView(section("حالت نریشن"))
        voiceModeGroup = RadioGroup(this@MainActivity).apply { orientation = RadioGroup.VERTICAL }
        val modes = listOf(
            "pocket_default" to "راوی فارسی داخلی · PocketTTS",
            "pocket_clone" to "کلون از نمونهٔ WAV · روی همین گوشی",
            "android_tts" to "TTS فارسی Android · پشتیبان",
            "external_audio" to "نریشن MP3/WAV آماده"
        )
        modes.forEachIndexed { index, (key, text) ->
            val rb = RadioButton(this@MainActivity).apply {
                id = 7000 + index; this.text = text; setTextColor(Color.WHITE); isChecked = current.voiceMode == key
            }
            voiceModeGroup?.addView(rb)
        }
        voiceModeGroup?.setOnCheckedChangeListener { _, checked ->
            val i = (checked - 7000).coerceIn(0, modes.lastIndex)
            current.voiceMode = modes[i].first; repo.save(current); show("حالت صدا: ${modes[i].second}")
        }
        addView(voiceModeGroup)

        addView(section("موتور فارسی داخلی"))
        modelStatus = info(""); addView(modelStatus); updateModelStatus()
        addView(row(
            button("آماده‌سازی مدل فارسی") {
                show("در حال استخراج مدل فارسی داخل گوشی…")
                pocketNarration.prepare({ updateModelStatus(); show("مدل فارسی روی دستگاه آماده شد ✓") }, { show(it); updateModelStatus() })
            },
            button("تست راوی داخلی") { previewPocket(false) }
        ))

        addView(section("کلون صدا"))
        addView(info("یک نمونهٔ تمیز WAV، یک گوینده، بدون موزیک؛ حدود ۲ تا ۵ ثانیه. کلون و تولید صدا روی خود دستگاه انجام می‌شود."))
        clonePreview = info(""); addView(clonePreview); updateClonePreview()
        addView(row(button("انتخاب نمونه WAV") { pickCloneVoice() }, button("حذف نمونه") { current.cloneReferenceUri = null; repo.save(current); updateClonePreview() }))
        addView(button("تست کلون فارسی روی گوشی") { previewPocket(true) })

        addView(section("نریشن آماده"))
        narrationPreview = info(""); addView(narrationPreview); updateNarrationPreview()
        addView(row(button("انتخاب MP3/WAV آماده") { pickNarration() }, button("حذف نریشن آماده") { current.narrationUri = null; repo.save(current); updateNarrationPreview() }))

        addView(section("TTS فارسی Android"))
        voiceSpinner = Spinner(this@MainActivity); addView(voiceSpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
        refreshVoiceSpinner()
        addView(label("سرعت", 13, Color.WHITE, true)); rateSeek = SeekBar(this@MainActivity).apply { max = 80; progress = 32 }; addView(rateSeek)
        addView(label("زیر و بمی", 13, Color.WHITE, true)); pitchSeek = SeekBar(this@MainActivity).apply { max = 80; progress = 40 }; addView(pitchSeek)
        addView(button("تست صدای Android") { previewAndroidVoice() })
    }

    private fun exportPanel(): View = scrollPanel {
        addView(section("خروجی واقعی MP4"))
        addView(info("رسانهٔ انتخابی + نریشن انتخاب‌شده + موزیک اختیاری → Media3 Transformer → H.264/AAC → Movies/DocStudio. هیچ موتور کامپیوتری در مسیر نیست."))
        addView(info("حالت فعلی صدا: ${voiceModeLabel(current.voiceMode)}\nرسانه: ${current.mediaUris.size}\nموزیک: ${if (current.musicUri != null) "دارد" else "ندارد"}"))
        exportProgress = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        addView(exportProgress)
        addView(button("ساخت مستند روی همین گوشی") { startExport() }.apply { textSize = 17f; minHeight = dp(66) })
        openOutputButton = button("باز کردن آخرین MP4") { openLastOutput() }.apply {
            isEnabled = !current.lastOutputUri.isNullOrBlank(); alpha = if (isEnabled) 1f else .45f
        }
        addView(openOutputButton)
    }

    override fun onInit(initStatus: Int) {
        if (initStatus == TextToSpeech.SUCCESS) {
            ttsReady = true; tts?.setLanguage(Locale("fa", "IR")); loadPersianVoices()
        } else ttsReady = false
    }

    private fun previewPocket(clone: Boolean) {
        if (clone && current.cloneReferenceUri.isNullOrBlank()) return show("اول نمونهٔ WAV را انتخاب کن")
        val text = current.script.ifBlank { "در این مستند، روایت را آرام و روشن دنبال می‌کنیم تا تصویر دقیق‌تری از واقعیت به دست آید." }.take(450)
        show("در حال تولید نمونهٔ فارسی روی خود گوشی…")
        pocketNarration.synthesize(
            text,
            if (clone) Uri.parse(current.cloneReferenceUri) else null,
            { done, total -> show("تولید صدا: $done از $total") },
            { result ->
                val uri = result.uris.firstOrNull() ?: return@synthesize show("فایل نمونه ساخته نشد")
                player?.release(); player = MediaPlayer.create(this, uri); player?.start(); show("نمونهٔ فارسی تولید شد ✓")
            },
            { show(it) }
        )
    }

    private fun previewAndroidVoice() {
        if (!ttsReady) return show("TTS Android آماده نیست")
        applyAndroidVoiceSettings()
        val sample = current.script.ifBlank { "این یک نمونهٔ نریشن فارسی برای مستند است." }.take(420)
        tts?.speak(sample, TextToSpeech.QUEUE_FLUSH, Bundle(), "preview")
    }

    private fun startExport() {
        syncFieldsToProject(); repo.save(current)
        if (current.script.isBlank()) return show("متن مستند خالی است")
        if (current.mediaUris.isEmpty()) return show("حداقل یک عکس یا ویدیو اضافه کن")
        exportProgress?.progress = 0
        when (current.voiceMode) {
            "external_audio" -> {
                val raw = current.narrationUri ?: return show("نریشن آماده انتخاب نشده")
                val uri = Uri.parse(raw); val duration = NarrationEngine.audioDurationMs(this, uri)
                if (duration <= 0) show("مدت نریشن آماده خوانده نشد") else renderWithNarration(listOf(uri), duration)
            }
            "pocket_clone" -> {
                val ref = current.cloneReferenceUri ?: return show("نمونهٔ WAV برای کلون انتخاب نشده")
                synthPocketForExport(Uri.parse(ref))
            }
            "pocket_default" -> synthPocketForExport(null)
            else -> synthAndroidForExport()
        }
    }

    private fun synthPocketForExport(reference: Uri?) {
        show("در حال تولید نریشن فارسی روی دستگاه…")
        pocketNarration.synthesize(
            current.script,
            reference,
            { done, total -> exportProgress?.progress = ((done * 30f / total).toInt()).coerceIn(1, 30); show("نریشن: $done/$total") },
            { result -> renderWithNarration(result.uris, result.totalDurationMs) },
            { show(it); exportProgress?.progress = 0 }
        )
    }

    private fun synthAndroidForExport() {
        val engine = tts ?: return show("TTS Android موجود نیست")
        if (!ttsReady) return show("TTS Android آماده نیست")
        applyAndroidVoiceSettings()
        show("در حال ساخت نریشن Android…")
        NarrationEngine(this, engine).synthesize(
            current.script,
            { done, total -> runOnUiThread { exportProgress?.progress = ((done * 30f / total).toInt()).coerceIn(1, 30) } },
            { result -> runOnUiThread { renderWithNarration(result.uris, result.totalDurationMs) } },
            { message -> runOnUiThread { show(message); exportProgress?.progress = 0 } }
        )
    }

    private fun renderWithNarration(uris: List<Uri>, duration: Long) {
        saveSrt(ScenePlanner.buildSrt(current.script, duration), current.title)
        show("در حال رندر MP4…")
        exportEngine.export(
            current,
            uris,
            duration,
            { p -> runOnUiThread { exportProgress?.progress = (30 + p * 70 / 100).coerceIn(30, 99) } },
            { uri -> runOnUiThread {
                current.lastOutputUri = uri.toString(); repo.save(current); exportProgress?.progress = 100
                openOutputButton?.isEnabled = true; openOutputButton?.alpha = 1f; show("MP4 ساخته و در Movies/DocStudio ذخیره شد ✓")
            } },
            { message -> runOnUiThread { exportProgress?.progress = 0; show("خطای رندر: $message") } }
        )
    }

    private fun saveSrt(text: String, title: String) {
        if (text.isBlank()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName(title) + ".srt")
                    put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocStudio")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
            }
        }
    }

    private fun pickMedia() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*")); addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(i, pickMediaCode)
    }

    private fun pickMusic() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE) }, pickMusicCode)
    private fun pickNarration() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE) }, pickNarrationCode)
    private fun pickCloneVoice() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "audio/wav"; putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/wav", "audio/x-wav", "audio/wave")); addCategory(Intent.CATEGORY_OPENABLE)
    }, pickCloneVoiceCode)

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            pickMediaCode -> {
                val found = mutableListOf<Uri>()
                data.clipData?.let { clip -> for (i in 0 until clip.itemCount) found += clip.getItemAt(i).uri }
                data.data?.let { found += it }
                found.distinct().forEach { persist(it); if (it.toString() !in current.mediaUris) current.mediaUris += it.toString() }
                repo.save(current); updateMediaPreview(); show("${found.size} رسانه اضافه شد")
            }
            pickMusicCode -> data.data?.let { persist(it); current.musicUri = it.toString(); repo.save(current); updateMusicPreview() }
            pickNarrationCode -> data.data?.let { persist(it); current.narrationUri = it.toString(); current.voiceMode = "external_audio"; repo.save(current); updateNarrationPreview(); showPanel(3) }
            pickCloneVoiceCode -> data.data?.let { uri ->
                persist(uri)
                if (!looksLikeWav(uri)) return show("نمونهٔ کلون باید WAV معتبر باشد")
                current.cloneReferenceUri = uri.toString(); current.voiceMode = "pocket_clone"; repo.save(current); showPanel(3)
            }
        }
    }

    private fun looksLikeWav(uri: Uri): Boolean {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val h = ByteArray(12); input.read(h) == 12 && String(h,0,4,Charsets.US_ASCII) == "RIFF" && String(h,8,4,Charsets.US_ASCII) == "WAVE"
            } ?: false
        }.getOrDefault(false)
    }

    private fun persist(uri: Uri) {
        if (uri.scheme != "content") return
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun loadPersianVoices() {
        persianVoices.clear(); persianVoices += tts?.voices.orEmpty().filter { it.locale.language.equals("fa", true) }.sortedBy { it.name }
        refreshVoiceSpinner()
    }

    private fun refreshVoiceSpinner() {
        val s = voiceSpinner ?: return
        val labels = if (persianVoices.isEmpty()) listOf("صدای پیش‌فرض فارسی Android") else persianVoices.map { "${it.name} · ${it.locale.displayName}" }
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
    }

    private fun applyAndroidVoiceSettings() {
        val engine = tts ?: return
        if (persianVoices.isNotEmpty()) persianVoices.getOrNull(voiceSpinner?.selectedItemPosition ?: 0)?.let { engine.voice = it }
        else engine.language = Locale("fa", "IR")
        engine.setSpeechRate(0.65f + ((rateSeek?.progress ?: 32) / 80f) * 0.75f)
        engine.setPitch(0.75f + ((pitchSeek?.progress ?: 40) / 80f) * 0.55f)
    }

    private fun refreshProjectSpinner() {
        val spinner = projectSpinner ?: return
        val items = repo.list()
        suppressProjectSelection = true
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items.map { it.title.ifBlank { "بدون نام" } })
        val pos = items.indexOfFirst { it.id == current.id }.coerceAtLeast(0); if (items.isNotEmpty()) spinner.setSelection(pos, false)
        suppressProjectSelection = false
    }

    private fun syncFieldsToProject() {
        projectTitle?.let { current.title = it.text.toString().trim().ifBlank { "مستند جدید" } }
        script?.let { current.script = it.text.toString() }
    }

    private fun updateMediaPreview() { mediaPreview?.text = if (current.mediaUris.isEmpty()) "هنوز رسانه‌ای انتخاب نشده." else current.mediaUris.mapIndexed { i, u -> "${i+1}. ${displayName(Uri.parse(u))}" }.joinToString("\n") }
    private fun updateMusicPreview() { musicPreview?.text = current.musicUri?.let { "موزیک: ${displayName(Uri.parse(it))}" } ?: "بدون موزیک" }
    private fun updateNarrationPreview() { narrationPreview?.text = current.narrationUri?.let { "نریشن آماده: ${displayName(Uri.parse(it))}" } ?: "نریشن آماده انتخاب نشده" }
    private fun updateClonePreview() { clonePreview?.text = current.cloneReferenceUri?.let { "نمونهٔ کلون: ${displayName(Uri.parse(it))}" } ?: "نمونهٔ کلون انتخاب نشده" }
    private fun updateModelStatus() { modelStatus?.text = if (pocketNarration.modelInstalled()) "مدل فارسی داخلی: آماده ✓" else "مدل فارسی داخل APK است؛ بار اول باید روی حافظهٔ داخلی استخراج شود." }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: uri.toString()
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: uri.toString()
    }

    private fun openLastOutput() {
        val raw = current.lastOutputUri ?: return show("خروجی قبلی موجود نیست")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(raw), "video/mp4"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }
            .onFailure { show("برنامه‌ای برای پخش ویدیو پیدا نشد") }
    }

    private fun voiceModeLabel(mode: String) = when(mode) {
        "pocket_default" -> "راوی فارسی داخلی"; "pocket_clone" -> "کلون فارسی روی گوشی"; "android_tts" -> "TTS Android"; else -> "نریشن آماده"
    }

    private fun scrollPanel(build: LinearLayout.() -> Unit): View {
        val scroll = ScrollView(this); val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(12), dp(4), dp(18)); build()
        }; scroll.addView(box); return scroll
    }

    private fun section(text: String) = label(text, 18, Color.WHITE, true).apply { setPadding(0, dp(12), 0, dp(8)) }
    private fun info(text: String) = label(text, 13, Color.rgb(190,200,210), false).apply { setPadding(dp(10), dp(9), dp(10), dp(9)); setBackgroundColor(Color.rgb(20,27,35)) }
    private fun edit(hint: String, multi: Boolean) = EditText(this).apply {
        this.hint = hint; setHintTextColor(Color.rgb(120,132,145)); setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(20,27,35)); setPadding(dp(12),dp(10),dp(12),dp(10)); textSize = 15f
        if (multi) { gravity = Gravity.TOP or Gravity.RIGHT; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE } else inputType = android.text.InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
    }
    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { this.text=text; textSize=size.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun button(text: String, action: () -> Unit) = Button(this).apply { this.text=text; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(35,72,96)); isAllCaps=false; setOnClickListener { action() }; minHeight=dp(52) }
    private fun tabButton(text: String, action: () -> Unit) = button(text, action).apply { textSize=14f }
    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd=dp(4); marginStart=dp(4) }) }
    }
    private fun show(message: String) { status.text = message }
    private fun safeName(text: String) = text.replace(Regex("[^\\p{L}\\p{N}_ -]"), "").trim().replace(' ','_').take(60).ifBlank { "Mostanad" }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
