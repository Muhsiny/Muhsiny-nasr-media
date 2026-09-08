package af.muhsiny.docstudio

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

@UnstableApi
class ExportEngine(private val context: Context) {
    data class Done(val videoUri: Uri, val srtUri: Uri?)

    private var transformer: Transformer? = null

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }

    fun export(
        project: DocumentaryProject,
        narrationUris: List<Uri>,
        sceneDurationsMs: List<Long>,
        onProgress: (Int) -> Unit,
        onDone: (Done) -> Unit,
        onError: (String) -> Unit
    ) {
        val scenes = project.scenes.filter { it.text.isNotBlank() }
        if (scenes.isEmpty()) return onError("هیچ صحنهٔ معتبری وجود ندارد")
        if (narrationUris.size != scenes.size) return onError("تعداد فایل‌های نریشن با صحنه‌ها برابر نیست")
        if (sceneDurationsMs.size != scenes.size || sceneDurationsMs.any { it <= 0L }) return onError("زمان‌بندی صحنه‌ها معتبر نیست")
        if (scenes.any { it.mediaUri.isNullOrBlank() }) return onError("برای همهٔ صحنه‌ها رسانه تعیین کن")
        if (transformer != null) return onError("یک رندر دیگر در حال اجرا است")

        try {
            val visualItems = buildVisualItems(project, scenes, sceneDurationsMs)
            val visualSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
                .addItems(visualItems)
                .build()

            val narrationItems = narrationUris.map { uri ->
                EditedMediaItem.Builder(MediaItem.fromUri(uri))
                    .setEffects(audioGain(project.narrationVolume / 100f))
                    .build()
            }
            val narrationSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                .addItems(narrationItems)
                .build()

            val sequences = mutableListOf(visualSequence, narrationSequence)

            project.musicUri?.let { raw ->
                val music = EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(raw)))
                    .setEffects(audioGain(project.musicVolume / 100f))
                    .build()
                sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                    .addItem(music)
                    .setIsLooping(true)
                    .build()
            }

            if (scenes.any { !it.sfxUri.isNullOrBlank() }) {
                val sfxItems = buildSfxItems(project, scenes, sceneDurationsMs)
                sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                    .addItems(sfxItems)
                    .build()
            }

            val composition = Composition.Builder(sequences).build()
            val tempDir = File(context.cacheDir, "docstudio_exports").apply { mkdirs() }
            val temp = File(tempDir, "export_${System.currentTimeMillis()}.mp4")

            transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        transformer = null
                        val video = saveVideo(project.title, temp)
                        temp.delete()
                        if (video == null) return onError("ویدیو ساخته شد اما ذخیره در گالری ناموفق بود")
                        val srt = if (project.subtitlesEnabled) {
                            saveSrt(project.title, ScenePlanner.buildSrtFromScenes(scenes.map { it.text }, sceneDurationsMs))
                        } else null
                        onDone(Done(video, srt))
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        transformer = null
                        onError(exportException.message ?: exportException.errorCodeName)
                    }
                })
                .build()

            transformer?.start(composition, temp.absolutePath)
            pollProgress(onProgress)
        } catch (e: Throwable) {
            transformer = null
            onError(e.message ?: "شروع رندر ناموفق بود")
        }
    }

    private fun buildVisualItems(project: DocumentaryProject, scenes: List<SceneItem>, durations: List<Long>): List<EditedMediaItem> {
        val output = mutableListOf<EditedMediaItem>()
        scenes.forEachIndexed { index, scene ->
            val raw = scene.mediaUri ?: error("رسانهٔ صحنه ${index + 1} تعیین نشده")
            val uri = Uri.parse(raw)
            val mime = mediaMime(uri, raw)
            val duration = durations[index].coerceAtLeast(500L)
            val effects = sceneVideoEffects(project, scene)
            if (mime.startsWith("image/")) {
                val item = MediaItem.Builder().setUri(uri).setImageDurationMs(duration).build()
                output += EditedMediaItem.Builder(item).setFrameRate(30).setEffects(effects).build()
            } else if (mime.startsWith("video/")) {
                output += buildRepeatedVideo(uri, duration, scene.clipStartMs, effects)
            } else error("نوع رسانهٔ صحنه ${index + 1} پشتیبانی نمی‌شود")
        }
        return output
    }

    private fun buildRepeatedVideo(uri: Uri, targetMs: Long, requestedStartMs: Long, effects: Effects): List<EditedMediaItem> {
        val sourceMs = mediaDurationMs(uri).coerceAtLeast(targetMs)
        val start = requestedStartMs.coerceIn(0L, (sourceMs - 300L).coerceAtLeast(0L))
        val available = (sourceMs - start).coerceAtLeast(300L)
        var remaining = targetMs
        val items = mutableListOf<EditedMediaItem>()
        while (remaining > 0L) {
            val part = min(remaining, available)
            val clip = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(start)
                .setEndPositionMs((start + part).coerceAtMost(sourceMs))
                .build()
            val media = MediaItem.Builder().setUri(uri).setClippingConfiguration(clip).build()
            items += EditedMediaItem.Builder(media).setRemoveAudio(true).setEffects(effects).build()
            remaining -= part
        }
        return items
    }

    private fun sceneVideoEffects(project: DocumentaryProject, scene: SceneItem): Effects {
        val (width, height) = outputSize(project)
        val layout = if (scene.cropMode == "fit") Presentation.LAYOUT_SCALE_TO_FIT else Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        val videoEffects = mutableListOf<Effect>(Presentation.createForWidthAndHeight(width, height, layout))
        val overlays = mutableListOf<TextureOverlay>()

        if (project.subtitlesEnabled && scene.subtitle && scene.text.isNotBlank()) {
            overlays += subtitleOverlay(scene.text, project.subtitleSize, height)
        }
        project.logoUri?.let { raw ->
            val settings = StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(0.90f, 0.88f)
                .setOverlayFrameAnchor(1f, 1f)
                .setScale(0.16f, 0.16f)
                .setAlphaScale(0.92f)
                .build()
            overlays += BitmapOverlay.createStaticBitmapOverlay(context, Uri.parse(raw), settings)
        }
        if (overlays.isNotEmpty()) videoEffects += OverlayEffect(overlays)
        return Effects(emptyList(), videoEffects)
    }

    private fun subtitleOverlay(text: String, requestedSize: Int, outputHeight: Int): TextOverlay {
        val wrapped = wrapSubtitle(text, if (outputHeight >= 1000) 52 else 42)
        val span = SpannableString(wrapped)
        span.setSpan(ForegroundColorSpan(Color.WHITE), 0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(BackgroundColorSpan(Color.argb(185, 0, 0, 0)), 0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(StyleSpan(Typeface.BOLD), 0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val px = (requestedSize * outputHeight / 720f).roundToInt().coerceIn(28, 110)
        span.setSpan(AbsoluteSizeSpan(px, false), 0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, span.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f, -0.78f)
            .setOverlayFrameAnchor(0f, -1f)
            .setScale(0.84f, 0.84f)
            .build()
        return TextOverlay.createStaticTextOverlay(span, settings)
    }

    private fun wrapSubtitle(text: String, maxChars: Int): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isNotEmpty() && current.length + word.length + 1 > maxChars) {
                lines += current.toString(); current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.take(3).joinToString("\n")
    }

    private fun buildSfxItems(project: DocumentaryProject, scenes: List<SceneItem>, durations: List<Long>): List<EditedMediaItem> {
        val silence = ensureSilenceWav()
        val out = mutableListOf<EditedMediaItem>()
        scenes.forEachIndexed { index, scene ->
            val uri = scene.sfxUri?.let(Uri::parse) ?: Uri.fromFile(silence)
            val gain = if (scene.sfxUri == null) 0f else project.sfxVolume / 100f
            out += buildRepeatedAudio(uri, durations[index], gain)
        }
        return out
    }

    private fun buildRepeatedAudio(uri: Uri, targetMs: Long, gain: Float): List<EditedMediaItem> {
        val sourceMs = mediaDurationMs(uri).coerceAtLeast(1000L)
        var remaining = targetMs
        val out = mutableListOf<EditedMediaItem>()
        while (remaining > 0L) {
            val part = min(remaining, sourceMs)
            val clip = MediaItem.ClippingConfiguration.Builder().setEndPositionMs(part).build()
            val item = MediaItem.Builder().setUri(uri).setClippingConfiguration(clip).build()
            out += EditedMediaItem.Builder(item).setEffects(audioGain(gain)).build()
            remaining -= part
        }
        return out
    }

    internal fun mediaMime(uri: Uri, raw: String = uri.toString()): String {
        val resolverMime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (resolverMime.isNotBlank()) return resolverMime
        val lower = raw.substringBefore('?').lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".m4a") -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }

    private fun mediaDurationMs(uri: Uri): Long {
        return runCatching {
            val r = MediaMetadataRetriever()
            r.setDataSource(context, uri)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            r.release(); ms
        }.getOrDefault(0L)
    }

    private fun outputSize(project: DocumentaryProject): Pair<Int, Int> {
        val long = if (project.resolution == "720p") 1280 else 1920
        val short = if (project.resolution == "720p") 720 else 1080
        return when (project.aspectRatio) {
            "9:16" -> short to long
            "1:1" -> short to short
            else -> long to short
        }
    }

    private fun audioGain(gain: Float): Effects {
        val safe = gain.coerceIn(0f, 1.5f)
        val mix = ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(1, 2).scaleBy(safe))
            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(2, 2).scaleBy(safe))
        }
        return Effects(listOf(ToInt16PcmAudioProcessor(), mix), emptyList())
    }

    private fun pollProgress(onProgress: (Int) -> Unit) {
        val current = transformer ?: return
        val holder = ProgressHolder()
        val state = runCatching { current.getProgress(holder) }.getOrNull() ?: return
        if (state == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress.coerceIn(0, 99))
        if (transformer != null) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ pollProgress(onProgress) }, 500)
    }

    private fun ensureSilenceWav(): File {
        val file = File(context.cacheDir, "docstudio_silence_1s.wav")
        if (file.exists() && file.length() > 1000L) return file
        val sampleRate = 44_100
        val dataSize = sampleRate * 2
        FileOutputStream(file).use { it.write(ByteArray(44 + dataSize)) }
        RandomAccessFile(file, "rw").use { raf ->
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray(Charsets.US_ASCII)); h.putInt(36 + dataSize)
            h.put("WAVE".toByteArray(Charsets.US_ASCII)); h.put("fmt ".toByteArray(Charsets.US_ASCII))
            h.putInt(16); h.putShort(1); h.putShort(1)
            h.putInt(sampleRate); h.putInt(sampleRate * 2); h.putShort(2); h.putShort(16)
            h.put("data".toByteArray(Charsets.US_ASCII)); h.putInt(dataSize)
            raf.seek(0); raf.write(h.array())
        }
        return file
    }

    private fun saveVideo(title: String, temp: File): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, safeName(title) + "_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/DocStudio")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } } ?: return null
                values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return null
                val out = File(dir, safeName(title) + "_${System.currentTimeMillis()}.mp4")
                temp.copyTo(out, overwrite = true)
                Uri.fromFile(out)
            }
        } catch (_: Exception) { null }
    }

    private fun saveSrt(title: String, content: String): Uri? {
        if (content.isBlank()) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName(title) + "_${System.currentTimeMillis()}.srt")
                    put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocStudio")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
                uri
            } else null
        } catch (_: Exception) { null }
    }

    private fun safeName(text: String): String = text
        .replace(Regex("[^\\p{L}\\p{N}_ -]"), "")
        .trim().replace(' ', '_').take(60).ifBlank { "Mostanad" }
}
