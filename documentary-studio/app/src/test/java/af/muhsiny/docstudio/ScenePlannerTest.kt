package af.muhsiny.docstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenePlannerTest {
    @Test
    fun longNarrationNeverExceedsTtsLimit() {
        val paragraph = "این یک جملهٔ آزمایشی برای نریشن مستند فارسی است و باید بدون قطع نامناسب پردازش شود. "
        val text = paragraph.repeat(180)
        val chunks = ScenePlanner.ttsChunks(text, 3000)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.isNotBlank() && it.length <= 3000 })
        assertTrue(chunks.joinToString(" ").contains("نریشن مستند فارسی"))
    }

    @Test
    fun scenesAreStableAndNumbered() {
        val scenes = ScenePlanner.scenes("این صحنهٔ اول مستند است. این صحنهٔ دوم با توضیح کافی برای روایت ساخته شده است؟\nاین صحنهٔ سوم نیز ادامه دارد.")
        assertTrue(scenes.size >= 2)
        assertEquals(1, scenes.first().index)
        assertEquals(scenes.size, scenes.last().index)
    }

    @Test
    fun subtitleEndsAtRealNarrationDuration() {
        val total = 18_750L
        val srt = ScenePlanner.buildSrt("این نخستین بخش روایت مستند است. این دومین بخش روایت مستند است.", total)
        assertTrue(srt.contains("00:00:18,750"))
        assertTrue(srt.contains("-->"))
    }
}
