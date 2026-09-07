package af.muhsiny.docstudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ExportEngineEndToEndTest {
    @Test
    fun imageAndNarrationProduceRealMp4() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File(target.cacheDir, "fixture_documentary.png")
        val audio = File(target.cacheDir, "fixture_narration.wav")
        createPng(image)
        createWav(audio, seconds = 3)

        val output = AtomicReference<Uri?>()
        val error = AtomicReference<String?>()
        val latch = CountDownLatch(1)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val project = DocumentaryProject(
                    title = "تست واقعی رندر",
                    script = "این یک تست واقعی برای تولید فایل ویدیویی است.",
                    mediaUris = mutableListOf(Uri.fromFile(image).toString())
                )
                val engine = ExportEngine(activity)
                engine.export(
                    project = project,
                    narrationUris = listOf(Uri.fromFile(audio)),
                    narrationDurationMs = 3000L,
                    onProgress = { },
                    onDone = { uri -> output.set(uri); latch.countDown() },
                    onError = { message -> error.set(message); latch.countDown() }
                )
            }

            assertTrue("Media3 export timed out", latch.await(90, TimeUnit.SECONDS))
            assertNull("Export failed: ${error.get()}", error.get())
            val uri = output.get()
            assertNotNull("No MP4 URI returned", uri)

            val resolver = target.contentResolver
            val length = resolver.openAssetFileDescriptor(uri!!, "r")?.use { it.length } ?: -1L
            assertTrue("MP4 is empty: $length", length > 5_000L)

            val duration = NarrationEngine.audioDurationMs(target, uri)
            assertTrue("MP4 duration invalid: $duration", duration >= 2_500L)
        }
    }

    private fun createPng(file: File) {
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }
        canvas.drawRGB(18, 24, 30)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 42f
        canvas.drawText("Documentary Studio", 120f, 185f, paint)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        check(file.length() > 1_000L)
    }

    private fun createWav(file: File, seconds: Int) {
        val sampleRate = 44_100
        val channels = 1
        val bits = 16
        val samples = sampleRate * seconds
        val dataSize = samples * channels * bits / 8
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8

        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bits.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            out.write(header.array())

            val toneHz = 220.0
            val amplitude = 0.18 * Short.MAX_VALUE
            val buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until samples) {
                val sample = (kotlin.math.sin(2.0 * Math.PI * toneHz * i / sampleRate) * amplitude).toInt().toShort()
                buffer.clear()
                buffer.putShort(sample)
                out.write(buffer.array())
            }
        }
        check(file.length() > 100_000L)
    }
}
