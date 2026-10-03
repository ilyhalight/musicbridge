package app.toil.musicbridge.scrobbling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ScrobblingEndpointTest {
    @Test fun officialServerAcceptsBaseUrlAndVersionedUrl() {
        assertEquals(DEFAULT_SCROBBLING_ENDPOINT, normalizeScrobblingEndpoint("https://api.listenbrainz.org"))
        assertEquals(DEFAULT_SCROBBLING_ENDPOINT, normalizeScrobblingEndpoint(" https://API.LISTENBRAINZ.ORG:443/1/ "))
    }

    @Test fun malojaPreservesApiPrefixAndAddsVersionOnce() {
        val expected = "https://music.example/maloja/apis/listenbrainz/1"
        assertEquals(expected, normalizeScrobblingEndpoint("https://music.example/maloja/apis/listenbrainz"))
        assertEquals(expected, normalizeScrobblingEndpoint("$expected/"))
    }

    @Test fun httpRequiresExplicitPermission() {
        assertTrue(runCatching { normalizeScrobblingEndpoint("http://192.168.1.2:42010/apis/listenbrainz") }.isFailure)
        assertEquals("http://192.168.1.2:42010/apis/listenbrainz/1", normalizeScrobblingEndpoint("http://192.168.1.2:42010/apis/listenbrainz", true))
    }

    @Test fun ipv6HostsAndCustomPortsArePreserved() {
        assertEquals("http://[::1]:42010/apis/listenbrainz/1", normalizeScrobblingEndpoint("http://[::1]:42010/apis/listenbrainz", true))
    }

    @Test fun rejectsUnsafeOrIncompleteUrls() {
        listOf(
            "", "music.example", "//music.example", "ftp://music.example", "file:///data/test",
            "https://user:password@music.example", "https://music.example?token=secret", "https://music.example#section",
            "https://music.example:0", "https://music.example:65536", "https://music.example:bad",
            "https://music.example/a/../b", "https://music.example/%2e%2e/b", "https://music.example/a%2fb",
            "https://music.example/a%5cb", "https://music.example/1/submit-listens", "https://music.example/1/validate-token",
            "https://music.example\r\nX-Header: secret",
        ).forEach { value ->
            assertTrue(value, runCatching { normalizeScrobblingEndpoint(value, true) }.isFailure)
        }
    }

    @Test fun reconnectingSameServerAndAccountPreservesQueueIdentity() {
        assertTrue(sameScrobblingAccount(DEFAULT_SCROBBLING_ENDPOINT, "Listener", DEFAULT_SCROBBLING_ENDPOINT, "Listener"))
    }

    @Test fun differentServerDoesNotReuseAccountEvenWithSameName() {
        assertFalse(sameScrobblingAccount(DEFAULT_SCROBBLING_ENDPOINT, "Listener", "https://music.example/apis/listenbrainz/1", "Listener"))
        assertFalse(sameScrobblingAccount(DEFAULT_SCROBBLING_ENDPOINT, "Old", DEFAULT_SCROBBLING_ENDPOINT, "New"))
        assertFalse(sameScrobblingAccount(DEFAULT_SCROBBLING_ENDPOINT, null, DEFAULT_SCROBBLING_ENDPOINT, "Listener"))
    }

    @Test fun malojaNativePathKeepsHostPortAndProxyPrefix() {
        assertEquals("http://192.168.1.2:42010/maloja/apis/mlj_1", malojaNativeEndpoint("http://192.168.1.2:42010/maloja/apis/listenbrainz/1"))
        assertEquals("https://music.example/apis/mlj_1", malojaNativeEndpoint("https://music.example/apis/lbrnz/1"))
        assertNull(malojaNativeEndpoint(DEFAULT_SCROBBLING_ENDPOINT))
        assertNull(malojaNativeEndpoint("https://music.example/other/1"))
    }
}
