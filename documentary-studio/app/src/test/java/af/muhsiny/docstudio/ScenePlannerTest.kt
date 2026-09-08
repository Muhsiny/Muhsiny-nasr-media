package af.muhsiny.docstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePlannerTest {
    @Test
    fun longNarrationNeverExceedsAndroidTtsLimit() {
        val paragraph = "این یک جملهٔ آزمایشی برای نریشن مستند فارسی است و باید بدون قطع نامناسب پردازش شود. "
        val chunks = ScenePlanner.ttsChunks(paragraph.repeat(180), 3000)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.isNotBlank() && it.length <= 3000 })
    }

    @Test
    fun pocketTtsChunksAreShortAndNonEmpty() {
        val paragraph = "در این مستند، اسناد تاریخی و روایت‌های شاهدان را با دقت بررسی می‌کنیم تا تصویر روشن‌تری از رویداد به دست آید. "
        val chunks = ScenePlanner.pocketTtsChunks(paragraph.repeat(30))
        assertTrue(chunks.size > 5)
        assertTrue(chunks.all { it.isNotBlank() && it.length <= 180 && it.split(Regex("\\s+")).size <= 24 })
    }

    @Test
    fun persianNormalizerFixesLettersAndReadsNumbers() {
        val normalized = PersianTextNormalizer.normalize("در سال ۱۳۸۵، يك رويداد مهم ثبت شد.")
        assertTrue(normalized.contains("یک"))
        assertTrue(normalized.contains("یک هزار و سیصد و هشتاد و پنج"))
        assertFalse(normalized.contains("۱۳۸۵"))
        assertFalse(normalized.contains("يك"))
    }

    @Test
    fun timelineRebuildPreservesSceneAssignmentsByPosition() {
        val project = DocumentaryProject(
            script = "این صحنهٔ اول مستند است و توضیح کافی دارد. این صحنهٔ دوم مستند است و توضیح کافی دارد.",
            mediaUris = mutableListOf("file:///a.jpg", "file:///b.jpg"),
            scenes = mutableListOf(
                SceneItem(text = "قدیمی اول", mediaUri = "file:///a.jpg", sfxUri = "file:///a.wav", cropMode = "fit", clipStartMs = 2200),
                SceneItem(text = "قدیمی دوم", mediaUri = "file:///b.jpg")
            )
        )
        ScenePlanner.rebuildProjectScenes(project)
        assertTrue(project.scenes.size >= 2)
        assertEquals("file:///a.jpg", project.scenes[0].mediaUri)
        assertEquals("file:///a.wav", project.scenes[0].sfxUri)
        assertEquals("fit", project.scenes[0].cropMode)
        assertEquals(2200L, project.scenes[0].clipStartMs)
    }

    @Test
    fun automaticMediaAssignmentCoversEveryScene() {
        val project = DocumentaryProject(
            mediaUris = mutableListOf("file:///1.jpg", "file:///2.mp4"),
            scenes = MutableList(7) { SceneItem(text = "صحنه $it") }
        )
        ScenePlanner.autoAssignMedia(project)
        assertTrue(project.scenes.all { !it.mediaUri.isNullOrBlank() })
        assertEquals("file:///1.jpg", project.scenes[0].mediaUri)
        assertEquals("file:///2.mp4", project.scenes[1].mediaUri)
        assertEquals("file:///1.jpg", project.scenes[2].mediaUri)
    }

    @Test
    fun allocatedSceneDurationsCoverWholeNarration() {
        val total = 18_750L
        val texts = listOf("بخش اول کوتاه", "این بخش دوم کمی طولانی‌تر از بخش اول است", "پایان")
        val durations = ScenePlanner.allocateSceneDurations(texts, total)
        assertEquals(texts.size, durations.size)
        assertEquals(total, durations.sum())
        assertTrue(durations.all { it > 0 })
    }

    @Test
    fun validationDetectsMissingMediaButAcceptsCompleteTimeline() {
        val project = DocumentaryProject(
            title = "تست",
            script = "متن",
            scenes = mutableListOf(SceneItem(text = "یک صحنه")),
            musicVolume = 10
        )
        assertTrue(ScenePlanner.validate(project).any { !it.ok && it.message.contains("رسانه") })
        project.scenes[0].mediaUri = "file:///scene.jpg"
        assertFalse(ScenePlanner.validate(project).any { !it.ok && it.message.contains("رسانه") })
    }

    @Test
    fun subtitleEndsAtExactSceneTimelineDuration() {
        val texts = listOf("بخش نخست", "بخش دوم")
        val durations = listOf(4_250L, 14_500L)
        val srt = ScenePlanner.buildSrtFromScenes(texts, durations)
        assertTrue(srt.contains("00:00:18,750"))
        assertTrue(srt.contains("00:00:04,250"))
        assertTrue(srt.contains("-->"))
    }
}
