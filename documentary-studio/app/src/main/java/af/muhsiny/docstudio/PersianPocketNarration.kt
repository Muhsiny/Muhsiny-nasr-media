package af.muhsiny.docstudio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class PersianPocketNarration(private val context: Context) {
    data class Result(val uris: List<Uri>, val totalDurationMs: Long)

    private val handler = Handler(Looper.getMainLooper())
    private val root = File(context.filesDir, "pockettts_fa")
    private val modelDir = File(root, "model")
    private val voicesDir = File(root, "voices")
    private val generatedDir = File(context.filesDir, "pocket_narration")

    @Volatile private var running: OnDevicePocketTts? = null

    fun modelInstalled(): Boolean = requiredModels().all { File(modelDir, it).length() > 1024L }

    fun cancel() {
        running?.stop()
    }

    fun defaultVoiceReady(): Boolean = File(voicesDir, "example_voice.wav").length() > 4096L

    fun prepare(onDone: () -> Unit, onError: (String) -> Unit) {
        Thread {
            runCatching {
                modelDir.mkdirs(); voicesDir.mkdirs(); generatedDir.mkdirs()
                requiredModels().forEach { copyAssetIfNeeded("pockettts_fa/model/$it", File(modelDir, it)) }
                copyAssetIfNeeded("pockettts_fa/voices/example_voice.wav", File(voicesDir, "example_voice.wav"))
                check(modelInstalled()) { "فایل‌های مدل فارسی ناقص است" }
                check(defaultVoiceReady()) { "نمونهٔ صدای داخلی ناقص است" }
            }.onSuccess { handler.post(onDone) }
             .onFailure { e -> handler.post { onError(e.message ?: "آماده‌سازی مدل فارسی ناموفق بود") } }
        }.start()
    }

    fun synthesize(
        text: String,
        referenceUri: Uri?,
        onProgress: (Int, Int) -> Unit,
        onDone: (Result) -> Unit,
        onError: (String) -> Unit
    ) {
        val normalized = PersianTextNormalizer.normalize(text)
        val chunks = ScenePlanner.pocketTtsChunks(normalized)
        if (chunks.isEmpty()) return onError("متن نریشن خالی است")

        Thread {
            try {
                installAssetsSync()
                val voiceFile = if (referenceUri != null) copyUserVoice(referenceUri) else File(voicesDir, "example_voice.wav")
                checkValidWav(voiceFile)

                val runDir = File(generatedDir, System.currentTimeMillis().toString()).apply { mkdirs() }
                val outputs = mutableListOf<Uri>()
                var totalMs = 0L

                OnDevicePocketTts(
                    modelsDir = modelDir.absolutePath,
                    voicesDir = voicesDir.absolutePath,
                    precision = "int8",
                    temperature = 0.3f,
                    lsdSteps = 1,
                    threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                    sentencePauseMs = 150,
                    maxTextTokens = 48
                ).use { engine ->
                    running = engine
                    chunks.forEachIndexed { index, chunk ->
                        val out = File(runDir, "chunk_${(index + 1).toString().padStart(3, '0')}.wav")
                        PcmWavWriter(out, 24_000).use { writer ->
                            val ok = engine.synthesize(chunk, voiceFile.absolutePath, object : OnDevicePocketTts.AudioSink {
                                override fun onAudio(samples: FloatArray): Boolean {
                                    writer.write(samples)
                                    return true
                                }
                            })
                            check(ok) { "تولید بخش ${index + 1} نریشن فارسی ناموفق شد" }
                        }
                        check(out.length() > 2048L) { "خروجی بخش ${index + 1} خالی است" }
                        outputs += Uri.fromFile(out)
                        totalMs += wavDurationMs(out)
                        handler.post { onProgress(index + 1, chunks.size) }
                    }
                }
                running = null
                check(totalMs > 300L) { "مدت نریشن تولیدشده معتبر نیست" }
                handler.post { onDone(Result(outputs, totalMs)) }
            } catch (e: Throwable) {
                running = null
                handler.post { onError(e.message ?: "تولید نریشن فارسی ناموفق شد") }
            }
        }.start()
    }

    private fun installAssetsSync() {
        modelDir.mkdirs(); voicesDir.mkdirs(); generatedDir.mkdirs()
        requiredModels().forEach { copyAssetIfNeeded("pockettts_fa/model/$it", File(modelDir, it)) }
        copyAssetIfNeeded("pockettts_fa/voices/example_voice.wav", File(voicesDir, "example_voice.wav"))
        check(modelInstalled()) { "مدل فارسی داخل APK ناقص است" }
    }

    private fun copyUserVoice(uri: Uri): File {
        val target = File(voicesDir, "user_reference_${System.currentTimeMillis()}.wav")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { input.copyTo(it) }
        } ?: error("فایل نمونهٔ صدا باز نشد")
        checkValidWav(target)
        return target
    }

    private fun copyAssetIfNeeded(asset: String, target: File) {
        if (target.exists() && target.length() > 1024L) return
        target.parentFile?.mkdirs()
        context.assets.open(asset).use { input -> FileOutputStream(target).use { input.copyTo(it) } }
    }

    private fun checkValidWav(file: File) {
        check(file.length() > 4096L) { "نمونهٔ صدا بسیار کوتاه یا خالی است" }
        val header = ByteArray(12)
        file.inputStream().use { check(it.read(header) == 12) { "فایل WAV ناقص است" } }
        check(String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WAVE") {
            "برای کلون روی دستگاه، نمونهٔ صدا باید WAV معتبر باشد"
        }
    }

    private fun requiredModels() = listOf(
        "tokenizer.model",
        "mimi_encoder.onnx",
        "text_conditioner.onnx",
        "flow_lm_main_int8.onnx",
        "flow_lm_flow_int8.onnx",
        "mimi_decoder_int8.onnx"
    )

    private fun wavDurationMs(file: File): Long {
        if (file.length() <= 44L) return 0L
        val dataBytes = file.length() - 44L
        return (dataBytes * 1000L / (24_000L * 2L)).coerceAtLeast(1L)
    }

    private class PcmWavWriter(private val file: File, private val sampleRate: Int) : AutoCloseable {
        private val out = FileOutputStream(file)
        private var samplesWritten = 0L
        init { out.write(ByteArray(44)) }

        fun write(samples: FloatArray) {
            val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { f ->
                val s = (f.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
                buf.putShort(s)
            }
            out.write(buf.array())
            samplesWritten += samples.size
        }

        override fun close() {
            out.flush(); out.close()
            val dataSize = (samplesWritten * 2L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            RandomAccessFile(file, "rw").use { raf ->
                val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                h.put("RIFF".toByteArray(Charsets.US_ASCII)); h.putInt(36 + dataSize)
                h.put("WAVE".toByteArray(Charsets.US_ASCII)); h.put("fmt ".toByteArray(Charsets.US_ASCII))
                h.putInt(16); h.putShort(1); h.putShort(1)
                h.putInt(sampleRate); h.putInt(sampleRate * 2); h.putShort(2); h.putShort(16)
                h.put("data".toByteArray(Charsets.US_ASCII)); h.putInt(dataSize)
                raf.seek(0); raf.write(h.array())
            }
        }
    }
}

object PersianTextNormalizer {
    private val harakat = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val digits = Regex("[0-9۰-۹٠-٩]+")

    fun normalize(input: String): String {
        var s = input
            .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
            .replace('ۀ', 'ه').replace('ة', 'ه')
            .replace(harakat, "")
            .replace(Regex("[ \\t]+"), " ")
        s = digits.replace(s) { m ->
            val ascii = m.value.map { digitToAscii(it) }.joinToString("")
            val n = ascii.toLongOrNull()
            if (n != null && n <= 999_999_999_999L) numberToWords(n) else m.value
        }
        return s.trim()
    }

    private fun digitToAscii(c: Char): Char = when (c) {
        in '۰'..'۹' -> ('0'.code + (c.code - '۰'.code)).toChar()
        in '٠'..'٩' -> ('0'.code + (c.code - '٠'.code)).toChar()
        else -> c
    }

    private fun numberToWords(n: Long): String {
        if (n == 0L) return "صفر"
        val groups = listOf(1_000_000_000L to "میلیارد", 1_000_000L to "میلیون", 1_000L to "هزار")
        var rest = n
        val parts = mutableListOf<String>()
        for ((value, name) in groups) {
            if (rest >= value) {
                parts += "${underThousand(rest / value)} $name"
                rest %= value
            }
        }
        if (rest > 0) parts += underThousand(rest)
        return parts.joinToString(" و ")
    }

    private fun underThousand(v: Long): String {
        var n = v.toInt()
        val out = mutableListOf<String>()
        val hundreds = arrayOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
        if (n >= 100) { out += hundreds[n / 100]; n %= 100 }
        if (n > 0) {
            val units = arrayOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه", "ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
            val tens = arrayOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
            if (n < 20) out += units[n]
            else {
                out += tens[n / 10]
                if (n % 10 > 0) out += units[n % 10]
            }
        }
        return out.joinToString(" و ")
    }
}
