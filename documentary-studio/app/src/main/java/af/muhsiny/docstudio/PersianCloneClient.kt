package af.muhsiny.docstudio

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class PersianCloneClient(private val context: Context) {
    data class Profile(val id: String, val name: String)
    data class AudioResult(val uri: Uri, val chunks: Int)

    fun health(baseUrl: String, onDone: (String) -> Unit, onError: (String) -> Unit) = background {
        runCatching {
            val json = getJson(url(baseUrl, "/health"))
            if (!json.optBoolean("ok")) error(json.optString("error", "engine unavailable"))
            val engine = json.optString("engine", "Persian clone")
            val loaded = if (json.optBoolean("model_loaded")) "مدل بار شده" else "مدل در اولین تولید بار می‌شود"
            "$engine · $loaded"
        }.onSuccess(onDone).onFailure { onError(it.message ?: "اتصال به موتور کلون ناموفق بود") }
    }

    fun registerVoice(
        baseUrl: String,
        source: Uri,
        name: String,
        onDone: (Profile) -> Unit,
        onError: (String) -> Unit
    ) = background {
        runCatching {
            val bytes = context.contentResolver.openInputStream(source)?.use { input ->
                val buffer = ByteArray(8192)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    total += n
                    if (total > 12 * 1024 * 1024) error("نمونهٔ صدا بیش از ۱۲ مگابایت است")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: error("فایل نمونهٔ صدا خوانده نشد")
            val ext = displayExtension(source)
            val payload = JSONObject().apply {
                put("name", name.ifBlank { "صدای فارسی" })
                put("extension", ext)
                put("audio_base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
            val json = postJson(url(baseUrl, "/voice/register"), payload)
            if (!json.optBoolean("ok")) error(json.optString("error", "ثبت صدا ناموفق بود"))
            val p = json.getJSONObject("profile")
            Profile(p.getString("id"), p.optString("name", name))
        }.onSuccess(onDone).onFailure { onError(it.message ?: "ساخت پروفایل کلون ناموفق بود") }
    }

    fun synthesize(
        baseUrl: String,
        profileId: String,
        text: String,
        title: String,
        onDone: (AudioResult) -> Unit,
        onError: (String) -> Unit
    ) = background {
        runCatching {
            val payload = JSONObject().apply {
                put("profile_id", profileId)
                put("text", text)
                put("title", title)
            }
            val json = postJson(url(baseUrl, "/voice/synthesize"), payload, 20 * 60 * 1000)
            if (!json.optBoolean("ok")) error(json.optString("error", "تولید صدای کلون ناموفق بود"))
            val remote = json.getString("url")
            val outDir = File(context.filesDir, "cloned_narration").apply { mkdirs() }
            val file = File(outDir, "clone_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.wav")
            download(url(baseUrl, remote), file, 20 * 60 * 1000)
            if (!file.exists() || file.length() < 4096L) error("فایل صدای کلون ناقص است")
            AudioResult(Uri.fromFile(file), json.optInt("chunks", 1).coerceAtLeast(1))
        }.onSuccess(onDone).onFailure { onError(it.message ?: "تولید صدای کلون ناموفق بود") }
    }

    private fun background(block: () -> Unit) {
        Thread(block, "DocStudio-PersianClone").start()
    }

    private fun url(base: String, path: String): String {
        val clean = base.trim().trimEnd('/')
        require(clean.startsWith("http://") || clean.startsWith("https://")) { "آدرس موتور باید با http:// یا https:// شروع شود" }
        return clean + if (path.startsWith('/')) path else "/$path"
    }

    private fun getJson(rawUrl: String): JSONObject {
        val c = URL(rawUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 7000
        c.readTimeout = 15000
        c.requestMethod = "GET"
        return readJson(c)
    }

    private fun postJson(rawUrl: String, body: JSONObject, timeout: Int = 120000): JSONObject {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        val c = URL(rawUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = timeout
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.setFixedLengthStreamingMode(bytes.size)
        c.outputStream.use { it.write(bytes) }
        return readJson(c)
    }

    private fun readJson(c: HttpURLConnection): JSONObject {
        return try {
            val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (text.isBlank()) error("پاسخ خالی از موتور دریافت شد (${c.responseCode})")
            val json = JSONObject(text)
            if (c.responseCode !in 200..299) error(json.optString("error", "HTTP ${c.responseCode}"))
            json
        } finally {
            c.disconnect()
        }
    }

    private fun download(rawUrl: String, target: File, timeout: Int) {
        val c = URL(rawUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = timeout
        try {
            if (c.responseCode !in 200..299) error("دانلود صدای کلون ناموفق بود: HTTP ${c.responseCode}")
            c.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        } finally {
            c.disconnect()
        }
    }

    private fun displayExtension(uri: Uri): String {
        val name = uri.lastPathSegment.orEmpty().lowercase()
        return name.substringAfterLast('.', "wav").takeIf { it in setOf("wav", "mp3", "m4a", "aac", "ogg", "flac") } ?: "wav"
    }
}
