package af.muhsiny.docstudio

import org.junit.Assert.assertEquals
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
    fun localEndpointGuardAllowsHttpsButRejectsBlankAndInvalidScheme() {
        assertEquals("https://example.org:8190", LocalEndpointGuard.normalize("https://example.org:8190/"))

        var blankRejected = false
        try { LocalEndpointGuard.normalize("   ") } catch (_: IllegalArgumentException) { blankRejected = true }
        assertTrue(blankRejected)

        var schemeRejected = false
        try { LocalEndpointGuard.normalize("ftp://192.168.1.8:8190") } catch (_: IllegalArgumentException) { schemeRejected = true }
        assertTrue(schemeRejected)
    }
}
