package af.muhsiny.docstudio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File

class NarrationEngine(
    private val context: Context,
    private val tts: TextToSpeech
) {
    data class Result(
        val uris: List<Uri>,
        val totalDurationMs: Long
    )

    fun synthesize(
        text: String,
        onProgress: (Int, Int) -> Unit,
        onDone: (Result) -> Unit,
        onError: (String) -> Unit
    ) {
        val chunks = ScenePlanner.ttsChunks(text)
        if (chunks.isEmpty()) return onError("متن نریشن خالی است")

        val dir = File(context.filesDir, "generated_narration").apply { mkdirs() }
        val runDir = File(dir, System.currentTimeMillis().toString()).apply { mkdirs() }
        val outputs = chunks.indices.map { File(runDir, "chunk_${(it + 1).toString().padStart(3, '0')}.wav") }
        var index = 0

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                val completed = index + 1
                onProgress(completed, chunks.size)
                index++
                if (index >= chunks.size) {
                    val uris = outputs.map(Uri::fromFile)
                    val total = outputs.sumOf { audioDurationMs(context, Uri.fromFile(it)) }
                    onDone(Result(uris, total.coerceAtLeast(1L)))
                } else {
                    synthesizeIndex(index, chunks, outputs, onError)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onError("ساخت بخش ${index + 1} نریشن ناموفق شد")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onError("ساخت بخش ${index + 1} نریشن ناموفق شد: $errorCode")
            }
        })

        synthesizeIndex(0, chunks, outputs, onError)
    }

    private fun synthesizeIndex(
        index: Int,
        chunks: List<String>,
        outputs: List<File>,
        onError: (String) -> Unit
    ) {
        val id = "docstudio_${System.currentTimeMillis()}_$index"
        val rc = tts.synthesizeToFile(chunks[index], Bundle(), outputs[index], id)
        if (rc != TextToSpeech.SUCCESS) onError("موتور گفتار بخش ${index + 1} را نپذیرفت")
    }

    companion object {
        fun audioDurationMs(context: Context, uri: Uri): Long {
            val r = MediaMetadataRetriever()
            return try {
                r.setDataSource(context, uri)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                0L
            } finally {
                runCatching { r.release() }
            }
        }
    }
}
