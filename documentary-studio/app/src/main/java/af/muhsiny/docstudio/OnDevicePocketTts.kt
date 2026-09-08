package af.muhsiny.docstudio

internal class OnDevicePocketTts(
    modelsDir: String,
    voicesDir: String,
    precision: String = "int8",
    temperature: Float = 0.3f,
    lsdSteps: Int = 1,
    threads: Int = 2,
    sentencePauseMs: Int = 150,
    maxTextTokens: Int = 48
) : AutoCloseable {
    private var handle: Long = 0L

    init {
        System.loadLibrary("pockettts_jni")
        handle = nativeCreate(
            modelsDir,
            voicesDir,
            precision,
            temperature,
            lsdSteps,
            threads,
            sentencePauseMs,
            maxTextTokens
        )
        check(handle != 0L) { "مدل فارسی PocketTTS روی دستگاه بارگذاری نشد" }
    }

    fun synthesize(text: String, voiceFile: String, sink: AudioSink): Boolean =
        nativeSynthesize(handle, text, voiceFile, sink)

    fun stop() {
        if (handle != 0L) nativeStop(handle)
    }

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    interface AudioSink {
        fun onAudio(samples: FloatArray): Boolean
    }

    private external fun nativeCreate(
        modelsDir: String,
        voicesDir: String,
        precision: String,
        temperature: Float,
        lsdSteps: Int,
        threads: Int,
        sentencePauseMs: Int,
        maxTextTokens: Int
    ): Long

    private external fun nativeSynthesize(
        handle: Long,
        text: String,
        voiceFile: String,
        sink: AudioSink
    ): Boolean

    private external fun nativeStop(handle: Long)
    private external fun nativeDestroy(handle: Long)
}
