package af.muhsiny.docstudio

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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

@RunWith(AndroidJUnit4::class)
class V7OpenAiIntegrationTest {
    @Test
    fun localOpenAiDirectorAndGeneratedAssetFlowIntoRealProject() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = ProjectRepository(context)
        val project = repo.currentOrCreate().apply {
            title = "V7 CI Project"
            script = "این نخستین صحنهٔ آزمایشی برای کارگردان هوشمند است. این دومین صحنهٔ آزمایشی است."
            scenes.clear()
            mediaUris.clear()
        }
        repo.save(project)
        context.getSharedPreferences("open_studio_v7", 0).edit()
            .putString("server", "http://10.0.2.2:8190")
            .putString("token", "ci-token")
            .commit()

        ActivityScenario.launch(V7MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                assertNotNull(findButton(root, "بازکردن استدیوی تدوین V6"))
                assertNotNull(findButton(root, "بررسی و شناسایی قابلیت‌های واقعی"))
                findButton(root, "بررسی و شناسایی قابلیت‌های واقعی")!!.performClick()
            }
            assertTrue("Capabilities did not load", waitForText(scenario, "کارگردان AI: ✓", 15_000))

            scenario.onActivity { activity ->
                val director = findButton(activity.window.decorView, "ساخت پلان هوشمند از سناریو")
                assertNotNull(director)
                assertTrue(director!!.isEnabled)
                director.performClick()
            }
            assertTrue("Director plan was not produced", waitForText(scenario, "پلان هوشمند ذخیره شد", 20_000))

            scenario.onActivity { activity -> findButton(activity.window.decorView, "اعمال پلان به تایم‌لاین")!!.performClick() }
            assertTrue("Plan was not applied", waitForText(scenario, "صحنه وارد تایم‌لاین شد", 8_000))
            val planned = repo.currentOrCreate()
            assertEquals(2, planned.scenes.size)

            scenario.onActivity { activity ->
                val image = findButton(activity.window.decorView, "ساخت تصاویر مولد صحنه‌ها")
                assertNotNull(image)
                assertTrue(image!!.isEnabled)
                image.performClick()
            }
            assertTrue("Generated image flow did not finish", waitForText(scenario, "تولید تمام شد", 25_000))
        }

        val finalProject = repo.currentOrCreate()
        assertEquals(2, finalProject.scenes.size)
        val first = finalProject.scenes[0].mediaUri
        assertNotNull("Generated scene did not receive media", first)
        val file = File(Uri.parse(first).path!!)
        assertTrue("Generated asset is missing", file.exists())
        assertTrue("Generated asset is too small", file.length() > 512L)
        assertNull("Archive scene must not be silently replaced with generated media", finalProject.scenes[1].mediaUri)
    }

    private fun waitForText(scenario: ActivityScenario<V7MainActivity>, needle: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            var found = false
            scenario.onActivity { activity -> found = allText(activity.window.decorView).contains(needle) }
            if (found) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun allText(view: View): String {
        val out = StringBuilder()
        walk(view) { if (it is TextView) out.append(it.text).append('\n') }
        return out.toString()
    }

    private fun findButton(view: View, text: String): Button? {
        var found: Button? = null
        walk(view) { if (found == null && it is Button && it.text.toString() == text) found = it }
        return found
    }

    private fun walk(view: View, visit: (View) -> Unit) {
        visit(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), visit)
    }
}
