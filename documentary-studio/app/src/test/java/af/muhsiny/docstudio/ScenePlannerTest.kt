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
