package af.muhsiny.docstudio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SceneItem(
    var id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var mediaUri: String? = null,
    var sfxUri: String? = null,
    var cropMode: String = "crop",
    var clipStartMs: Long = 0L,
    var subtitle: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("mediaUri", mediaUri ?: JSONObject.NULL)
        put("sfxUri", sfxUri ?: JSONObject.NULL)
        put("cropMode", cropMode)
        put("clipStartMs", clipStartMs)
        put("subtitle", subtitle)
    }

    companion object {
        fun fromJson(o: JSONObject): SceneItem = SceneItem(
            id = o.optString("id", UUID.randomUUID().toString()),
            text = o.optString("text", ""),
            mediaUri = o.optNullableString("mediaUri"),
            sfxUri = o.optNullableString("sfxUri"),
            cropMode = o.optString("cropMode", "crop").takeIf { it == "fit" || it == "crop" } ?: "crop",
            clipStartMs = o.optLong("clipStartMs", 0L).coerceAtLeast(0L),
            subtitle = o.optBoolean("subtitle", true)
        )
    }
}

data class DocumentaryProject(
    var id: String = UUID.randomUUID().toString(),
    var title: String = "مستند جدید",
    var script: String = "",
    var scenes: MutableList<SceneItem> = mutableListOf(),
    var mediaUris: MutableList<String> = mutableListOf(),
    var musicUri: String? = null,
    var narrationUri: String? = null,
    var voiceMode: String = "pocket_default",
    var cloneReferenceUri: String? = null,
    var logoUri: String? = null,
    var aspectRatio: String = "16:9",
    var resolution: String = "1080p",
    var narrationVolume: Int = 100,
    var musicVolume: Int = 14,
    var sfxVolume: Int = 35,
    var subtitlesEnabled: Boolean = true,
    var subtitleSize: Int = 44,
    var lastOutputUri: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("script", script)
        put("scenes", JSONArray().apply { scenes.forEach { put(it.toJson()) } })
        put("mediaUris", JSONArray(mediaUris))
        put("musicUri", musicUri ?: JSONObject.NULL)
        put("narrationUri", narrationUri ?: JSONObject.NULL)
        put("voiceMode", voiceMode)
        put("cloneReferenceUri", cloneReferenceUri ?: JSONObject.NULL)
        put("logoUri", logoUri ?: JSONObject.NULL)
        put("aspectRatio", aspectRatio)
        put("resolution", resolution)
        put("narrationVolume", narrationVolume)
        put("musicVolume", musicVolume)
        put("sfxVolume", sfxVolume)
        put("subtitlesEnabled", subtitlesEnabled)
        put("subtitleSize", subtitleSize)
        put("lastOutputUri", lastOutputUri ?: JSONObject.NULL)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    fun ensureScenes() {
        if (scenes.isNotEmpty()) return
        val planned = ScenePlanner.scenes(script, 80)
        scenes = planned.mapIndexed { index, p ->
            SceneItem(
                text = p.text,
                mediaUri = mediaUris.getOrNull(index % mediaUris.size.coerceAtLeast(1))
            )
        }.toMutableList()
    }

    companion object {
        fun fromJson(o: JSONObject): DocumentaryProject {
            val media = mutableListOf<String>()
            val mediaArray = o.optJSONArray("mediaUris") ?: JSONArray()
            for (i in 0 until mediaArray.length()) media += mediaArray.optString(i)

            val sceneList = mutableListOf<SceneItem>()
            val sceneArray = o.optJSONArray("scenes") ?: JSONArray()
            for (i in 0 until sceneArray.length()) {
                sceneArray.optJSONObject(i)?.let { sceneList += SceneItem.fromJson(it) }
            }

            val legacyClone = o.optBoolean("useVoiceClone", false)
            val mode = o.optString("voiceMode", if (legacyClone) "pocket_clone" else "pocket_default")
            return DocumentaryProject(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "مستند جدید"),
                script = o.optString("script", ""),
                scenes = sceneList,
                mediaUris = media,
                musicUri = o.optNullableString("musicUri"),
                narrationUri = o.optNullableString("narrationUri"),
                voiceMode = mode,
                cloneReferenceUri = o.optNullableString("cloneReferenceUri"),
                logoUri = o.optNullableString("logoUri"),
                aspectRatio = o.optString("aspectRatio", "16:9").takeIf { it in setOf("16:9", "9:16", "1:1") } ?: "16:9",
                resolution = o.optString("resolution", "1080p").takeIf { it in setOf("720p", "1080p") } ?: "1080p",
                narrationVolume = o.optInt("narrationVolume", 100).coerceIn(0, 150),
                musicVolume = o.optInt("musicVolume", 14).coerceIn(0, 100),
                sfxVolume = o.optInt("sfxVolume", 35).coerceIn(0, 100),
                subtitlesEnabled = o.optBoolean("subtitlesEnabled", true),
                subtitleSize = o.optInt("subtitleSize", 44).coerceIn(24, 72),
                lastOutputUri = o.optNullableString("lastOutputUri"),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            ).also { it.ensureScenes() }
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
        save(p); setCurrent(p.id); return p
    }

    fun duplicate(source: DocumentaryProject): DocumentaryProject {
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            title = source.title + " - کپی",
            scenes = source.scenes.map { it.copy(id = UUID.randomUUID().toString()) }.toMutableList(),
            mediaUris = source.mediaUris.toMutableList(),
            lastOutputUri = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        save(copy)
        return copy
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

    fun setCurrent(id: String) { prefs.edit().putString("current_project", id).apply() }

    fun delete(id: String) {
        file(id).delete()
        if (prefs.getString("current_project", null) == id) prefs.edit().remove("current_project").apply()
    }

    private fun file(id: String) = File(dir, "$id.json")
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}
