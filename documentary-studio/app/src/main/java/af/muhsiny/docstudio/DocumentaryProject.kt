package af.muhsiny.docstudio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class DocumentaryProject(
    var id: String = UUID.randomUUID().toString(),
    var title: String = "مستند جدید",
    var script: String = "",
    var mediaUris: MutableList<String> = mutableListOf(),
    var musicUri: String? = null,
    var narrationUri: String? = null,
    var lastOutputUri: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("script", script)
        put("mediaUris", JSONArray(mediaUris))
        put("musicUri", musicUri ?: JSONObject.NULL)
        put("narrationUri", narrationUri ?: JSONObject.NULL)
        put("lastOutputUri", lastOutputUri ?: JSONObject.NULL)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): DocumentaryProject {
            val media = mutableListOf<String>()
            val a = o.optJSONArray("mediaUris") ?: JSONArray()
            for (i in 0 until a.length()) media += a.optString(i)
            return DocumentaryProject(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "مستند جدید"),
                script = o.optString("script", ""),
                mediaUris = media,
                musicUri = o.optNullableString("musicUri"),
                narrationUri = o.optNullableString("narrationUri"),
                lastOutputUri = o.optNullableString("lastOutputUri"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        private fun JSONObject.optNullableString(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key).takeIf { it.isNotBlank() }
        }
    }
}

class ProjectRepository(private val context: Context) {
    private val dir = File(context.filesDir, "documentary_projects").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("docstudio_projects", Context.MODE_PRIVATE)

    fun list(): List<DocumentaryProject> = dir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { runCatching { DocumentaryProject.fromJson(JSONObject(it.readText(Charsets.UTF_8))) }.getOrNull() }
        ?.sortedByDescending { it.updatedAt }
        ?: emptyList()

    fun create(title: String = "مستند جدید"): DocumentaryProject {
        val p = DocumentaryProject(title = title)
        save(p)
        setCurrent(p.id)
        return p
    }

    fun save(project: DocumentaryProject) {
        project.updatedAt = System.currentTimeMillis()
        val target = file(project.id)
        val temp = File(dir, "${project.id}.tmp")
        temp.writeText(project.toJson().toString(2), Charsets.UTF_8)
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            target.writeText(project.toJson().toString(2), Charsets.UTF_8)
            temp.delete()
        }
        setCurrent(project.id)
    }

    fun load(id: String): DocumentaryProject? {
        val f = file(id)
        if (!f.exists()) return null
        return runCatching { DocumentaryProject.fromJson(JSONObject(f.readText(Charsets.UTF_8))) }.getOrNull()
    }

    fun currentOrCreate(): DocumentaryProject {
        val id = prefs.getString("current_project", null)
        return id?.let(::load) ?: list().firstOrNull()?.also { setCurrent(it.id) } ?: create()
    }

    fun setCurrent(id: String) {
        prefs.edit().putString("current_project", id).apply()
    }

    fun delete(id: String) {
        file(id).delete()
        if (prefs.getString("current_project", null) == id) prefs.edit().remove("current_project").apply()
    }

    private fun file(id: String) = File(dir, "$id.json")
}
