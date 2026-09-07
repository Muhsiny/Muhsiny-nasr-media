package af.muhsiny.docstudio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import java.io.File

@UnstableApi
class ExportEngine(private val context: Context) {
    private var transformer: Transformer? = null

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }

    fun export(
        project: DocumentaryProject,
        narrationUris: List<Uri>,
        narrationDurationMs: Long,
        onProgress: (Int) -> Unit,
        onDone: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        if (project.mediaUris.isEmpty()) return onError("هیچ عکس یا ویدیویی در پروژه نیست")
        if (narrationUris.isEmpty()) return onError("نریشن موجود نیست")
        if (transformer != null) return onError("یک رندر دیگر در حال اجرا است")

        try {
            val visuals = buildVisualItems(project, narrationDurationMs)
            val visualSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
                .addItems(visuals)
                .setIsLooping(true)
                .build()

            val narrationItems = narrationUris.map { uri ->
                EditedMediaItem.Builder(MediaItem.fromUri(uri))
                    .setEffects(audioGain(1.0f))
                    .build()
            }
            val narrationSequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                .addItems(narrationItems)
                .build()

            val sequences = mutableListOf(visualSequence, narrationSequence)
            project.musicUri?.let { raw ->
                val music = EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(raw)))
                    .setEffects(audioGain(0.14f))
                    .build()
                sequences += EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                    .addItem(music)
                    .setIsLooping(true)
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
                        val stored = saveToGallery(project.title, temp)
                        temp.delete()
                        if (stored != null) onDone(stored) else onError("ویدیو ساخته شد اما ذخیره در گالری ناموفق بود")
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        transformer = null
                        onError(exportException.message ?: exportException.errorCodeName)
                    }
                })
                .build()

            transformer?.start(composition, temp.absolutePath)
            pollProgress(onProgress)
        } catch (e: Exception) {
            transformer = null
            onError(e.message ?: "شروع رندر ناموفق بود")
        }
    }

    private fun buildVisualItems(project: DocumentaryProject, durationMs: Long): List<EditedMediaItem> {
        val count = project.mediaUris.size.coerceAtLeast(1)
        val imageDuration = (durationMs / count).coerceIn(2500L, 12000L)
        return project.mediaUris.map { raw ->
            val uri = Uri.parse(raw)
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("image/")) {
                val item = MediaItem.Builder().setUri(uri).setImageDurationMs(imageDuration).build()
                EditedMediaItem.Builder(item).setFrameRate(30).build()
            } else {
                EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveAudio(true).build()
            }
        }
    }

    private fun audioGain(gain: Float): Effects {
        val mix = ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(1, 2).scaleBy(gain))
            putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(2, 2).scaleBy(gain))
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

    private fun saveToGallery(title: String, temp: File): Uri? {
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
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return null
                val out = File(dir, safeName(title) + "_${System.currentTimeMillis()}.mp4")
                temp.copyTo(out, overwrite = true)
                Uri.fromFile(out)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun safeName(text: String): String = text
        .replace(Regex("[^\\p{L}\\p{N}_ -]"), "")
        .trim()
        .replace(' ', '_')
        .take(60)
        .ifBlank { "Mostanad" }
}
