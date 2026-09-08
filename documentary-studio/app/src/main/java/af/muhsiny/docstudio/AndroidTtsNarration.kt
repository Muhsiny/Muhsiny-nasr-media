package af.muhsiny.docstudio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File

class AndroidTtsNarration(
    private val context: Context,
    private val tts: TextToSpeech
) {
    data class Result(val uris: List<Uri>, val durationsMs: List<Long>, val totalDurationMs: Long)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var cancelled = false

    fun cancel() {
        cancelled = true
        tts.stop()
    }

    fun synthesizeScenes(
        sceneTexts: List<String>,
        onProgress: (Int, Int) -> Unit,
        onDone: (Result) -> Unit,
        onError: (String) -> Unit
    ) {
        if (sceneTexts.isEmpty()) return onError("هیچ صحنه‌ای برای TTS وجود ندارد")
        if (sceneTexts.any { it.isBlank() }) return onError("یکی از صحنه‌ها متن ندارد")
        cancelled = false
        val dir = File(context.filesDir, "android_tts_narration/${System.currentTimeMillis()}").apply { mkdirs() }
        val files = sceneTexts.mapIndexed { index, _ -> File(dir, "scene_${(index + 1).toString().padStart(3, '0')}.wav") }
        val uris = mutableListOf<Uri>()
        val durations = mutableListOf<Long>()
        var index = 0

        fun finishError(message: String) {
            handler.post { onError(message) }
        }

        lateinit var synthesizeNext: () -> Unit
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onError(utteranceId: String?) = finishError("Android TTS در صحنه ${index + 1} خطا داد")
            override fun onError(utteranceId: String?, errorCode: Int) = finishError("Android TTS خطا داد: $errorCode")
            override fun onDone(utteranceId: String?) {
                if (cancelled) return
                val file = files[index]
                if (!file.exists() || file.length() < 1024L) return finishError("فایل TTS صحنه ${index + 1} ساخته نشد")
                val duration = mediaDurationMs(file)
                if (duration <= 100L) return finishError("مدت TTS صحنه ${index + 1} معتبر نیست")
                uris += Uri.fromFile(file)
                durations += duration
                val done = index + 1
                handler.post { onProgress(done, sceneTexts.size) }
                index++
                if (index >= sceneTexts.size) {
                    handler.post { onDone(Result(uris, durations, durations.sum())) }
                } else synthesizeNext()
            }
        })

        synthesizeNext = {
            if (cancelled) return@synthesizeNext
            val normalized = PersianTextNormalizer.normalize(sceneTexts[index])
            if (normalized.length > 3600) return@synthesizeNext finishError("متن صحنه ${index + 1} برای Android TTS بیش از حد طولانی است؛ آن را به دو صحنه تقسیم کن")
            val id = "docstudio_scene_${index}_${System.nanoTime()}"
            val result = tts.synthesizeToFile(normalized, Bundle(), files[index], id)
            if (result != TextToSpeech.SUCCESS) finishError("شروع TTS صحنه ${index + 1} ناموفق بود")
        }
        synthesizeNext()
    }

    private fun mediaDurationMs(file: File): Long = runCatching {
        val r = MediaMetadataRetriever()
        r.setDataSource(file.absolutePath)
        val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release(); ms
    }.getOrDefault(0L)
}
