package af.muhsiny.docstudio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.media3.common.util.UnstableApi
import java.util.Collections
import java.util.Locale

@UnstableApi
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var repo: ProjectRepository
    private lateinit var current: DocumentaryProject
    private lateinit var exportEngine: ExportEngine
    private lateinit var pocketNarration: PersianPocketNarration
    private var androidNarration: AndroidTtsNarration? = null

    private lateinit var panelHost: FrameLayout
    private lateinit var status: TextView
    private var projectTitle: EditText? = null
    private var script: EditText? = null
    private var projectSpinner: Spinner? = null
    private var exportProgress: ProgressBar? = null
    private var openOutputButton: Button? = null
    private var modelStatus: TextView? = null
    private var voiceSpinner: Spinner? = null

    private data class SceneBinding(
        val sceneId: String,
        val text: EditText,
        val media: Spinner,
        val crop: Spinner,
        val clipStart: EditText,
        val subtitle: CheckBox
    )
    private val sceneBindings = mutableListOf<SceneBinding>()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val persianVoices = mutableListOf<android.speech.tts.Voice>()
    private var player: MediaPlayer? = null
    private var suppressProjectSelection = false
    private var pendingSfxSceneId: String? = null

    private val pickMediaCode = 6101
    private val pickMusicCode = 6102
    private val pickNarrationCode = 6103
    private val pickCloneVoiceCode = 6104
    private val pickLogoCode = 6105
    private val pickScriptCode = 6106
    private val pickSceneSfxCode = 6107

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 10, 14)
        window.navigationBarColor = Color.rgb(7, 10, 14)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        repo = ProjectRepository(this)
        current = repo.currentOrCreate().also { it.ensureScenes() }
        exportEngine = ExportEngine(this)
        pocketNarration = PersianPocketNarration(this)
        setContentView(buildUi())
        tts = TextToSpeech(this, this)
        showPanel(0)
    }

    override fun onPause() {
        syncAll(); repo.save(current); super.onPause()
    }

    override fun onDestroy() {
        androidNarration?.cancel(); pocketNarration.cancel(); exportEngine.cancel()
        player?.release(); player = null
        tts?.stop(); tts?.shutdown(); super.onDestroy()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 10, 14))
            setPadding(dp(10), dp(12), dp(10), dp(8))
        }
        root.addView(label("استدیوی مستند PRO", 25, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        root.addView(label("V6 · تایم‌لاین واقعی · کلون فارسی روی دستگاه · زیرنویس و برند · MP4", 11, Color.rgb(162,174,188), false).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(3), 0, dp(8))
        })
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("پروژه", "تایم‌لاین", "رسانه", "نریشن", "صدا/برند", "خروجی").forEachIndexed { index, title ->
            tabs.addView(tabButton(title) { showPanel(index) }, LinearLayout.LayoutParams(dp(108), dp(48)).apply { marginEnd = dp(5) })
        }
        tabScroll.addView(tabs); root.addView(tabScroll)
        panelHost = FrameLayout(this)
        root.addView(panelHost, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        status = label("آماده", 12, Color.rgb(231,196,113), true).apply { setPadding(dp(5), dp(7), dp(5), dp(3)) }
        root.addView(status)
        return root
    }

    private fun showPanel(index: Int) {
        syncAll(); repo.save(current)
        projectTitle = null; script = null; projectSpinner = null; sceneBindings.clear()
        panelHost.removeAllViews()
        val view = when (index) {
            0 -> projectPanel()
            1 -> timelinePanel()
            2 -> mediaPanel()
            3 -> narrationPanel()
            4 -> audioBrandPanel()
            else -> exportPanel()
        }
        panelHost.addView(view)
    }

    private fun projectPanel(): View = scrollPanel {
        addView(section("مدیریت پروژه"))
        val projects = repo.list()
        projectSpinner = spinner(projects.map { it.title })
        addView(projectSpinner, match(dp(54)))
        suppressProjectSelection = true
        val selected = projects.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        projectSpinner?.setSelection(selected)
        suppressProjectSelection = false
        projectSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressProjectSelection) return
                val p = repo.list().getOrNull(position) ?: return
                if (p.id == current.id) return
                syncAll(); repo.save(current)
                current = p.also { it.ensureScenes() }; repo.setCurrent(current.id)
                showPanel(0); show("پروژه بارگذاری شد")
            }
        }
        addView(row(
            button("پروژه جدید") { syncAll(); repo.save(current); current = repo.create(); showPanel(0) },
            button("کپی پروژه") { syncAll(); repo.save(current); current = repo.duplicate(current); showPanel(0) },
            button("حذف") { repo.delete(current.id); current = repo.currentOrCreate(); showPanel(0) }
        ))

        addView(section("سناریو"))
        projectTitle = edit("نام مستند", false).apply { setText(current.title) }
        script = edit("متن کامل مستند را اینجا بنویس یا فایل TXT وارد کن…", true).apply { setText(current.script); minLines = 12 }
        addView(projectTitle); addView(script)
        addView(row(
            button("وارد کردن TXT") { pickScript() },
            button("ذخیره") { syncAll(); repo.save(current); show("پروژه ذخیره شد ✓") }
        ))
        addView(button("ساخت/بازسازی تایم‌لاین از سناریو") {
            syncAll(); ScenePlanner.rebuildProjectScenes(current); repo.save(current); showPanel(1); show("تایم‌لاین از سناریو بازسازی شد")
        })
        val estimate = ScenePlanner.estimateDurationMs(current.script)
        addView(info("برآورد نریشن: ${formatDuration(estimate)} · صحنه‌های فعلی: ${current.scenes.size}"))
    }

    private fun timelinePanel(): View = scrollPanel {
        addView(section("تایم‌لاین صحنه‌محور"))
        addView(info("هر صحنه متن، رسانه، SFX، Crop/Fit، نقطهٔ شروع کلیپ و زیرنویس مستقل دارد. زمان صحنه هنگام نریشن واقعی محاسبه می‌شود."))
        addView(row(
            button("+ صحنه") {
                syncSceneEditors(); current.scenes += SceneItem(text = "صحنهٔ جدید", mediaUri = current.mediaUris.firstOrNull()); repo.save(current); showPanel(1)
            },
            button("بازسازی از سناریو") { syncAll(); ScenePlanner.rebuildProjectScenes(current); repo.save(current); showPanel(1) },
            button("تخصیص خودکار رسانه") { syncSceneEditors(); ScenePlanner.autoAssignMedia(current); repo.save(current); showPanel(1) }
        ))
        addView(button("سناریو ← متن تایم‌لاین") {
            syncSceneEditors(); current.script = current.scenes.joinToString("\n") { it.text.trim() }; repo.save(current); show("سناریوی اصلی از تایم‌لاین به‌روز شد")
        })

        if (current.scenes.isEmpty()) addView(info("هنوز صحنه‌ای وجود ندارد."))
        current.scenes.forEachIndexed { index, scene -> addView(sceneCard(scene, index)) }
    }

    private fun sceneCard(scene: SceneItem, index: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(17, 22, 29)); cornerRadius = dp(10).toFloat(); setStroke(dp(1), Color.rgb(48, 60, 72))
            }
        }
        box.addView(label("صحنه ${index + 1}", 16, Color.rgb(238,203,117), true))
        val textEdit = edit("متن صحنه", true).apply { setText(scene.text); minLines = 3 }
        box.addView(textEdit)

        val mediaOptions = listOf("— رسانه تعیین نشده —") + current.mediaUris.map { displayName(Uri.parse(it)) }
        box.addView(label("رسانهٔ صحنه", 12, Color.LTGRAY, true))
        val mediaSpinner = spinner(mediaOptions)
        val mediaIndex = current.mediaUris.indexOf(scene.mediaUri).let { if (it >= 0) it + 1 else 0 }
        mediaSpinner.setSelection(mediaIndex)
        box.addView(mediaSpinner, match(dp(50)))

        val cropSpinner = spinner(listOf("پرکردن قاب (Crop)", "نمایش کامل (Fit)"))
        cropSpinner.setSelection(if (scene.cropMode == "fit") 1 else 0)
        box.addView(row(label("چیدمان تصویر", 12, Color.LTGRAY, true), cropSpinner))

        val clipEdit = edit("شروع کلیپ به ثانیه، مثال 3.5", false).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (scene.clipStartMs > 0) "%.1f".format(Locale.US, scene.clipStartMs / 1000.0) else "0")
        }
        box.addView(clipEdit)

        val subtitleCheck = CheckBox(this).apply {
            text = "زیرنویس این صحنه"; setTextColor(Color.WHITE); isChecked = scene.subtitle
        }
        box.addView(subtitleCheck)

        val sfxText = label("SFX: ${scene.sfxUri?.let { displayName(Uri.parse(it)) } ?: "ندارد"}", 12, Color.rgb(184,194,204), false)
        box.addView(sfxText)
        box.addView(row(
            button("انتخاب SFX") { syncSceneEditors(); pendingSfxSceneId = scene.id; pickSceneSfx() },
            button("حذف SFX") { syncSceneEditors(); current.scenes.firstOrNull { it.id == scene.id }?.sfxUri = null; repo.save(current); showPanel(1) },
            button("نمایش رسانه") { scene.mediaUri?.let { openUri(Uri.parse(it)) } ?: show("این صحنه رسانه ندارد") }
        ))
        box.addView(row(
            button("↑") { moveScene(scene.id, -1) },
            button("↓") { moveScene(scene.id, 1) },
            button("کپی") { duplicateScene(scene.id) },
            button("حذف") { deleteScene(scene.id) }
        ))
        sceneBindings += SceneBinding(scene.id, textEdit, mediaSpinner, cropSpinner, clipEdit, subtitleCheck)
        return box.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(9) } }
    }

    private fun mediaPanel(): View = scrollPanel {
        addView(section("کتابخانهٔ رسانه"))
        addView(row(
            button("افزودن عکس/ویدیو") { pickMedia() },
            button("تخصیص خودکار به صحنه‌ها") { ScenePlanner.autoAssignMedia(current); repo.save(current); show("رسانه‌ها به صحنه‌ها تخصیص یافت") },
            button("پاک‌کردن کتابخانه") {
                current.mediaUris.clear(); current.scenes.forEach { it.mediaUri = null }; repo.save(current); showPanel(2)
            }
        ))
        addView(info("${current.mediaUris.size} فایل در کتابخانه · ${current.scenes.count { !it.mediaUri.isNullOrBlank() }} صحنه دارای رسانه"))
        current.mediaUris.toList().forEachIndexed { index, raw ->
            val uri = Uri.parse(raw)
            addView(row(
                label("${index + 1}. ${displayName(uri)}", 12, Color.WHITE, false),
                button("بازکردن") { openUri(uri) },
                button("حذف") {
                    current.mediaUris.remove(raw); current.scenes.filter { it.mediaUri == raw }.forEach { it.mediaUri = null }; repo.save(current); showPanel(2)
                }
            ))
        }
        addView(section("لوگوی رسانه"))
        addView(info(current.logoUri?.let { "لوگو: ${displayName(Uri.parse(it))}" } ?: "لوگو انتخاب نشده"))
        addView(row(
            button("انتخاب PNG/JPG لوگو") { pickLogo() },
            button("حذف لوگو") { current.logoUri = null; repo.save(current); showPanel(2) }
        ))
    }

    private fun narrationPanel(): View = scrollPanel {
        addView(section("موتور نریشن"))
        val modes = listOf(
            "pocket_default" to "راوی فارسی داخلی · آفلاین",
            "pocket_clone" to "کلون فارسی از WAV · روی همین گوشی",
            "android_tts" to "TTS فارسی Android",
            "external_audio" to "نریشن MP3/WAV آماده"
        )
        val group = RadioGroup(this@MainActivity).apply { orientation = RadioGroup.VERTICAL }
        modes.forEachIndexed { i, (key, title) ->
            group.addView(RadioButton(this@MainActivity).apply {
                id = 8000 + i; text = title; setTextColor(Color.WHITE); isChecked = current.voiceMode == key
            })
        }
        group.setOnCheckedChangeListener { _, id ->
            val i = (id - 8000).coerceIn(0, modes.lastIndex); current.voiceMode = modes[i].first; repo.save(current); show("حالت نریشن: ${modes[i].second}")
        }
        addView(group)

        addView(section("PocketTTS فارسی داخل APK"))
        modelStatus = info(""); addView(modelStatus); updateModelStatus()
        addView(row(
            button("آماده‌سازی مدل") { show("در حال آماده‌سازی مدل…"); pocketNarration.prepare({ updateModelStatus(); show("مدل فارسی آماده است ✓") }, { show(it) }) },
            button("تست راوی") { previewPocket(false) }
        ))

        addView(section("کلون فارسی"))
        addView(info("نمونهٔ تمیز WAV، یک گوینده و بدون موزیک انتخاب کن. تولید و کلون روی خود دستگاه انجام می‌شود."))
        addView(info(current.cloneReferenceUri?.let { "نمونه: ${displayName(Uri.parse(it))}" } ?: "نمونهٔ کلون انتخاب نشده"))
        addView(row(
            button("انتخاب WAV") { pickCloneVoice() },
            button("تست کلون") { previewPocket(true) },
            button("حذف") { current.cloneReferenceUri = null; repo.save(current); showPanel(3) }
        ))

        addView(section("Android TTS"))
        voiceSpinner = spinner(if (persianVoices.isEmpty()) listOf("صدای فارسی پیدا نشده") else persianVoices.map { it.name })
        addView(voiceSpinner, match(dp(52)))
        restoreAndroidVoiceSelection()
        voiceSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                persianVoices.getOrNull(position)?.let { getPreferences(MODE_PRIVATE).edit().putString("ttsVoice", it.name).apply() }
            }
        }
        addView(seekSetting("سرعت TTS", 60, 160, getPreferences(MODE_PRIVATE).getInt("ttsRate", 100)) {
            getPreferences(MODE_PRIVATE).edit().putInt("ttsRate", it).apply()
        })
        addView(seekSetting("زیر و بمی TTS", 60, 140, getPreferences(MODE_PRIVATE).getInt("ttsPitch", 100)) {
            getPreferences(MODE_PRIVATE).edit().putInt("ttsPitch", it).apply()
        })
        addView(button("تست Android TTS") { previewAndroidVoice() })

        addView(section("نریشن آماده"))
        addView(info(current.narrationUri?.let { "فایل: ${displayName(Uri.parse(it))}" } ?: "فایل نریشن آماده انتخاب نشده"))
        addView(row(
            button("انتخاب MP3/WAV") { pickNarration() },
            button("حذف") { current.narrationUri = null; repo.save(current); showPanel(3) }
        ))
    }

    private fun audioBrandPanel(): View = scrollPanel {
        addView(section("میکس صدا"))
        addView(info(current.musicUri?.let { "موسیقی: ${displayName(Uri.parse(it))}" } ?: "موسیقی پس‌زمینه ندارد"))
        addView(row(button("انتخاب موسیقی") { pickMusic() }, button("حذف موسیقی") { current.musicUri = null; repo.save(current); showPanel(4) }))
        addView(seekSetting("ولوم نریشن", 0, 150, current.narrationVolume) { current.narrationVolume = it; repo.save(current) })
        addView(seekSetting("ولوم موسیقی", 0, 100, current.musicVolume) { current.musicVolume = it; repo.save(current) })
        addView(seekSetting("ولوم SFX", 0, 100, current.sfxVolume) { current.sfxVolume = it; repo.save(current) })

        addView(section("زیرنویس"))
        val sub = CheckBox(this@MainActivity).apply {
            text = "زیرنویس فارسی روی خود ویدیو بسوزد"; setTextColor(Color.WHITE); isChecked = current.subtitlesEnabled
            setOnCheckedChangeListener { _, checked -> current.subtitlesEnabled = checked; repo.save(current) }
        }
        addView(sub)
        addView(seekSetting("اندازهٔ زیرنویس", 24, 72, current.subtitleSize) { current.subtitleSize = it; repo.save(current) })
        addView(info("برای هر صحنه نیز می‌توان زیرنویس را جداگانه خاموش کرد. فایل SRT هم کنار خروجی ذخیره می‌شود."))

        addView(section("برند"))
        addView(info(current.logoUri?.let { "لوگوی فعال: ${displayName(Uri.parse(it))}" } ?: "بدون لوگو"))
        addView(row(button("انتخاب لوگو") { pickLogo() }, button("حذف لوگو") { current.logoUri = null; repo.save(current); showPanel(4) }))
    }

    private fun exportPanel(): View = scrollPanel {
        addView(section("قاب و کیفیت"))
        val ratioValues = listOf("16:9", "9:16", "1:1")
        val ratio = spinner(ratioValues).apply { setSelection(ratioValues.indexOf(current.aspectRatio).coerceAtLeast(0)) }
        ratio.onItemSelectedListener = simpleSelection { current.aspectRatio = ratioValues[it]; repo.save(current) }
        addView(row(label("نسبت تصویر", 13, Color.WHITE, true), ratio))
        val resValues = listOf("720p", "1080p")
        val res = spinner(resValues).apply { setSelection(resValues.indexOf(current.resolution).coerceAtLeast(0)) }
        res.onItemSelectedListener = simpleSelection { current.resolution = resValues[it]; repo.save(current) }
        addView(row(label("کیفیت خروجی", 13, Color.WHITE, true), res))

        addView(section("بازبینی قبل از رندر"))
        val checks = ScenePlanner.validate(current)
        checks.forEach { addView(info((if (it.ok) "✓ " else "⚠ ") + it.message)) }
        val estimate = ScenePlanner.estimateDurationMs(current.scenes.joinToString(" ") { it.text })
        addView(info("برآورد مدت: ${formatDuration(estimate)} · خروجی: ${current.aspectRatio} / ${current.resolution}"))

        exportProgress = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        addView(exportProgress)
        addView(button("ساخت مستند نهایی") { startExport() }.apply { minHeight = dp(66); textSize = 17f })
        addView(button("لغو رندر") { pocketNarration.cancel(); androidNarration?.cancel(); exportEngine.cancel(); show("رندر لغو شد") })
        openOutputButton = button("بازکردن آخرین MP4") { openLastOutput() }.apply {
            isEnabled = !current.lastOutputUri.isNullOrBlank(); alpha = if (isEnabled) 1f else .45f
        }
        addView(openOutputButton)
    }

    private fun startExport() {
        syncAll(); current.ensureScenes(); repo.save(current)
        val hardErrors = ScenePlanner.validate(current).filter { !it.ok && !it.message.contains("موسیقی") }
        if (hardErrors.isNotEmpty()) return show(hardErrors.first().message)
        val scenes = current.scenes.filter { it.text.isNotBlank() }
        val texts = scenes.map { it.text }
        exportProgress?.progress = 0
        when (current.voiceMode) {
            "pocket_default", "pocket_clone" -> {
                val reference = if (current.voiceMode == "pocket_clone") current.cloneReferenceUri?.let(Uri::parse) else null
                if (current.voiceMode == "pocket_clone" && reference == null) return show("نمونهٔ WAV کلون انتخاب نشده")
                show("در حال تولید نریشن فارسی صحنه‌به‌صحنه…")
                pocketNarration.synthesizeScenes(texts, reference,
                    { done, total -> show("نریشن: $done از $total") },
                    { result -> renderWithNarration(result.uris, result.durationsMs) },
                    { show(it) })
            }
            "android_tts" -> {
                if (!ttsReady || tts == null) return show("Android TTS فارسی آماده نیست")
                applyAndroidVoiceSettings()
                show("در حال ساخت TTS صحنه‌به‌صحنه…")
                androidNarration = AndroidTtsNarration(this, tts!!)
                androidNarration?.synthesizeScenes(texts,
                    { done, total -> show("TTS: $done از $total") },
                    { result -> renderWithNarration(result.uris, result.durationsMs) },
                    { show(it) })
            }
            "external_audio" -> {
                val raw = current.narrationUri ?: return show("فایل نریشن آماده انتخاب نشده")
                val uri = Uri.parse(raw)
                val total = mediaDurationMs(uri)
                if (total <= 500L) return show("مدت نریشن آماده معتبر نیست")
                val durations = ScenePlanner.allocateSceneDurations(texts, total)
                renderWithNarration(listOf(uri), durations)
            }
            else -> show("حالت نریشن معتبر نیست")
        }
    }

    private fun renderWithNarration(uris: List<Uri>, durations: List<Long>) {
        show("نریشن آماده شد؛ رندر MP4 شروع شد…")
        exportEngine.export(current, uris, durations,
            { p -> exportProgress?.progress = p; show("رندر: $p٪") },
            { done ->
                exportProgress?.progress = 100
                current.lastOutputUri = done.videoUri.toString(); repo.save(current)
                openOutputButton?.isEnabled = true; openOutputButton?.alpha = 1f
                show(if (done.srtUri != null) "MP4 و SRT ساخته و ذخیره شد ✓" else "MP4 ساخته و ذخیره شد ✓")
            },
            { show("خطای رندر: $it") })
    }

    override fun onInit(initStatus: Int) {
        if (initStatus == TextToSpeech.SUCCESS) {
            ttsReady = true; tts?.setLanguage(Locale.forLanguageTag("fa-IR")); loadPersianVoices()
        } else ttsReady = false
    }

    private fun loadPersianVoices() {
        persianVoices.clear()
        val voices = runCatching { tts?.voices.orEmpty() }.getOrDefault(emptySet())
        persianVoices += voices.filter { v -> v.locale.language == "fa" || v.locale.language == "fas" }.sortedBy { it.name }
    }

    private fun restoreAndroidVoiceSelection() {
        val saved = getPreferences(MODE_PRIVATE).getString("ttsVoice", null)
        val index = persianVoices.indexOfFirst { it.name == saved }
        if (index >= 0) voiceSpinner?.setSelection(index)
    }

    private fun applyAndroidVoiceSettings() {
        val saved = getPreferences(MODE_PRIVATE).getString("ttsVoice", null)
        persianVoices.firstOrNull { it.name == saved }?.let { tts?.voice = it }
        val rate = getPreferences(MODE_PRIVATE).getInt("ttsRate", 100) / 100f
        val pitch = getPreferences(MODE_PRIVATE).getInt("ttsPitch", 100) / 100f
        tts?.setSpeechRate(rate.coerceIn(.6f, 1.6f)); tts?.setPitch(pitch.coerceIn(.6f, 1.4f))
    }

    private fun previewPocket(clone: Boolean) {
        if (clone && current.cloneReferenceUri.isNullOrBlank()) return show("اول نمونهٔ WAV را انتخاب کن")
        val text = current.scenes.firstOrNull()?.text?.ifBlank { null } ?: current.script.ifBlank { "این یک نمونهٔ نریشن فارسی مستند است." }
        show("در حال تولید نمونهٔ فارسی…")
        pocketNarration.synthesize(text.take(450), if (clone) Uri.parse(current.cloneReferenceUri) else null,
            { done, total -> show("صدا: $done از $total") },
            { result ->
                val uri = result.uris.firstOrNull() ?: return@synthesize show("نمونه ساخته نشد")
                player?.release(); player = MediaPlayer.create(this, uri); player?.start(); show("نمونهٔ فارسی تولید شد ✓")
            }, { show(it) })
    }

    private fun previewAndroidVoice() {
        if (!ttsReady) return show("Android TTS آماده نیست")
        applyAndroidVoiceSettings()
        val sample = current.scenes.firstOrNull()?.text?.take(420) ?: "این یک نمونهٔ نریشن فارسی برای مستند است."
        tts?.speak(PersianTextNormalizer.normalize(sample), TextToSpeech.QUEUE_FLUSH, Bundle(), "preview")
    }

    private fun syncAll() {
        projectTitle?.let { current.title = it.text.toString().trim().ifBlank { "مستند جدید" } }
        script?.let { current.script = it.text.toString() }
        syncSceneEditors()
    }

    private fun syncSceneEditors() {
        sceneBindings.forEach { b ->
            val scene = current.scenes.firstOrNull { it.id == b.sceneId } ?: return@forEach
            scene.text = b.text.text.toString().trim()
            val pos = b.media.selectedItemPosition
            scene.mediaUri = if (pos <= 0) null else current.mediaUris.getOrNull(pos - 1)
            scene.cropMode = if (b.crop.selectedItemPosition == 1) "fit" else "crop"
            scene.clipStartMs = ((b.clipStart.text.toString().toDoubleOrNull() ?: 0.0) * 1000.0).toLong().coerceAtLeast(0L)
            scene.subtitle = b.subtitle.isChecked
        }
    }

    private fun moveScene(id: String, delta: Int) {
        syncSceneEditors(); val i = current.scenes.indexOfFirst { it.id == id }; val j = i + delta
        if (i < 0 || j !in current.scenes.indices) return
        Collections.swap(current.scenes, i, j); repo.save(current); showPanel(1)
    }

    private fun duplicateScene(id: String) {
        syncSceneEditors(); val i = current.scenes.indexOfFirst { it.id == id }; if (i < 0) return
        val copy = current.scenes[i].copy(id = java.util.UUID.randomUUID().toString())
        current.scenes.add(i + 1, copy); repo.save(current); showPanel(1)
    }

    private fun deleteScene(id: String) {
        syncSceneEditors(); current.scenes.removeAll { it.id == id }; repo.save(current); showPanel(1)
    }

    private fun pickMedia() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*")); addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, pickMediaCode)
    }

    private fun pickMusic() = pickOne("audio/*", pickMusicCode)
    private fun pickNarration() = pickOne("audio/*", pickNarrationCode)
    private fun pickCloneVoice() = pickOne("audio/*", pickCloneVoiceCode)
    private fun pickLogo() = pickOne("image/*", pickLogoCode)
    private fun pickScript() = pickOne("text/plain", pickScriptCode)
    private fun pickSceneSfx() = pickOne("audio/*", pickSceneSfxCode)

    private fun pickOne(type: String, code: Int) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            this.type = type; addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, code)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == pickMediaCode) {
            val uris = mutableListOf<Uri>()
            data.clipData?.let { c -> for (i in 0 until c.itemCount) uris += c.getItemAt(i).uri }
            data.data?.let { uris += it }
            uris.distinct().forEach { uri ->
                takePermission(uri, data.flags)
                val mime = contentResolver.getType(uri).orEmpty()
                if ((mime.startsWith("image/") || mime.startsWith("video/")) && uri.toString() !in current.mediaUris) current.mediaUris += uri.toString()
            }
            repo.save(current); showPanel(2); show("${uris.size} فایل بررسی/افزوده شد")
            return
        }
        val uri = data.data ?: return
        takePermission(uri, data.flags)
        when (requestCode) {
            pickMusicCode -> current.musicUri = uri.toString()
            pickNarrationCode -> current.narrationUri = uri.toString()
            pickCloneVoiceCode -> current.cloneReferenceUri = uri.toString()
            pickLogoCode -> current.logoUri = uri.toString()
            pickScriptCode -> {
                val text = runCatching { contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }.getOrNull()
                if (text.isNullOrBlank()) return show("فایل متن خوانده نشد")
                current.script = text; script?.setText(text); ScenePlanner.rebuildProjectScenes(current); repo.save(current); showPanel(0); show("سناریو وارد و تایم‌لاین ساخته شد")
                return
            }
            pickSceneSfxCode -> {
                val id = pendingSfxSceneId; pendingSfxSceneId = null
                current.scenes.firstOrNull { it.id == id }?.sfxUri = uri.toString(); repo.save(current); showPanel(1); show("SFX صحنه ثبت شد")
                return
            }
        }
        repo.save(current); show("فایل انتخاب شد ✓")
    }

    private fun takePermission(uri: Uri, flags: Int) {
        val take = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(uri, take) }
    }

    private fun updateModelStatus() {
        modelStatus?.text = if (pocketNarration.modelInstalled() && pocketNarration.defaultVoiceReady())
            "✓ مدل فارسی و صدای پایه روی دستگاه آماده است"
        else "مدل فارسی داخل APK است و با «آماده‌سازی مدل» در حافظهٔ برنامه استخراج می‌شود"
    }

    private fun openLastOutput() {
        val raw = current.lastOutputUri ?: return show("هنوز خروجی ساخته نشده")
        openUri(Uri.parse(raw), "video/mp4")
    }

    private fun openUri(uri: Uri, forcedMime: String? = null) {
        val mime = forcedMime ?: contentResolver.getType(uri) ?: "*/*"
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
        }.onFailure { show("برنامه‌ای برای بازکردن این فایل پیدا نشد") }
    }

    private fun mediaDurationMs(uri: Uri): Long = runCatching {
        val r = MediaMetadataRetriever(); r.setDataSource(this, uri)
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release(); ms
    }.getOrDefault(0L)

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "file"
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "فایل"
    }

    private fun formatDuration(ms: Long): String {
        val total = ms / 1000; val m = total / 60; val s = total % 60
        return "%d:%02d".format(m, s)
    }

    private fun simpleSelection(onSelected: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onSelected(position)
    }

    private fun seekSetting(title: String, minValue: Int, maxValue: Int, value: Int, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val valueText = label("$title: $value", 12, Color.WHITE, true)
        val seek = SeekBar(this).apply { min = minValue; max = maxValue; progress = value.coerceIn(minValue, maxValue) }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { valueText.text = "$title: $progress" }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { seekBar?.progress?.let(onChange) }
        })
        box.addView(valueText); box.addView(seek); return box
    }

    private fun scrollPanel(block: LinearLayout.() -> Unit): View {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(8), dp(4), dp(20)); block() }
        scroll.addView(box); return scroll
    }

    private fun section(text: String) = label(text, 17, Color.rgb(235,199,111), true).apply { setPadding(0, dp(12), 0, dp(6)) }
    private fun info(text: String) = label(text, 12, Color.rgb(184,195,207), false).apply { setPadding(dp(7), dp(7), dp(7), dp(7)); setBackgroundColor(Color.rgb(15, 20, 27)) }
    private fun label(text: String, sp: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = sp.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD); setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    private fun edit(hint: String, multiline: Boolean) = EditText(this).apply {
        this.hint = hint; setHintTextColor(Color.rgb(112,124,137)); setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(18,24,31)); setPadding(dp(10), dp(10), dp(10), dp(10)); textSize = 14f
        if (multiline) { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE; gravity = Gravity.TOP or Gravity.START }
    }
    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; textSize = 12f; setOnClickListener { onClick() }
    }
    private fun tabButton(text: String, onClick: () -> Unit) = button(text, onClick)
    private fun spinner(items: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items.ifEmpty { listOf("—") })
    }
    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { v -> addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }) }
    }
    private fun match(height: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply { bottomMargin = dp(6) }
    private fun show(message: String) { if (::status.isInitialized) status.text = message }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
