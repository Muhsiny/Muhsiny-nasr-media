package af.muhsiny.docstudio

data class PlannedScene(
    val index: Int,
    val text: String,
    val weight: Int
)

object ScenePlanner {
    fun scenes(text: String, maxScenes: Int = 120): List<PlannedScene> {
        val raw = text
            .replace("\r", "")
            .split(Regex("(?<=[.!؟؛])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val merged = mutableListOf<String>()
        var carry = ""
        for (part in raw) {
            val candidate = if (carry.isBlank()) part else "$carry $part"
            if (candidate.length < 55) carry = candidate
            else { merged += candidate; carry = "" }
        }
        if (carry.isNotBlank()) merged += carry

        return merged.take(maxScenes).mapIndexed { index, s ->
            PlannedScene(index + 1, s, s.count { !it.isWhitespace() }.coerceAtLeast(1))
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

    fun buildSrt(text: String, totalDurationMs: Long): String {
        val scenes = scenes(text)
        if (scenes.isEmpty() || totalDurationMs <= 0) return ""
        val totalWeight = scenes.sumOf { it.weight }.coerceAtLeast(1)
        var cursor = 0L
        return buildString {
            scenes.forEachIndexed { i, scene ->
                val proportional = totalDurationMs * scene.weight / totalWeight
                val duration = if (i == scenes.lastIndex) totalDurationMs - cursor else proportional.coerceAtLeast(900L)
                val end = (cursor + duration).coerceAtMost(totalDurationMs)
                append(i + 1).append('\n')
                append(formatSrt(cursor)).append(" --> ").append(formatSrt(end)).append('\n')
                append(scene.text).append("\n\n")
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
