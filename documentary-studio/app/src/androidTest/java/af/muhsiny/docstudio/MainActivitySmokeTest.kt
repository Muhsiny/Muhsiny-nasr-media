package af.muhsiny.docstudio

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    fun tabsRenderAndNavigateWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val initial = buttonTexts(root)
                assertTrue(initial.containsAll(listOf("پروژه", "صحنه‌ها", "رسانه", "صدا", "خروجی")))

                findButton(root, "رسانه")!!.performClick()
                assertNotNull(findButton(activity.window.decorView, "افزودن چند عکس یا ویدیو"))
                assertNotNull(findButton(activity.window.decorView, "انتخاب موزیک"))

                findButton(activity.window.decorView, "صدا")!!.performClick()
                assertNotNull(findButton(activity.window.decorView, "انتخاب MP3/WAV آماده"))
                assertNotNull(findButton(activity.window.decorView, "تست صدا"))

                findButton(activity.window.decorView, "خروجی")!!.performClick()
                assertNotNull(findButton(activity.window.decorView, "ساخت مستند روی همین گوشی"))
            }
        }
    }

    private fun buttonTexts(view: View): List<String> {
        val out = mutableListOf<String>()
        walk(view) { if (it is Button) out += it.text.toString() }
        return out
    }

    private fun findButton(view: View, text: String): Button? {
        var found: Button? = null
        walk(view) {
            if (found == null && it is Button && it.text.toString() == text) found = it
        }
        return found
    }

    private fun walk(view: View, visit: (View) -> Unit) {
        visit(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), visit)
    }
}
