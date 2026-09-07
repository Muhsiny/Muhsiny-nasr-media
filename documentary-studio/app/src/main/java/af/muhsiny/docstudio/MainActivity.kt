package af.muhsiny.docstudio

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var script: EditText
    private lateinit var engineUrl: EditText
    private lateinit var projectTitle: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 15, 20)
        window.navigationBarColor = Color.rgb(10, 15, 20)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(buildUi())
        restore()
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(10, 15, 20)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(24), dp(18), dp(32))
        }

        root.addView(label("استدیوی مستند", 30, Color.WHITE, true))
        root.addView(label("مستندسازی فارسی/دری با تمرکز بر تصویر و ویدیوی واقع‌گرایانه", 14, Color.rgb(174,184,194), false).apply {
            setPadding(0, dp(8), 0, dp(20))
        })

        root.addView(sectionTitle("۱. پروژه"))
        projectTitle = edit("نام پروژه", false)
        root.addView(projectTitle)

        root.addView(sectionTitle("۲. سناریو و نریشن"))
        script = edit("متن مستند را این‌جا بنویس…", true)
        script.minLines = 8
        root.addView(script)

        root.addView(sectionTitle("۳. سبک بصری"))
        root.addView(cardText("واقع‌گرایانه · سینمایی · مستند خبری\nبدون انیمه، کارتونی یا چهره‌سازی فانتزی"))

        root.addView(sectionTitle("۴. موتور محلی"))
        engineUrl = edit("آدرس موتور محلی؛ مثال: http://192.168.1.10:8188", false)
        engineUrl.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        root.addView(engineUrl)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val test = button("آزمایش اتصال") { testEngine() }
        val save = button("ذخیره پروژه") { saveProject() }
        row.addView(test, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(6) })
        row.addView(save, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(6) })
        root.addView(row)

        root.addView(sectionTitle("۵. تولید"))
        root.addView(button("تولید تصویر طبیعی", ::generateImage))
        root.addView(button("تولید ویدیوی طبیعی", ::generateVideo))
        root.addView(button("ساخت طرح مستند", ::buildDocumentaryPlan))

        status = label("آماده", 13, Color.rgb(215,181,109), true).apply {
            setPadding(dp(14), dp(18), dp(14), dp(8))
        }
        root.addView(status)

        root.addView(label("هستهٔ این نسخه به سرویس خاصی قفل نیست. موتور تولید می‌تواند روی کمپیوتر شخصی اجرا شود و بعداً تعویض گردد.", 12, Color.rgb(130,142,153), false).apply {
            setPadding(0, dp(14), 0, 0)
        })

        scroll.addView(root)
        return scroll
    }

    private fun sectionTitle(text: String) = label(text, 16, Color.rgb(215,181,109), true).apply {
        gravity = Gravity.START
        setPadding(0, dp(22), 0, dp(8))
    }

    private fun label(text: String, sp: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = sp.toFloat()
        setTextColor(color)
        gravity = Gravity.CENTER_HORIZONTAL
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun edit(hint: String, multiline: Boolean) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(Color.rgb(118,130,141))
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(20, 28, 36))
        setPadding(dp(14), dp(13), dp(14), dp(13))
        gravity = Gravity.TOP or Gravity.START
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, if (multiline) dp(190) else dp(56)).apply {
            bottomMargin = dp(10)
        }
    }

    private fun cardText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(20, 28, 36))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        gravity = Gravity.START
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.rgb(10, 15, 20))
        setBackgroundColor(Color.rgb(215,181,109))
        setOnClickListener { action() }
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply {
            bottomMargin = dp(10)
        }
    }

    private fun testEngine() {
        val base = engineUrl.text.toString().trim().trimEnd('/')
        if (base.isBlank()) return show("آدرس موتور محلی را وارد کن")
        show("در حال آزمایش اتصال…")
        io.execute {
            val ok = try {
                val conn = URL("$base/system_stats").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                conn.responseCode in 200..299
            } catch (_: Exception) { false }
            runOnUiThread { show(if (ok) "اتصال برقرار شد ✓" else "اتصال برقرار نشد") }
        }
    }

    private fun generateImage() = sendPrompt("image")
    private fun generateVideo() = sendPrompt("video")

    private fun sendPrompt(kind: String) {
        val base = engineUrl.text.toString().trim().trimEnd('/')
        val text = script.text.toString().trim()
        if (base.isBlank()) return show("نخست موتور محلی را مشخص کن")
        if (text.isBlank()) return show("متن یا توضیح صحنه را وارد کن")

        val realism = "photorealistic documentary footage, natural human faces, realistic lighting, authentic environment, cinematic documentary, no cartoon, no anime, no illustration"
        val payload = JSONObject().put("type", kind).put("prompt", "$text\n$realism")
        show(if (kind == "image") "درخواست تصویر طبیعی ارسال شد…" else "درخواست ویدیوی طبیعی ارسال شد…")

        io.execute {
            val result = try {
                val conn = URL("$base/docstudio/generate").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 30000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                BufferedReader(InputStreamReader(stream)).use { it.readText() }
            } catch (e: Exception) { "ERROR: ${e.message}" }
            runOnUiThread {
                show(if (result.startsWith("ERROR:")) "موتور پاسخ نداد" else "درخواست پذیرفته شد ✓")
            }
        }
    }

    private fun buildDocumentaryPlan() {
        val text = script.text.toString().trim()
        if (text.isBlank()) return show("متن مستند را وارد کن")
        val scenes = text.split(Regex("[.!؟\\n]+"))
            .map { it.trim() }
            .filter { it.length > 12 }
            .take(20)
        val plan = scenes.mapIndexed { i, s -> "صحنه ${i + 1}: $s" }.joinToString("\n\n")
        script.setText(plan.ifBlank { text })
        show("طرح صحنه‌بندی ساخته شد")
    }

    private fun saveProject() {
        getSharedPreferences("docstudio", MODE_PRIVATE).edit()
            .putString("title", projectTitle.text.toString())
            .putString("script", script.text.toString())
            .putString("engine", engineUrl.text.toString())
            .apply()
        show("پروژه روی دستگاه ذخیره شد ✓")
    }

    private fun restore() {
        val p = getSharedPreferences("docstudio", MODE_PRIVATE)
        projectTitle.setText(p.getString("title", ""))
        script.setText(p.getString("script", ""))
        engineUrl.setText(p.getString("engine", ""))
    }

    private fun show(text: String) { status.text = text }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
