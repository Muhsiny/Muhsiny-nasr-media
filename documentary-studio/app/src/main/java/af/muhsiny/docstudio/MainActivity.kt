package af.muhsiny.docstudio

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import org.json.JSONArray
import java.io.File
import java.util.Locale

@UnstableApi
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var projectTitle: EditText
    private lateinit var script: EditText
    private lateinit var status: TextView
    private lateinit var scenePreview: TextView
    private lateinit var mediaPreview: TextView
    private lateinit var musicPreview: TextView
    private lateinit var voiceSpinner: Spinner
    private lateinit var rateSeek: SeekBar
    private lateinit var pitchSeek: SeekBar
    private lateinit var exportProgress: ProgressBar
    private lateinit var openOutputButton: Button
    private lateinit var panelHost: FrameLayout

    private val mediaUris = mutableListOf<Uri>()
    private var musicUri: Uri? = null
    private var lastOutputUri: Uri? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val persianVoices = mutableListOf<android.speech.tts.Voice>()
    private var transformer: Transformer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val pickMediaCode = 4101
    private val pickMusicCode = 4102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 14, 19)
        window.navigationBarColor = Color.rgb(9, 14, 19)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(buildUi())
        restoreProject()
        tts = TextToSpeech(this, this)
        showPanel(0)
    }

    override fun onDestroy() {
        transformer?.cancel()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 14, 19))
            setPadding(dp(12), dp(14), dp(12), dp(12))
        }

        root.addView(label("استدیوی مستند ۲", 26, Color.WHITE, true).apply { gravity = Gravity.CENTER })
        root.addView(label("نسخهٔ مستقل: متن، رسانه، نریشن و خروجی MP4 روی خود گوشی", 12, Color.rgb(165, 176, 187), false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        })

        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("پروژه", "صحنه‌ها", "رسانه", "صدا", "خروجی").forEachIndexed { index, title ->
            tabs.addView(tabButton(title) { showPanel(index) }, LinearLayout.LayoutParams(dp(104), dp(48)).apply { marginEnd = dp(6) })
        }
        tabScroll.addView(tabs)
        root.addView(tabScroll)

        panelHost = FrameLayout(this)
        root.addView(panelHost, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        status = label("آماده", 12, Color.rgb(224, 190, 112), true).apply {
            gravity = Gravity.START
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        root.addView(status)
        return root
    }

    private fun showPanel(index: Int) {
        panelHost.removeAllViews()
        val view = when (index) {
            0 -> projectPanel()
            1 -> scenesPanel()
            2 -> mediaPanel()
            3 -> voicePanel()
            else -> exportPanel()
        }
        panelHost.addView(view)
    }

    private fun scrollPanel(builder: LinearLayout.() -> Unit): View {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(12), dp(4), dp(24))
            builder()
        }
        scroll.addView(box)
        return scroll
    }

    private fun projectPanel(): View = scrollPanel {
        addView(section("پروژه و سناریو"))
        projectTitle = edit("نام مستند", false)
        script = edit("متن کامل مستند را اینجا بنویس…", true).apply { minLines = 11 }
        addView(projectTitle)
        addView(script)
        addView(row(button("ذخیرهٔ واقعی پروژه") { saveProject() }, button("پاک‌کردن پروژه") { clearProject() }))
        addView(info("این بخش مستقل است؛ عنوان، متن و فهرست رسانه‌ها روی خود گوشی ذخیره می‌شود."))
    }

    private fun scenesPanel(): View = scrollPanel {
        addView(section("صحنه‌بندی متن"))
        addView(button("ساخت صحنه‌ها از متن") { buildScenes() })
        scenePreview = info("هنوز صحنه‌ای ساخته نشده است.").apply { minHeight = dp(260) }
        addView(scenePreview)
        updateScenePreview()
    }

    private fun mediaPanel(): View = scrollPanel {
        addView(section("رسانه‌های واقعی از گوشی"))
        addView(button("افزودن چند عکس یا ویدیو") { pickMedia() })
        addView(button("حذف همهٔ رسانه‌ها") { mediaUris.clear(); persistMedia(); updateMediaPreview(); show("رسانه‌ها پاک شدند") })
        mediaPreview = info("هیچ رسانه‌ای انتخاب نشده است.").apply { minHeight = dp(180) }
        addView(mediaPreview)
        addView(section("موسیقی پس‌زمینه"))
        addView(row(button("انتخاب موزیک") { pickMusic() }, button("حذف موزیک") { musicUri = null; persistMedia(); updateMusicPreview() }))
        musicPreview = info("موزیک انتخاب نشده است.")
        addView(musicPreview)
        updateMediaPreview()
        updateMusicPreview()
    }

    private fun voicePanel(): View = scrollPanel {
        addView(section("نریشن فارسی روی خود گوشی"))
        addView(info("این بخش از موتور Text‑to‑Speech نصب‌شدهٔ Android استفاده می‌کند. اگر موتور گوشی صدای فارسی داشته باشد، نریشن بدون کمپیوتر ساخته می‌شود."))
        voiceSpinner = Spinner(this@MainActivity)
        addView(voiceSpinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(8) })
        addView(label("سرعت", 13, Color.WHITE, true))
        rateSeek = SeekBar(this@MainActivity).apply { max = 80; progress = 35 }
        addView(rateSeek)
        addView(label("زیر و بمی", 13, Color.WHITE, true))
        pitchSeek = SeekBar(this@MainActivity).apply { max = 80; progress = 40 }
        addView(pitchSeek)
        addView(row(button("تست صدا") { previewVoice() }, button("تازه‌سازی صداها") { loadPersianVoices() }))
        refreshVoiceSpinner()
    }

    private fun exportPanel(): View = scrollPanel {
        addView(section("خروجی واقعی MP4"))
        addView(info("روند واقعی: متن → نریشن فارسی → ترکیب عکس/ویدیو → میکس اختیاری موزیک → MP4. رسانه‌های تصویری در طول نریشن به‌صورت حلقه‌ای ادامه پیدا می‌کنند."))
        exportProgress = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        addView(exportProgress)
        addView(button("ساخت مستند روی همین گوشی") { startStandaloneExport() }.apply { textSize = 17f; minHeight = dp(64) })
        openOutputButton = button("باز کردن آخرین MP4") { openLastOutput() }.apply {
            isEnabled = lastOutputUri != null
            alpha = if (isEnabled) 1f else .45f
        }
        addView(openOutputButton)
        addView(button("ساخت فایل زیرنویس SRT") { exportSrt() })
    }

    override fun onInit(initStatus: Int) {
        if (initStatus == TextToSpeech.SUCCESS) {
            ttsReady = true
            val result = tts?.setLanguage(Locale("fa", "IR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
            loadPersianVoices()
            show(if (result >= 0) "موتور گفتار آماده است ✓" else "موتور گفتار نصب است اما فارسی را گزارش نکرد")
        } else {
            ttsReady = false
            show("موتور گفتار Android راه‌اندازی نشد")
        }
    }

    private fun loadPersianVoices() {
        persianVoices.clear()
        val voices = tts?.voices.orEmpty().filter { it.locale.language.equals("fa", true) }.sortedBy { it.name }
        persianVoices.addAll(voices)
        refreshVoiceSpinner()
        if (voices.isNotEmpty()) show("${voices.size} صدای فارسی روی گوشی پیدا شد")
    }

    private fun refreshVoiceSpinner() {
        if (!::voiceSpinner.isInitialized) return
        val labels = if (persianVoices.isEmpty()) listOf("صدای پیش‌فرض فارسی Android") else persianVoices.map { "${it.name} · ${it.locale.displayName}" }
        voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
    }

    private fun applyVoiceSettings() {
        if (persianVoices.isNotEmpty() && ::voiceSpinner.isInitialized) {
            persianVoices.getOrNull(voiceSpinner.selectedItemPosition)?.let { tts?.voice = it }
        } else {
            tts?.language = Locale("fa", "IR")
        }
        val rate = if (::rateSeek.isInitialized) 0.65f + (rateSeek.progress / 80f) * 0.75f else 0.95f
        val pitch = if (::pitchSeek.isInitialized) 0.75f + (pitchSeek.progress / 80f) * 0.55f else 1.0f
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
    }

    private fun previewVoice() {
        if (!ttsReady) return show("موتور گفتار هنوز آماده نیست")
        val text = currentScript().ifBlank { "این یک نمونهٔ نریشن فارسی برای مستند است. روایت باید آرام، روشن و قابل فهم باشد." }.take(420)
        applyVoiceSettings()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "preview")
        show("پخش آزمایشی صدا…")
    }

    private fun pickMedia() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, pickMediaCode)
    }

    private fun pickMusic() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, pickMusicCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        if (requestCode == pickMediaCode) {
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) addPersistedUri(clip.getItemAt(i).uri, mediaUris)
            } ?: data.data?.let { addPersistedUri(it, mediaUris) }
            persistMedia()
            updateMediaPreview()
            show("${mediaUris.size} رسانه در پروژه است")
        } else if (requestCode == pickMusicCode) {
            data.data?.let {
                persistUriPermission(it)
                musicUri = it
                persistMedia()
                updateMusicPreview()
                show("موزیک انتخاب شد")
            }
        }
    }

    private fun addPersistedUri(uri: Uri, list: MutableList<Uri>) {
        persistUriPermission(uri)
        if (list.none { it == uri }) list.add(uri)
    }

    private fun persistUriPermission(uri: Uri) {
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
    }

    private fun buildScenes() {
        saveCurrentTextFields()
        val scenes = sceneTexts()
        updateScenePreview()
        show(if (scenes.isEmpty()) "متن برای صحنه‌بندی کافی نیست" else "${scenes.size} صحنه ساخته شد")
    }

    private fun sceneTexts(): List<String> = currentScript()
        .split(Regex("(?<=[.!؟؛])\\s+|\\n+"))
        .map { it.trim() }
        .filter { it.length >= 8 }
        .take(60)

    private fun updateScenePreview() {
        if (!::scenePreview.isInitialized) return
        val scenes = sceneTexts()
        scenePreview.text = if (scenes.isEmpty()) "هنوز صحنه‌ای ساخته نشده است." else scenes.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n\n")
    }

    private fun updateMediaPreview() {
        if (!::mediaPreview.isInitialized) return
        mediaPreview.text = if (mediaUris.isEmpty()) "هیچ رسانه‌ای انتخاب نشده است." else mediaUris.mapIndexed { i, uri -> "${i + 1}. ${displayName(uri)}" }.joinToString("\n")
    }

    private fun updateMusicPreview() {
        if (!::musicPreview.isInitialized) return
        musicPreview.text = musicUri?.let { "موزیک: ${displayName(it)}" } ?: "موزیک انتخاب نشده است."
    }

    private fun startStandaloneExport() {
        saveCurrentTextFields()
        val text = currentScript().trim()
        if (text.isBlank()) return show("متن مستند خالی است")
        if (mediaUris.isEmpty()) return show("حداقل یک عکس یا ویدیو انتخاب کن")
        if (!ttsReady) return show("موتور گفتار Android آماده نیست")
        if (transformer != null) return show("یک خروجی در حال ساخت است")

        exportProgress.progress = 2
        show("در حال ساخت نریشن روی گوشی…")
        applyVoiceSettings()

        val narrationDir = File(filesDir, "narration").apply { mkdirs() }
        val narrationFile = File(narrationDir, "narration_${System.currentTimeMillis()}.wav")
        val utteranceId = "export_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { }
            override fun onDone(id: String?) {
                if (id != utteranceId) return
                runOnUiThread {
                    exportProgress.progress = 12
                    show("نریشن ساخته شد ✓؛ در حال رندر MP4…")
                    beginMedia3Export(narrationFile)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                runOnUiThread { show("ساخت نریشن ناموفق شد"); exportProgress.progress = 0 }
            }
            override fun onError(id: String?, errorCode: Int) {
                runOnUiThread { show("ساخت نریشن ناموفق شد: $errorCode"); exportProgress.progress = 0 }
            }
        })

        val result = tts?.synthesizeToFile(text, Bundle(), narrationFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            show("موتور گفتار درخواست ساخت فایل را نپذیرفت")
            exportProgress.progress = 0
        }
    }

    private fun beginMedia3Export(narrationFile: File) {
        try {
            val visualItems = buildVisualItems()
            val visualSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
                .addItems(visualItems)
                .setIsLooping(true)
                .build()

            val narrationEffects = stereoEffects(1.0f)
            val narrationItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(narrationFile)))
                .setEffects(narrationEffects)
                .build()
            val narrationSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                .addItem(narrationItem)
                .build()

            val sequences = mutableListOf(visualSequence, narrationSequence)
            musicUri?.let { mUri ->
                val musicItem = EditedMediaItem.Builder(MediaItem.fromUri(mUri))
                    .setEffects(stereoEffects(0.16f))
                    .build()
                val musicSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                    .addItem(musicItem)
                    .setIsLooping(true)
                    .build()
                sequences.add(musicSequence)
            }

            val composition = Composition.Builder(sequences).build()
            val tempDir = File(cacheDir, "exports").apply { mkdirs() }
            val temp = File(tempDir, "docstudio_${System.currentTimeMillis()}.mp4")

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    exportProgress.progress = 100
                    val stored = saveVideoToGallery(temp)
                    temp.delete()
                    if (stored != null) {
                        lastOutputUri = stored
                        openOutputButton.isEnabled = true
                        openOutputButton.alpha = 1f
                        show("MP4 واقعاً ساخته و در Movies/DocStudio ذخیره شد ✓")
                    } else {
                        show("رندر تمام شد اما ذخیره در گالری ناموفق بود")
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    transformer = null
                    exportProgress.progress = 0
                    show("رندر ناموفق: ${exportException.message ?: exportException.errorCodeName}")
                }
            }

            transformer = Transformer.Builder(this)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(listener)
                .build()
            transformer?.start(composition, temp.absolutePath)
            pollProgress()
        } catch (e: Exception) {
            transformer = null
            exportProgress.progress = 0
            show("شروع رندر ناموفق: ${e.message}")
        }
    }

    private fun buildVisualItems(): List<EditedMediaItem> {
        val words = currentScript().split(Regex("\\s+")).count { it.isNotBlank() }
        val estimatedTotalMs = (words * 430L).coerceAtLeast(8000L)
        val perImageMs = (estimatedTotalMs / mediaUris.size.coerceAtLeast(1)).coerceIn(3500L, 14000L)
        return mediaUris.map { uri ->
            val mime = contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("image/")) {
                val item = MediaItem.Builder().setUri(uri).setImageDurationMs(perImageMs).build()
                EditedMediaItem.Builder(item).setFrameRate(30).build()
            } else {
                EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveAudio(true).build()
            }
        }
    }

    private fun stereoEffects(gain: Float): Effects {
        val mix = ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(1, 2).scaleBy(gain))
            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(2, 2).scaleBy(gain))
        }
        return Effects(listOf(ToInt16PcmAudioProcessor(), mix), emptyList())
    }

    private fun pollProgress() {
        val t = transformer ?: return
        val holder = ProgressHolder()
        val state = try { t.getProgress(holder) } catch (_: Exception) { return }
        if (state == Transformer.PROGRESS_STATE_AVAILABLE) exportProgress.progress = holder.progress.coerceIn(12, 99)
        if (transformer != null) handler.postDelayed({ pollProgress() }, 500)
    }

    private fun saveVideoToGallery(temp: File): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, safeName(projectTitleText()) + "_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/DocStudio")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                contentResolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } } ?: return null
                values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                uri
            } else {
                val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return null
                val file = File(dir, safeName(projectTitleText()) + "_${System.currentTimeMillis()}.mp4")
                temp.copyTo(file, overwrite = true)
                Uri.fromFile(file)
            }
        } catch (_: Exception) { null }
    }

    private fun openLastOutput() {
        val uri = lastOutputUri ?: return show("هنوز خروجی وجود ندارد")
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (e: Exception) { show("باز کردن ویدیو ممکن نشد: ${e.message}") }
    }

    private fun exportSrt() {
        val scenes = sceneTexts()
        if (scenes.isEmpty()) return show("ابتدا متن مستند را وارد کن")
        val srt = buildString {
            var cursor = 0L
            scenes.forEachIndexed { index, text ->
                val duration = (text.split(Regex("\\s+")).size * 430L).coerceIn(2500L, 12000L)
                append(index + 1).append('\n')
                append(formatSrt(cursor)).append(" --> ").append(formatSrt(cursor + duration)).append('\n')
                append(text).append("\n\n")
                cursor += duration
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName(projectTitleText()) + ".srt")
                    put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocStudio")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("insert failed")
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(srt) }
                show("SRT در Downloads/DocStudio ذخیره شد ✓")
            } else {
                val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), safeName(projectTitleText()) + ".srt")
                file.writeText(srt, Charsets.UTF_8)
                show("SRT ساخته شد ✓")
            }
        } catch (e: Exception) { show("ساخت SRT ناموفق: ${e.message}") }
    }

    private fun formatSrt(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        val x = ms % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, x)
    }

    private fun saveProject() {
        saveCurrentTextFields()
        persistMedia()
        show("پروژه روی خود گوشی ذخیره شد ✓")
    }

    private fun saveCurrentTextFields() {
        val prefs = getSharedPreferences("docstudio2", MODE_PRIVATE)
        if (::projectTitle.isInitialized) prefs.edit().putString("title", projectTitle.text.toString()).apply()
        if (::script.isInitialized) prefs.edit().putString("script", script.text.toString()).apply()
    }

    private fun clearProject() {
        getSharedPreferences("docstudio2", MODE_PRIVATE).edit().clear().apply()
        mediaUris.clear(); musicUri = null; lastOutputUri = null
        if (::projectTitle.isInitialized) projectTitle.setText("")
        if (::script.isInitialized) script.setText("")
        updateScenePreview(); updateMediaPreview(); updateMusicPreview()
        show("پروژه پاک شد")
    }

    private fun restoreProject() {
        val p = getSharedPreferences("docstudio2", MODE_PRIVATE)
        val title = p.getString("title", "") ?: ""
        val text = p.getString("script", "") ?: ""
        if (::projectTitle.isInitialized) projectTitle.setText(title)
        if (::script.isInitialized) script.setText(text)
        mediaUris.clear()
        try {
            val a = JSONArray(p.getString("media", "[]"))
            for (i in 0 until a.length()) mediaUris.add(Uri.parse(a.getString(i)))
        } catch (_: Exception) { }
        musicUri = p.getString("music", null)?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }

    private fun persistMedia() {
        val a = JSONArray(); mediaUris.forEach { a.put(it.toString()) }
        getSharedPreferences("docstudio2", MODE_PRIVATE).edit()
            .putString("media", a.toString())
            .putString("music", musicUri?.toString())
            .apply()
    }

    private fun currentScript(): String {
        return if (::script.isInitialized) script.text.toString() else getSharedPreferences("docstudio2", MODE_PRIVATE).getString("script", "").orEmpty()
    }

    private fun projectTitleText(): String {
        return if (::projectTitle.isInitialized) projectTitle.text.toString().ifBlank { "Mostanad" } else getSharedPreferences("docstudio2", MODE_PRIVATE).getString("title", "Mostanad").orEmpty().ifBlank { "Mostanad" }
    }

    private fun displayName(uri: Uri): String {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) return c.getString(i)
            }
        } catch (_: Exception) { }
        return uri.lastPathSegment ?: "media"
    }

    private fun safeName(text: String): String = text.replace(Regex("[^\\p{L}\\p{N}_ -]"), "").trim().replace(' ', '_').take(60).ifBlank { "Mostanad" }

    private fun show(text: String) { status.text = text }

    private fun tabButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 13f
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(25, 35, 44))
        setOnClickListener { action() }
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.rgb(8, 13, 18))
        setBackgroundColor(Color.rgb(221, 186, 108))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply { bottomMargin = dp(8) }
    }

    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { index, view ->
            addView(view, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                if (index == 0) marginEnd = dp(4) else marginStart = dp(4)
                bottomMargin = dp(8)
            })
        }
    }

    private fun edit(hint: String, multi: Boolean) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(Color.rgb(112, 124, 136))
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(20, 28, 36))
        setPadding(dp(13), dp(12), dp(13), dp(12))
        gravity = Gravity.TOP or Gravity.START
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, if (multi) dp(250) else dp(56)).apply { bottomMargin = dp(9) }
    }

    private fun section(text: String) = label(text, 17, Color.rgb(221, 186, 108), true).apply {
        gravity = Gravity.START
        setPadding(0, dp(10), 0, dp(8))
    }

    private fun info(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.rgb(223, 229, 234))
        setBackgroundColor(Color.rgb(20, 28, 36))
        setPadding(dp(13), dp(12), dp(13), dp(12))
        gravity = Gravity.START
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(9) }
    }

    private fun label(text: String, sp: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = sp.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
