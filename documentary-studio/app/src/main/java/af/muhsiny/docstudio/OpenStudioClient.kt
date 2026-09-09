package af.muhsiny.docstudio

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


data class OpenCapabilities(
    val directorAi: Boolean,
    val image: Boolean,
    val video: Boolean,
    val transcribe: Boolean,
    val upscale: Boolean,
    val directorModel: String?,
    val engines: List<String>
) {
    companion object {
        fun fromJson(o: JSONObject): OpenCapabilities {
            val arr = o.optJSONArray("engines") ?: JSONArray()
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) arr.optString(i)?.takeIf { it.isNotBlank() }?.let(list::add)
            return OpenCapabilities(
                directorAi = o.optBoolean("director_ai", false),
                image = o.optBoolean("image", false),
                video = o.optBoolean("video", false),
                transcribe = o.optBoolean("transcribe", false),
                upscale = o.optBoolean("upscale", false),
                directorModel = o.optString("director_model").takeIf { it.isNotBlank() && it != "null" },
                engines = list
            )
        }
    }
}

object LocalEndpointGuard {
    fun normalize(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) throw IllegalArgumentException("نشانی موتور محلی خالی است")
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        val uri = URI(withScheme)
        val host = uri.host ?: throw IllegalArgumentException("نشانی موتور معتبر نیست")
        if (uri.scheme != "http" && uri.scheme != "https") throw IllegalArgumentException("فقط HTTP/HTTPS پشتیبانی می‌شود")
        if (uri.scheme == "http" && !isPrivateHost(host)) throw IllegalArgumentException("HTTP فقط برای شبکهٔ محلی مجاز است")
        return withScheme
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host.equals("localhost", true)) return true
        val addr = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        if (addr.isLoopbackAddress || addr.isSiteLocalAddress) return true
        if (addr is Inet4Address) {
            val b = addr.address.map { it.toInt() and 0xff }
            return b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168)
        }
        return false
    }
}

class OpenStudioClient(baseUrl: String, private val token: String) {
    private val base = LocalEndpointGuard.normalize(baseUrl)

    fun capabilities(): OpenCapabilities = OpenCapabilities.fromJson(requestJson("GET", "/v1/capabilities"))

    fun directorPlan(script: String): JSONObject {
        val root = requestJson("POST", "/v1/director/plan", JSONObject().put("script", script))
        root.optJSONObject("plan")?.let { return it }
        val raw = root.opt("plan")
        return if (raw is JSONObject) raw else JSONObject().put("raw", raw?.toString().orEmpty())
    }

    fun submitGeneration(kind: String, prompt: String, width: Int, height: Int, seconds: Int = 5): String {
        require(kind == "image" || kind == "video")
        val body = JSONObject()
            .put("prompt", prompt)
            .put("width", width)
            .put("height", height)
            .put("seconds", seconds)
        val o = requestJson("POST", "/v1/$kind/generate", body)
        return o.optString("job_id").takeIf { it.isNotBlank() } ?: error("موتور job_id برنگرداند")
    }

    fun waitForJob(jobId: String, timeoutMs: Long = 30 * 60 * 1000L, onStatus: (String) -> Unit = {}): JSONObject {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val job = requestJson("GET", "/v1/jobs/$jobId")
            val status = job.optString("status", "unknown")
            onStatus(status)
            when (status) {
                "done" -> return job
                "error" -> error(job.optString("error", "خطای تولید"))
            }
            Thread.sleep(1800)
        }
        error("زمان تولید بیش از حد طول کشید")
    }

    fun downloadFirstResult(context: Context, job: JSONObject, prefix: String): Uri {
        val files = job.optJSONObject("result")?.optJSONArray("files") ?: error("خروجی فایل ندارد")
        val item = files.optJSONObject(0) ?: error("خروجی فایل ندارد")
        val filename = item.optString("filename").takeIf { it.isNotBlank() } ?: error("نام فایل خروجی خالی است")
        val subfolder = item.optString("subfolder", "")
        val type = item.optString("type", "output")
        val query = "filename=${enc(filename)}&subfolder=${enc(subfolder)}&type=${enc(type)}"
        val connection = open("GET", "/v1/comfy/view?$query")
        val ext = filename.substringAfterLast('.', "bin").lowercase().take(8)
        val dir = File(context.filesDir, "open_assets").apply { mkdirs() }
        val target = File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
        try {
            ensureSuccess(connection)
            connection.inputStream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        } finally {
            connection.disconnect()
        }
        if (target.length() < 512L) error("فایل تولیدشده معتبر نیست")
        return Uri.fromFile(target)
    }

    fun transcribe(context: Context, uri: Uri): String {
        val suffix = runCatching {
            val path = uri.lastPathSegment.orEmpty()
            val ext = path.substringAfterLast('.', "media").lowercase().filter { it.isLetterOrDigit() }.take(8)
            if (ext.isBlank()) ".media" else ".$ext"
        }.getOrDefault(".media")
        val temp = File.createTempFile("docstudio_transcribe_", suffix, context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("فایل صوت/ویدیو باز نشد")
            val size = temp.length()
            if (size <= 0L) error("فایل صوت/ویدیو خالی است")
            if (size > 512L * 1024L * 1024L) error("فایل برای رونویسی بیش از ۵۱۲ مگابایت است")

            val c = open("POST", "/v1/transcribe", "application/octet-stream")
            c.readTimeout = 30 * 60 * 1000
            c.setRequestProperty("X-Filename", "upload$suffix")
            c.setFixedLengthStreamingMode(size)
            try {
                c.outputStream.use { output -> temp.inputStream().use { input -> input.copyTo(output) } }
                val json = readJsonResponse(c)
                return json.optString("text").trim().takeIf { it.isNotBlank() } ?: error("رونویسی خالی برگشت")
            } finally {
                c.disconnect()
            }
        } finally {
            temp.delete()
        }
    }

    private fun requestJson(method: String, path: String, body: JSONObject? = null): JSONObject {
        val c = open(method, path)
        try {
            if (body != null) c.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            return readJsonResponse(c)
        } finally {
            c.disconnect()
        }
    }

    private fun readJsonResponse(c: HttpURLConnection): JSONObject {
        val code = c.responseCode
        val bytes = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        val text = bytes.toString(StandardCharsets.UTF_8)
        val json = runCatching { JSONObject(text) }.getOrElse { JSONObject().put("error", text) }
        if (code !in 200..299) error(json.optString("error", "HTTP $code"))
        return json
    }

    private fun ensureSuccess(c: HttpURLConnection) {
        val code = c.responseCode
        if (code in 200..299) return
        val text = c.errorStream?.use { it.readBytes().toString(StandardCharsets.UTF_8) }.orEmpty()
        val message = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty().ifBlank { "HTTP $code" }
        error(message)
    }

    private fun open(method: String, path: String, contentType: String = "application/json; charset=utf-8"): HttpURLConnection {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 6_000
        c.readTimeout = 120_000
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", "application/json")
        if (method == "POST") {
            c.doOutput = true
            c.setRequestProperty("Content-Type", contentType)
        }
        return c
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
