package af.muhsiny.docstudio

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    fun allProfessionalTabsExposeRealControlsAndTimelineActions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertTrue(buttonTexts(root).containsAll(listOf("پروژه", "تایم‌لاین", "رسانه", "نریشن", "صدا/برند", "خروجی")))

                findButton(root, "پروژه")!!.performClick()
                assertNotNull(findButton(activity.window.decorView, "پروژه جدید"))
                assertNotNull(findButton(activity.window.decorView, "کپی پروژه"))
                assertNotNull(findButton(activity.window.decorView, "وارد کردن TXT"))
                assertNotNull(findButton(activity.window.decorView, "ساخت/بازسازی تایم‌لاین از سناریو"))
                assertTrue(countType<EditText>(activity.window.decorView) >= 2)

                findButton(activity.window.decorView, "تایم‌لاین")!!.performClick()
                assertNotNull(findButton(activity.window.decorView, "+ صحنه"))
                assertNotNull(findButton(activity.window.decorView, "تخصیص خودکار رسانه"))

                findButton(activity.window.decorView, "رسانه")!!.performClick()
                assertNotNull(findButton(activity.window.decorView, "افزودن عکس/ویدیو"))
                assertNotNull(findButton(activity.window.decorView, "انتخاب PNG/JPG لوگو"))

                findButton(activity.window.decorView, "نریشن")!!.performClick()
                assertNotNull(findButton(activity.window.decorView, "آماده‌سازی مدل"))
                assertNotNull(findButton(activity.window.decorView, "تست راوی"))
                assertNotNull(findButton(activity.window.decorView, "انتخاب WAV"))
                assertNotNull(findButton(activity.window.decorView, "تست کلون"))
                assertNotNull(findButton(activity.window.decorView, "انتخاب MP3/WAV"))
                assertNotNull(findButton(activity.window.decorView, "تست Android TTS"))
                assertTrue(countType<Spinner>(activity.window.decorView) >= 1)

                findButton(activity.window.decorView, "صدا/برند")!!.performClick()
                assertNotNull(findButton(activity.window.decorView, "انتخاب موسیقی"))
                assertNotNull(findButton(activity.window.decorView, "انتخاب لوگو"))
                assertTrue(countType<SeekBar>(activity.window.decorView) >= 4)
                assertTrue(countType<CheckBox>(activity.window.decorView) >= 1)

                findButton(activity.window.decorView, "خروجی")!!.performClick()
                assertNotNull(findButton(activity.window.decorView, "ساخت مستند نهایی"))
                assertNotNull(findButton(activity.window.decorView, "لغو رندر"))
                assertNotNull(findButton(activity.window.decorView, "بازکردن آخرین MP4"))
                assertTrue(countType<Spinner>(activity.window.decorView) >= 2)
            }
        }
    }

    private inline fun <reified T : View> countType(view: View): Int {
        var count = 0; walk(view) { if (it is T) count++ }; return count
    }

    private fun buttonTexts(view: View): List<String> {
        val out = mutableListOf<String>(); walk(view) { if (it is Button) out += it.text.toString() }; return out
    }

    private fun findButton(view: View, text: String): Button? {
        var found: Button? = null; walk(view) { if (found == null && it is Button && it.text.toString() == text) found = it }; return found
    }

    private fun walk(view: View, visit: (View) -> Unit) {
        visit(view); if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), visit)
    }
}
