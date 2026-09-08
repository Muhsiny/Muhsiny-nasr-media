package af.muhsiny.docstudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun twoSceneProfessionalTimelineProducesPortraitMp4WithMixAndOverlays() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val image1 = File(target.cacheDir, "fixture_scene_1.png")
        val image2 = File(target.cacheDir, "fixture_scene_2.png")
        val narration1 = File(target.cacheDir, "fixture_narration_1.wav")
        val narration2 = File(target.cacheDir, "fixture_narration_2.wav")
        val sfx = File(target.cacheDir, "fixture_sfx.wav")
        val music = File(target.cacheDir, "fixture_music.wav")
        createPng(image1, "Scene One")
        createPng(image2, "Scene Two")
        createWav(narration1, seconds = 2, hz = 220.0)
        createWav(narration2, seconds = 2, hz = 260.0)
        createWav(sfx, seconds = 1, hz = 520.0)
        createWav(music, seconds = 2, hz = 110.0)

        val output = AtomicReference<ExportEngine.Done?>()
        val error = AtomicReference<String?>()
        val latch = CountDownLatch(1)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val project = DocumentaryProject(
                    title = "تست حرفه‌ای رندر",
                    script = "این صحنهٔ نخست است. این صحنهٔ دوم است.",
                    scenes = mutableListOf(
                        SceneItem(text = "این صحنهٔ نخست است.", mediaUri = Uri.fromFile(image1).toString(), sfxUri = Uri.fromFile(sfx).toString(), cropMode = "crop", subtitle = true),
                        SceneItem(text = "این صحنهٔ دوم است.", mediaUri = Uri.fromFile(image2).toString(), cropMode = "fit", subtitle = true)
                    ),
                    mediaUris = mutableListOf(Uri.fromFile(image1).toString(), Uri.fromFile(image2).toString()),
                    musicUri = Uri.fromFile(music).toString(),
                    logoUri = Uri.fromFile(image1).toString(),
                    aspectRatio = "9:16",
                    resolution = "720p",
                    narrationVolume = 100,
                    musicVolume = 8,
                    sfxVolume = 15,
                    subtitlesEnabled = true,
                    subtitleSize = 36
                )
                val engine = ExportEngine(activity)
                engine.export(
                    project = project,
                    narrationUris = listOf(Uri.fromFile(narration1), Uri.fromFile(narration2)),
                    sceneDurationsMs = listOf(2000L, 2000L),
                    onProgress = { },
                    onDone = { done -> output.set(done); latch.countDown() },
                    onError = { message -> error.set(message); latch.countDown() }
                )
            }

            assertTrue("Media3 export timed out", latch.await(150, TimeUnit.SECONDS))
            assertNull("Export failed: ${error.get()}", error.get())
            val done = output.get()
            assertNotNull("No MP4 returned", done)
            assertNotNull("SRT should be saved when subtitles are enabled", done!!.srtUri)

            val resolver = target.contentResolver
            val length = resolver.openAssetFileDescriptor(done.videoUri, "r")?.use { it.length } ?: -1L
            assertTrue("MP4 is empty: $length", length > 8_000L)

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(target, done.videoUri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            retriever.release()
            assertTrue("MP4 duration invalid: $duration", duration >= 3_500L)
            assertEquals(720, width)
            assertEquals(1280, height)
        }
    }

    private fun createPng(file: File, text: String) {
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true }
        canvas.drawRGB(18, 24, 30)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 42f
        canvas.drawText(text, 150f, 185f, paint)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        check(file.length() > 1_000L)
    }

    private fun createWav(file: File, seconds: Int, hz: Double) {
        val sampleRate = 44_100
        val channels = 1
        val bits = 16
        val samples = sampleRate * seconds
        val dataSize = samples * channels * bits / 8
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8

        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII)); header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII)); header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16); header.putShort(1); header.putShort(channels.toShort())
            header.putInt(sampleRate); header.putInt(byteRate); header.putShort(blockAlign.toShort()); header.putShort(bits.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII)); header.putInt(dataSize)
            out.write(header.array())
            val amplitude = 0.16 * Short.MAX_VALUE
            val buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until samples) {
                val sample = (kotlin.math.sin(2.0 * Math.PI * hz * i / sampleRate) * amplitude).toInt().toShort()
                buffer.clear(); buffer.putShort(sample); out.write(buffer.array())
            }
        }
        check(file.length() > 50_000L)
    }
}
