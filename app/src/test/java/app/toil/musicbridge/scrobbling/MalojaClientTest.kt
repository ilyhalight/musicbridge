package app.toil.musicbridge.scrobbling

import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MalojaClientTest {
    private val endpoint = "https://music.example/maloja/apis/listenbrainz/1"
    private val listen = ScrobbleListen("id", "account", "player", ScrobbleTrack("Трек", "Автор 1, Автор 2,Автор 3", "Album", durationMs = 120_000), 1_790_000_000, 30_000, splitArtists = true)

    @Test fun commaSeparatedArtistsAreTrimmedDeduplicatedAndNotEmpty() {
        assertEquals(listOf("Автор 1", "Автор 2"), MalojaClient.artists(" Автор 1, ,Автор 2, Автор 1, "))
    }

    @Test fun otherPunctuationIsNotSplit() {
        assertEquals(listOf("Simon & Garfunkel", "AC/DC", "A feat. B"), MalojaClient.artists("Simon & Garfunkel, AC/DC, A feat. B"))
    }

    @Test fun payloadContainsListAndDisablesFurtherServerParsing() {
        val json = JSONObject(MalojaClient.payload(listen))
        val artists = json.getJSONArray("artists")
        assertEquals(3, artists.length())
        assertEquals("Автор 1", artists.getString(0))
        assertEquals("Автор 2", artists.getString(1))
        assertEquals("Автор 3", artists.getString(2))
        assertEquals("Трек", json.getString("title"))
        assertEquals("Album", json.getString("album"))
        assertEquals(listen.listenedAt, json.getLong("time"))
        assertEquals(30, json.getInt("duration"))
        assertEquals(120, json.getInt("length"))
        assertTrue(json.getBoolean("nofix"))
        assertFalse(json.has("key"))
    }

    @Test fun optionalMetadataIsOmittedWhenUnknown() {
        val json = JSONObject(MalojaClient.payload(listen.copy(track = listen.track.copy(album = null, durationMs = null))))
        assertFalse(json.has("album"))
        assertFalse(json.has("length"))
    }

    @Test fun singleArtistIsStillAnArray() {
        val json = JSONObject(MalojaClient.payload(listen.copy(track = listen.track.copy(artist = "Artist"))))
        assertEquals(1, json.getJSONArray("artists").length())
    }

    @Test fun nativeKeyIsAddedOnlyAtSendTime() {
        val payload = MalojaClient.payload(listen)
        val client = MalojaClient(endpoint) { url, body ->
            assertEquals("https://music.example/maloja/apis/mlj_1/newscrobble", url)
            assertEquals("test-key", JSONObject(body).getString("key"))
            ApiResponse(200, "{\"status\":\"success\"}")
        }
        assertEquals(200, client.submit("test-key", payload).code)
        assertFalse(JSONObject(payload).has("key"))
    }

    @Test fun errorInSuccessfulHttpResponseIsNotMarkedSubmitted() {
        val client = MalojaClient(endpoint) { _, _ -> ApiResponse(200, "{\"status\":\"failure\"}") }
        assertEquals(SubmissionDecision.Rejected, submissionDecision(client.submit("test-key", MalojaClient.payload(listen)).code))
    }

    @Test fun unauthorizedAndTransientResponsesKeepTheirStatus() {
        listOf(401, 403, 429, 500, 503).forEach { code ->
            val client = MalojaClient(endpoint) { _, _ -> ApiResponse(code, "error", "120") }
            assertEquals(code, client.submit("test-key", MalojaClient.payload(listen)).code)
        }
    }

    @Test fun duplicateListenSuccessIsAccepted() {
        val client = MalojaClient(endpoint) { _, _ -> ApiResponse(200, "{\"status\":\"success\",\"warnings\":[{\"type\":\"scrobble_exists\"}]}") }
        assertEquals(200, client.submit("test-key", MalojaClient.payload(listen)).code)
    }

    @Test(expected = IOException::class) fun malformedResponseIsRetryable() {
        MalojaClient(endpoint) { _, _ -> ApiResponse(200, "not JSON") }.submit("test-key", MalojaClient.payload(listen))
    }

    @Test fun officialListenBrainzPayloadKeepsTheOriginalArtistString() {
        val json = JSONObject(ListenBrainzClient.payload(listen.copy(splitArtists = false)))
        assertEquals(listen.track.artist, json.getJSONArray("payload").getJSONObject(0).getJSONObject("track_metadata").getString("artist_name"))
    }

    @Test fun boundsKeepEscapedPayloadWithinWorkManagerLimit() {
        val track = listen.track.copy(title = "\u0001".repeat(256), album = "\u0001".repeat(256), artist = (1..32).joinToString(",") { it.toString().padEnd(23, '\u0001') + "X" })
        assertTrue(MalojaClient.payload(listen.copy(track = track)).toByteArray().size + track.title.toByteArray().size < 9000)
    }

    @Test(expected = IllegalArgumentException::class) fun emptyArtistListIsRejected() {
        MalojaClient.payload(listen.copy(track = listen.track.copy(artist = " , , ")))
    }

    @Test(expected = IllegalArgumentException::class) fun nativeClientRejectsOtherServerPaths() {
        MalojaClient(DEFAULT_SCROBBLING_ENDPOINT)
    }
}
