package com.aethershield.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientOsTest {

    @Test
    fun androidIsCheckedBeforeLinux() {
        // Android Chrome user agents also say "Linux"; the order is what makes this right.
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"

        assertEquals(ClientOs.ANDROID, ClientOs.fromUserAgent(ua))
    }

    @Test
    fun iphoneAndIpadAreIos() {
        val iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15"
        val ipad = "Mozilla/5.0 (iPad; CPU OS 16_6 like Mac OS X) AppleWebKit/605.1.15"

        assertEquals(ClientOs.IOS, ClientOs.fromUserAgent(iphone))
        assertEquals(ClientOs.IOS, ClientOs.fromUserAgent(ipad))
    }

    @Test
    fun desktopOperatingSystemsAreRecognised() {
        assertEquals(
            ClientOs.WINDOWS,
            ClientOs.fromUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/121"),
        )
        assertEquals(
            ClientOs.MACOS,
            ClientOs.fromUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605"),
        )
        assertEquals(
            ClientOs.LINUX,
            ClientOs.fromUserAgent("Mozilla/5.0 (X11; Linux x86_64) Firefox/122"),
        )
    }

    @Test
    fun aMissingOrForeignAgentIsUnknown() {
        assertNull(null)
        assertEquals(ClientOs.UNKNOWN, ClientOs.fromUserAgent(null))
        assertEquals(ClientOs.UNKNOWN, ClientOs.fromUserAgent(""))
        assertEquals(ClientOs.UNKNOWN, ClientOs.fromUserAgent("curl/8.4.0"))
    }
}

class HttpProxyRequestTest {

    private fun head(vararg lines: String) = lines.joinToString("\r\n") + "\r\n"

    @Test
    fun connectCarriesTheHostAndPort() {
        val parsed = HttpProxyRequest.parse(head("CONNECT example.com:8443 HTTP/1.1", "Host: example.com"))

        requireNotNull(parsed)
        assertEquals("CONNECT", parsed.method)
        assertEquals("example.com", parsed.host)
        assertEquals(8443, parsed.port)
        assertNull("CONNECT is tunnelled raw", parsed.originPath)
    }

    @Test
    fun anAbsoluteUrlIsRewrittenToOriginForm() {
        val parsed = HttpProxyRequest.parse(
            head("GET http://example.com/a/b?c=1 HTTP/1.1", "Host: example.com")
        )

        requireNotNull(parsed)
        assertEquals("GET", parsed.method)
        assertEquals("example.com", parsed.host)
        assertEquals(80, parsed.port)
        assertEquals("/a/b?c=1", parsed.originPath)
    }

    @Test
    fun anAbsoluteUrlWithAPortIsUnderstood() {
        val parsed = HttpProxyRequest.parse(head("GET http://example.com:8080/x HTTP/1.1"))

        requireNotNull(parsed)
        assertEquals(8080, parsed.port)
        assertEquals("/x", parsed.originPath)
    }

    @Test
    fun theUserAgentIsCapturedForTheDeviceList() {
        val parsed = HttpProxyRequest.parse(
            head(
                "CONNECT example.com:443 HTTP/1.1",
                "Host: example.com",
                "User-Agent: Mozilla/5.0 (Linux; Android 13) Chrome/119",
            )
        )

        requireNotNull(parsed)
        assertEquals("Mozilla/5.0 (Linux; Android 13) Chrome/119", parsed.userAgent)
        assertEquals(ClientOs.ANDROID, ClientOs.fromUserAgent(parsed.userAgent))
    }

    @Test
    fun httpsUrlsAreNotRewriteableSoTheyAreRejected() {
        // Browsers only send HTTPS through CONNECT; anything else is not our business.
        assertNull(HttpProxyRequest.parse(head("GET https://example.com/ HTTP/1.1")))
    }

    @Test
    fun nonsenseIsRejected() {
        assertNull(HttpProxyRequest.parse(""))
        assertNull(HttpProxyRequest.parse("garbage"))
        assertNull(HttpProxyRequest.parse(head("CONNECT")))
    }
}
