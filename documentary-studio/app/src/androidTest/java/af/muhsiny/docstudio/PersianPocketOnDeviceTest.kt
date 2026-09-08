package af.muhsiny.docstudio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PersianPocketOnDeviceTest {
    @Test
    fun twoPersianScenesCreateTwoRealWavsAndFeedSceneSyncedMp4() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val narration = PersianPocketNarration(context)
        val voiceLatch = CountDownLatch(1)
        val voiceError = AtomicReference<String?>()
        val voiceResult = AtomicReference<PersianPocketNarration.SceneResult?>()
        val texts = listOf(
            "در سال ۱۳۸۵، نخستین بخش این روایت فارسی با صدای داخلی ساخته می‌شود.",
            "در بخش دوم، نریشن باید جداگانه تولید شود و زمان واقعی خودش را داشته باشد."
        )

        narration.synthesizeScenes(
            texts,
            null,
            { _, _ -> },
            { result -> voiceResult.set(result); voiceLatch.countDown() },
            { error -> voiceError.set(error); voiceLatch.countDown() }
        )

        assertTrue("Persian scene synthesis timed out", voiceLatch.await(240, TimeUnit.SECONDS))
        assertNull("Persian synthesis failed: ${voiceError.get()}", voiceError.get())
        val result = voiceResult.get() ?: error("No Persian scene narration result")
        assertEquals(2, result.uris.size)
        assertEquals(2, result.durationsMs.size)
        assertEquals(result.durationsMs.sum(), result.totalDurationMs)
        assertTrue(result.durationsMs.all { it > 400L })
        result.uris.forEach { uri ->
            val wav = File(uri.path!!)
            assertTrue("Generated WAV empty: ${wav.length()}", wav.length() > 10_000L)
        }

        val image1 = File(context.cacheDir, "pocket_scene_1.png")
        val image2 = File(context.cacheDir, "pocket_scene_2.png")
        createPng(image1, "Persian Scene 1")
        createPng(image2, "Persian Scene 2")

        val output = AtomicReference<ExportEngine.Done?>()
        val renderError = AtomicReference<String?>()
        val renderLatch = CountDownLatch(1)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val project = DocumentaryProject(
                    title = "تست فارسی مستقل دو صحنه",
                    script = texts.joinToString(" "),
                    scenes = mutableListOf(
                        SceneItem(text = texts[0], mediaUri = Uri.fromFile(image1).toString(), cropMode = "crop", subtitle = true),
                        SceneItem(text = texts[1], mediaUri = Uri.fromFile(image2).toString(), cropMode = "fit", subtitle = true)
                    ),
                    mediaUris = mutableListOf(Uri.fromFile(image1).toString(), Uri.fromFile(image2).toString()),
                    aspectRatio = "16:9",
                    resolution = "720p",
                    subtitlesEnabled = true
                )
                ExportEngine(activity).export(
                    project,
                    result.uris,
                    result.durationsMs,
                    { },
                    { done -> output.set(done); renderLatch.countDown() },
                    { message -> renderError.set(message); renderLatch.countDown() }
                )
            }
            assertTrue("Persian MP4 render timed out", renderLatch.await(150, TimeUnit.SECONDS))
        }

        assertNull("MP4 failed: ${renderError.get()}", renderError.get())
        val done = output.get() ?: error("No MP4 result")
        val size = context.contentResolver.openAssetFileDescriptor(done.videoUri, "r")?.use { it.length } ?: -1L
        assertTrue("MP4 is empty: $size", size > 8_000L)
        assertTrue("Expected SRT for Persian timeline", done.srtUri != null)
    }

    private fun createPng(file: File, text: String) {
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true; color = android.graphics.Color.WHITE; textSize = 36f }
        canvas.drawRGB(18, 24, 30)
        canvas.drawText(text, 120f, 185f, paint)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        check(file.length() > 1_000L)
    }
}
