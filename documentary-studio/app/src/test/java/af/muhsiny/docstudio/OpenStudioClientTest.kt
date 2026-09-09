package af.muhsiny.docstudio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenStudioClientTest {
    @Test
    fun localEndpointGuardAllowsPrivateLanAndRejectsPublicHttp() {
        assertEquals("http://192.168.1.8:8190", LocalEndpointGuard.normalize("192.168.1.8:8190/"))
        assertEquals("http://10.0.2.2:8190", LocalEndpointGuard.normalize("http://10.0.2.2:8190"))
        var rejected = false
        try { LocalEndpointGuard.normalize("http://8.8.8.8:8190") } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test
    fun capabilitiesOnlyReflectWhatWorkerReports() {
        val json = JSONObject()
            .put("director_ai", true)
            .put("director_model", "qwen-local")
            .put("image", true)
            .put("video", false)
            .put("transcribe", true)
            .put("upscale", false)
            .put("engines", JSONArray(listOf("ollama", "comfyui", "whisper.cpp")))
        val caps = OpenCapabilities.fromJson(json)
        assertTrue(caps.directorAi)
        assertTrue(caps.image)
        assertTrue(caps.transcribe)
        assertFalse(caps.video)
        assertFalse(caps.upscale)
        assertEquals("qwen-local", caps.directorModel)
        assertEquals(3, caps.engines.size)
    }
}
