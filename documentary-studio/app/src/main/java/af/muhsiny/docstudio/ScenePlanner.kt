package af.muhsiny.docstudio

data class PlannedScene(
    val index: Int,
    val text: String,
    val weight: Int
)

data class ProjectCheck(val ok: Boolean, val message: String)

object ScenePlanner {
    fun scenes(text: String, maxScenes: Int = 120): List<PlannedScene> {
        val raw = text
            .replace("\r", "")
            .split(Regex("(?<=[.!؟؛])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Keep meaningful sentences as independent timeline scenes. Only attach
        // genuinely tiny fragments (headings, short tails) to a neighbor.
        val merged = mutableListOf<String>()
        var carry = ""
        for (part in raw) {
            if (part.length < 24) {
                carry = if (carry.isBlank()) part else "$carry $part"
            } else if (carry.isNotBlank()) {
                merged += "$carry $part".trim()
                carry = ""
            } else {
                merged += part
            }
        }
        if (carry.isNotBlank()) {
            if (merged.isNotEmpty()) merged[merged.lastIndex] = "${merged.last()} $carry".trim()
            else merged += carry
        }

        return merged.take(maxScenes).mapIndexed { index, s ->
            PlannedScene(index + 1, s, s.count { !it.isWhitespace() }.coerceAtLeast(1))
        }
    }

    fun rebuildProjectScenes(project: DocumentaryProject, maxScenes: Int = 80) {
        val old = project.scenes.toList()
        val planned = scenes(project.script, maxScenes)
        project.scenes = planned.mapIndexed { index, p ->
            val previous = old.getOrNull(index)
            SceneItem(
                id = previous?.id ?: java.util.UUID.randomUUID().toString(),
                text = p.text,
                mediaUri = previous?.mediaUri ?: project.mediaUris.getOrNull(index % project.mediaUris.size.coerceAtLeast(1)),
                sfxUri = previous?.sfxUri,
                cropMode = previous?.cropMode ?: "crop",
                clipStartMs = previous?.clipStartMs ?: 0L,
                subtitle = previous?.subtitle ?: true
            )
        }.toMutableList()
    }

    fun autoAssignMedia(project: DocumentaryProject) {
        if (project.mediaUris.isEmpty()) return
        project.scenes.forEachIndexed { index, scene ->
            scene.mediaUri = project.mediaUris[index % project.mediaUris.size]
        }
    }

    fun pocketTtsChunks(text: String, maxWords: Int = 24, maxChars: Int = 180): List<String> {
        require(maxWords in 8..40)
        require(maxChars in 80..260)
        val normalized = text.replace("\r", "").trim()
        if (normalized.isBlank()) return emptyList()
        val clauses = normalized
            .split(Regex("(?<=[.!؟؛،,:])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val result = mutableListOf<String>()
        for (clause in clauses) {
            val words = clause.split(Regex("\\s+")).filter { it.isNotBlank() }
            var current = mutableListOf<String>()
            var chars = 0
            for (word in words) {
                val added = word.length + if (current.isEmpty()) 0 else 1
                if (current.isNotEmpty() && (current.size >= maxWords || chars + added > maxChars)) {
                    result += current.joinToString(" ")
                    current = mutableListOf(); chars = 0
                }
                current += word
                chars += word.length + if (current.size == 1) 0 else 1
            }
            if (current.isNotEmpty()) result += current.joinToString(" ")
        }
        return result.filter { it.isNotBlank() }
    }

    fun ttsChunks(text: String, maxChars: Int = 3000): List<String> {
        require(maxChars in 500..3900)
        val scenes = scenes(text, Int.MAX_VALUE).map { it.text }
        if (scenes.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            val value = current.toString().trim()
            if (value.isNotBlank()) chunks += value
            current.clear()
        }

        for (scene in scenes) {
            if (scene.length > maxChars) {
                flush()
                var start = 0
                while (start < scene.length) {
                    var end = (start + maxChars).coerceAtMost(scene.length)
                    if (end < scene.length) {
                        val safe = scene.lastIndexOf(' ', end)
                        if (safe > start + maxChars / 2) end = safe
                    }
                    chunks += scene.substring(start, end).trim()
                    start = end
                    while (start < scene.length && scene[start].isWhitespace()) start++
                }
                continue
            }

            val extra = if (current.isEmpty()) scene.length else scene.length + 1
            if (current.length + extra > maxChars) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(scene)
        }
        flush()
        return chunks
    }

    fun allocateSceneDurations(sceneTexts: List<String>, totalDurationMs: Long): List<Long> {
        if (sceneTexts.isEmpty() || totalDurationMs <= 0L) return emptyList()
        if (sceneTexts.size == 1) return listOf(totalDurationMs)
        val count = sceneTexts.size
        if (totalDurationMs < count) {
            return List(count) { index -> if (index < totalDurationMs.toInt()) 1L else 0L }
        }
        val weights = sceneTexts.map { it.count { c -> !c.isWhitespace() }.coerceAtLeast(1).toLong() }
        val totalWeight = weights.sum().coerceAtLeast(1L)
        val out = LongArray(count) { index -> (totalDurationMs * weights[index] / totalWeight).coerceAtLeast(1L) }
        var delta = totalDurationMs - out.sum()
        var cursor = 0
        while (delta != 0L) {
            val i = cursor % count
            if (delta > 0L) {
                out[i] += 1L
                delta--
            } else if (out[i] > 1L) {
                out[i] -= 1L
                delta++
            }
            cursor++
            if (cursor > count * 10_000 && delta != 0L) break
        }
        return out.toList()
    }

    fun estimateDurationMs(text: String, wordsPerMinute: Int = 125): Long {
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        if (words == 0) return 0L
        return (words * 60_000L / wordsPerMinute.coerceIn(80, 220)).coerceAtLeast(1000L)
    }

    fun validate(project: DocumentaryProject): List<ProjectCheck> {
        val checks = mutableListOf<ProjectCheck>()
        checks += ProjectCheck(project.title.isNotBlank(), if (project.title.isNotBlank()) "عنوان پروژه آماده است" else "عنوان پروژه خالی است")
        checks += ProjectCheck(project.script.isNotBlank(), if (project.script.isNotBlank()) "سناریو موجود است" else "سناریو خالی است")
        checks += ProjectCheck(project.scenes.isNotEmpty(), if (project.scenes.isNotEmpty()) "${project.scenes.size} صحنه آماده است" else "هیچ صحنه‌ای ساخته نشده")
        val missingMedia = project.scenes.count { it.mediaUri.isNullOrBlank() }
        checks += ProjectCheck(missingMedia == 0, if (missingMedia == 0) "برای همهٔ صحنه‌ها رسانه تعیین شده" else "$missingMedia صحنه رسانه ندارد")
        val emptyScenes = project.scenes.count { it.text.isBlank() }
        checks += ProjectCheck(emptyScenes == 0, if (emptyScenes == 0) "متن همهٔ صحنه‌ها معتبر است" else "$emptyScenes صحنه متن ندارد")
        if (project.voiceMode == "pocket_clone") checks += ProjectCheck(!project.cloneReferenceUri.isNullOrBlank(), if (!project.cloneReferenceUri.isNullOrBlank()) "نمونهٔ کلون انتخاب شده" else "نمونهٔ WAV کلون انتخاب نشده")
        if (project.voiceMode == "external_audio") checks += ProjectCheck(!project.narrationUri.isNullOrBlank(), if (!project.narrationUri.isNullOrBlank()) "نریشن آماده انتخاب شده" else "نریشن MP3/WAV انتخاب نشده")
        checks += ProjectCheck(project.musicVolume <= 35, if (project.musicVolume <= 35) "ولوم موسیقی مناسب نریشن است" else "موسیقی احتمالاً روی نریشن غالب می‌شود")
        return checks
    }

    fun buildSrt(text: String, totalDurationMs: Long): String {
        val scenes = scenes(text)
        return buildSrtFromScenes(scenes.map { it.text }, allocateSceneDurations(scenes.map { it.text }, totalDurationMs))
    }

    fun buildSrtFromScenes(sceneTexts: List<String>, durationsMs: List<Long>): String {
        if (sceneTexts.isEmpty() || durationsMs.size != sceneTexts.size) return ""
        var cursor = 0L
        return buildString {
            sceneTexts.forEachIndexed { i, scene ->
                val end = cursor + durationsMs[i].coerceAtLeast(1L)
                append(i + 1).append('\n')
                append(formatSrt(cursor)).append(" --> ").append(formatSrt(end)).append('\n')
                append(scene.trim()).append("\n\n")
                cursor = end
            }
        }
    }

    private fun formatSrt(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        val x = ms % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, x)
    }
}
