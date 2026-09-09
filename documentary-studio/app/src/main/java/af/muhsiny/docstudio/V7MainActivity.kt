package af.muhsiny.docstudio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class V7MainActivity : Activity() {
    private lateinit var repo: ProjectRepository
    private lateinit var project: DocumentaryProject
    private lateinit var serverInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var capabilityText: TextView
    private lateinit var planText: TextView
    private lateinit var statusText: TextView
    private lateinit var directorButton: Button
    private lateinit var applyPlanButton: Button
    private lateinit var imageButton: Button
    private lateinit var videoButton: Button
    private var capabilities: OpenCapabilities? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("open_studio_v7", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 10, 14)
        window.navigationBarColor = Color.rgb(7, 10, 14)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        repo = ProjectRepository(this)
        project = repo.currentOrCreate().also { it.ensureScenes() }
        setContentView(buildUi())
        refreshPlanPreview()
    }

    override fun onResume() {
        super.onResume()
        if (!::repo.isInitialized) return
        val previousId = if (::project.isInitialized) project.id else null
        project = repo.currentOrCreate().also { it.ensureScenes() }
        if (::planText.isInitialized) {
            refreshPlanPreview()
            applyPlanButton.enable(loadPlan() != null)
            if (previousId != null && previousId != project.id) {
                setStatus("پروژهٔ فعال به‌روز شد: ${project.title}")
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(20))
            setBackgroundColor(Color.rgb(7, 10, 14))
        }
        root.addView(text("استدیوی مستند V7 · OPEN AI", 25, true).apply { gravity = Gravity.CENTER })
        root.addView(text("بدون کریډت اجباری · موتورهای آزاد · تدوین و نریشن آفلاین", 12, false, Color.LTGRAY).apply { gravity = Gravity.CENTER })
        root.addView(space(10))
        root.addView(text("پروژهٔ فعال: ${project.title}", 15, true))
        root.addView(button("بازکردن استدیوی تدوین V6") { startActivity(Intent(this, MainActivity::class.java)) })

        root.addView(section("موتور آزاد محلی"))
        root.addView(text("کارهای سنگین روی سیستم شخصی/مشترک اجرا می‌شوند. اپ فقط قابلیت‌هایی را فعال می‌کند که واقعاً روی آن سیستم آماده باشند.", 12, false, Color.LTGRAY))
        serverInput = edit("مثال: http://192.168.1.10:8190").apply { setText(prefs.getString("server", "")) }
        tokenInput = edit("Token موتور").apply {
            setText(prefs.getString("token", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(serverInput)
        root.addView(tokenInput)
        root.addView(button("بررسی و شناسایی قابلیت‌های واقعی") { checkCapabilities() })
        capabilityText = text("هنوز بررسی نشده", 12, false, Color.rgb(231, 196, 113))
        root.addView(capabilityText)

        root.addView(section("کارگردان هوشمند محلی"))
        root.addView(text("سناریوی پروژه را به پلان صحنه‌ای تبدیل می‌کند؛ برای ادعاهای تاریخی، صحنهٔ آرشیوی را از بازسازی مولد جدا نگه می‌دارد.", 12, false, Color.LTGRAY))
        directorButton = button("ساخت پلان هوشمند از سناریو") { createDirectorPlan() }.apply { isEnabled = false; alpha = .45f }
        applyPlanButton = button("اعمال پلان به تایم‌لاین") { applyPlanToTimeline() }.apply { isEnabled = loadPlan() != null; alpha = if (isEnabled) 1f else .45f }
        root.addView(directorButton)
        root.addView(applyPlanButton)
        planText = text("پلان هنوز ساخته نشده", 11, false, Color.WHITE).apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
        root.addView(planText)

        root.addView(section("تولید رسانهٔ آزاد"))
        root.addView(text("فقط صحنه‌هایی که AI Director نوع آن‌ها را «generated» تعیین کرده، خودکار تولید می‌شوند؛ صحنه‌های تاریخیِ archive با تصویر جعلی جایگزین نمی‌شوند.", 12, false, Color.LTGRAY))
        imageButton = button("ساخت تصاویر مولد صحنه‌ها") { generateScenes("image") }.apply { isEnabled = false; alpha = .45f }
        videoButton = button("ساخت ویدیوهای مولد صحنه‌ها") { generateScenes("video") }.apply { isEnabled = false; alpha = .45f }
        root.addView(imageButton)
        root.addView(videoButton)

        root.addView(section("وضعیت"))
        statusText = text("آماده", 12, true, Color.rgb(231, 196, 113))
        root.addView(statusText)

        val scroll = ScrollView(this)
        scroll.addView(root)
        return scroll
    }

    private fun checkCapabilities() {
        saveConnection()
        setStatus("در حال بررسی موتور آزاد…")
        executor.execute {
            runCatching { client().capabilities() }
                .onSuccess { caps ->
                    capabilities = caps
                    runOnUiThread {
                        capabilityText.text = buildString {
                            append("موتورها: ").append(if (caps.engines.isEmpty()) "هیچ‌کدام" else caps.engines.joinToString("، "))
                            append("\nکارگردان AI: ").append(if (caps.directorAi) "✓ ${caps.directorModel.orEmpty()}" else "✗")
                            append(" · تصویر: ").append(if (caps.image) "✓" else "✗")
                            append(" · ویدیو: ").append(if (caps.video) "✓" else "✗")
                            append(" · Whisper: ").append(if (caps.transcribe) "✓" else "✗")
                            append(" · Upscale: ").append(if (caps.upscale) "✓" else "✗")
                        }
                        directorButton.enable(caps.directorAi)
                        imageButton.enable(caps.image)
                        videoButton.enable(caps.video)
                        setStatus("شناسایی قابلیت‌ها تمام شد")
                    }
                }
                .onFailure { e -> runOnUiThread { capabilityText.text = "اتصال ناموفق: ${e.message}"; setStatus("موتور آزاد در دسترس نیست") } }
        }
    }

    private fun createDirectorPlan() {
        project = repo.currentOrCreate().also { it.ensureScenes() }
        val script = project.script.trim()
        if (script.length < 20) return setStatus("سناریوی پروژه برای کارگردانی کافی نیست")
        saveConnection()
        directorButton.enable(false)
        setStatus("AI Director در حال تحلیل سناریو…")
        executor.execute {
            runCatching { client().directorPlan(script) }
                .onSuccess { plan ->
                    savePlan(plan)
                    runOnUiThread {
                        refreshPlanPreview()
                        applyPlanButton.enable(true)
                        directorButton.enable(capabilities?.directorAi == true)
                        setStatus("پلان هوشمند ذخیره شد ✓")
                    }
                }
                .onFailure { e -> runOnUiThread { directorButton.enable(capabilities?.directorAi == true); setStatus("خطای کارگردان: ${e.message}") } }
        }
    }

    private fun applyPlanToTimeline() {
        project = repo.currentOrCreate().also { it.ensureScenes() }
        val plan = loadPlan() ?: return setStatus("پلان ذخیره‌شده وجود ندارد")
        val scenes = plan.optJSONArray("scenes") ?: return setStatus("پلان صحنه ندارد")
        if (scenes.length() == 0) return setStatus("پلان صحنه ندارد")
        val previous = project.scenes.toList()
        val next = mutableListOf<SceneItem>()
        for (i in 0 until scenes.length()) {
            val s = scenes.optJSONObject(i) ?: continue
            val narration = s.optString("narration").trim()
            if (narration.isBlank()) continue
            next += SceneItem(
                text = narration,
                mediaUri = previous.getOrNull(i)?.mediaUri,
                sfxUri = previous.getOrNull(i)?.sfxUri,
                cropMode = previous.getOrNull(i)?.cropMode ?: "crop",
                subtitle = true
            )
        }
        if (next.isEmpty()) return setStatus("هیچ صحنهٔ معتبر در پلان نبود")
        project.scenes = next
        project.script = next.joinToString("\n") { it.text }
        repo.save(project)
        setStatus("${next.size} صحنه وارد تایم‌لاین شد ✓")
    }

    private fun generateScenes(kind: String) {
        val caps = capabilities ?: return setStatus("اول موتور را بررسی کن")
        if (kind == "image" && !caps.image) return setStatus("موتور تصویر آماده نیست")
        if (kind == "video" && !caps.video) return setStatus("موتور ویدیو آماده نیست")
        project = repo.currentOrCreate().also { it.ensureScenes() }
        val plan = loadPlan() ?: return setStatus("اول پلان هوشمند بساز")
        val planned = plan.optJSONArray("scenes") ?: return setStatus("پلان صحنه ندارد")
        val dims = generationDimensions(project.aspectRatio)
        imageButton.enable(false); videoButton.enable(false)
        setStatus("شروع تولید رسانه…")
        executor.execute {
            var made = 0
            var skipped = 0
            try {
                val c = client()
                val count = minOf(planned.length(), project.scenes.size)
                for (i in 0 until count) {
                    if (Thread.currentThread().isInterrupted) break
                    val meta = planned.optJSONObject(i) ?: continue
                    val sourceType = meta.optString("source_type", "generated").lowercase()
                    if (sourceType != "generated") { skipped++; continue }
                    val prompt = meta.optString("visual_prompt").trim().ifBlank { meta.optString("narration").trim() }
                    if (prompt.isBlank()) { skipped++; continue }
                    runOnUiThread { setStatus("${if (kind == "video") "ویدیو" else "تصویر"} صحنه ${i + 1}…") }
                    val jobId = c.submitGeneration(kind, prompt, dims.first, dims.second, seconds = 5)
                    val job = c.waitForJob(jobId) { state -> runOnUiThread { setStatus("صحنه ${i + 1}: $state") } }
                    val uri = c.downloadFirstResult(this, job, "AI_${kind}_scene_${i + 1}")
                    val raw = uri.toString()
                    project.scenes[i].mediaUri = raw
                    if (!project.mediaUris.contains(raw)) project.mediaUris.add(raw)
                    repo.save(project)
                    made++
                }
                runOnUiThread { setStatus("تولید تمام شد: $made رسانه ساخته شد، $skipped صحنهٔ غیرمولد دست‌نخورده ماند") }
            } catch (e: Exception) {
                runOnUiThread { setStatus("تولید متوقف شد: ${e.message}") }
            } finally {
                runOnUiThread {
                    imageButton.enable(capabilities?.image == true)
                    videoButton.enable(capabilities?.video == true)
                }
            }
        }
    }

    private fun generationDimensions(ratio: String): Pair<Int, Int> = when (ratio) {
        "9:16" -> 720 to 1280
        "1:1" -> 1024 to 1024
        else -> 1280 to 720
    }

    private fun refreshPlanPreview() {
        val plan = loadPlan()
        planText.text = if (plan == null) "پلان هنوز ساخته نشده" else prettyPlan(plan)
    }

    private fun prettyPlan(plan: JSONObject): String {
        val scenes = plan.optJSONArray("scenes") ?: return plan.toString(2)
        val out = StringBuilder("صحنه‌ها: ${scenes.length()}\n")
        for (i in 0 until minOf(scenes.length(), 20)) {
            val s = scenes.optJSONObject(i) ?: continue
            out.append("\n").append(i + 1).append(". ")
                .append(s.optString("source_type", "?"))
                .append(" · ").append(s.optString("narration").take(100))
            val vp = s.optString("visual_prompt").trim()
            if (vp.isNotBlank()) out.append("\n   ↳ ").append(vp.take(160))
        }
        return out.toString()
    }

    private fun savePlan(plan: JSONObject) {
        val dir = File(filesDir, "open_plans").apply { mkdirs() }
        File(dir, "${project.id}.json").writeText(plan.toString(2), Charsets.UTF_8)
    }

    private fun loadPlan(): JSONObject? {
        val f = File(File(filesDir, "open_plans"), "${project.id}.json")
        return if (f.exists()) runCatching { JSONObject(f.readText(Charsets.UTF_8)) }.getOrNull() else null
    }

    private fun client(): OpenStudioClient = OpenStudioClient(serverInput.text.toString(), tokenInput.text.toString())

    private fun saveConnection() {
        prefs.edit().putString("server", serverInput.text.toString().trim()).putString("token", tokenInput.text.toString()).apply()
    }

    private fun setStatus(message: String) { statusText.text = message }

    private fun section(title: String): TextView = text(title, 17, true, Color.rgb(238, 203, 117)).apply { setPadding(0, dp(18), 0, dp(6)) }
    private fun text(value: String, size: Int, bold: Boolean, color: Int = Color.WHITE): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private fun edit(hintValue: String): EditText = EditText(this).apply {
        hint = hintValue; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); setSingleLine(true); setPadding(dp(10), dp(8), dp(10), dp(8))
    }
    private fun button(title: String, action: () -> Unit): Button = Button(this).apply { text = title; setOnClickListener { action() }; minHeight = dp(52) }
    private fun Button.enable(value: Boolean) { isEnabled = value; alpha = if (value) 1f else .45f }
    private fun space(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
