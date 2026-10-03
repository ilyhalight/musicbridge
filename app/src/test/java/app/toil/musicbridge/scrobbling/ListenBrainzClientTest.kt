package app.toil.musicbridge.scrobbling

import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenBrainzClientTest {
    private val listen = ScrobbleListen("id", "account", "player", ScrobbleTrack("Трек", "Artist", "Album", "media-id", 120_000), 1_790_000_000, 30_000)

    @Test fun payloadContainsExactlyOneSingleListenAndStartTimestamp() {
        val json = JSONObject(ListenBrainzClient.payload(listen))
        assertEquals("single", json.getString("listen_type"))
        assertEquals(1, json.getJSONArray("payload").length())
        val payload = json.getJSONArray("payload").getJSONObject(0)
        assertEquals(listen.listenedAt, payload.getLong("listened_at"))
        val metadata = payload.getJSONObject("track_metadata")
        assertEquals("Трек", metadata.getString("track_name"))
        assertEquals("Artist", metadata.getString("artist_name"))
        assertEquals("Album", metadata.getString("release_name"))
        val additional = metadata.getJSONObject("additional_info")
        assertEquals("MusicBridge", additional.getString("submission_client"))
        assertEquals("player", additional.getString("media_player"))
        assertEquals(30, additional.getInt("duration_played"))
        assertEquals(120_000, additional.getLong("duration_ms"))
        assertEquals(120, additional.getLong("duration"))
        assertFalse(metadata.has("media_id"))
        assertFalse(json.toString().contains("playing_now"))
    }

    @Test fun unknownAlbumAndDurationAreOmitted() {
        val payload = JSONObject(ListenBrainzClient.payload(listen.copy(track = listen.track.copy(album = null, durationMs = null))))
        val metadata = payload.getJSONArray("payload").getJSONObject(0).getJSONObject("track_metadata")
        assertFalse(metadata.has("release_name"))
        assertFalse(metadata.getJSONObject("additional_info").has("duration_ms"))
        assertFalse(metadata.getJSONObject("additional_info").has("duration"))
    }

    @Test fun oversizedMetadataIsBoundedForWorkManager() {
        val payload = ListenBrainzClient.payload(listen.copy(track = listen.track.copy(title = "文".repeat(5000), artist = "文".repeat(5000), album = "文".repeat(5000))))
        assertTrue(payload.toByteArray().size < 5000)
    }

    @Test fun validatesWithAuthTokenAndNoQueryString() {
        val client = ListenBrainzClient { path, token, body ->
            assertEquals("validate-token", path)
            assertEquals("secret", token)
            assertNull(body)
            ApiResponse(200, "{\"valid\":true,\"user_name\":\"Listener\"}")
        }
        assertEquals("Listener", client.validateToken("secret"))
    }

    @Test fun http200CanStillMeanInvalidToken() {
        val client = ListenBrainzClient { _, _, _ -> ApiResponse(200, "{\"valid\":false}") }
        assertNull(client.validateToken("secret"))
    }

    @Test(expected = IOException::class) fun malformedValidationIsNotSaved() {
        ListenBrainzClient { _, _, _ -> ApiResponse(200, "{}") }.validateToken("secret")
    }

    @Test fun submitUsesOnlySubmitListensEndpoint() {
        val payload = ListenBrainzClient.payload(listen)
        val client = ListenBrainzClient { path, token, body ->
            assertEquals("submit-listens", path)
            assertEquals("secret", token)
            assertEquals(payload, body)
            ApiResponse(200)
        }
        assertEquals(200, client.submit("secret", payload).code)
    }

    @Test fun transientFailuresRetryAndInvalidPayloadFails() {
        listOf(408, 429, 500, 502, 503).forEach { assertEquals(SubmissionDecision.Retry, submissionDecision(it)) }
        listOf(401, 403).forEach { assertEquals(SubmissionDecision.Unauthorized, submissionDecision(it)) }
        assertEquals(SubmissionDecision.Accepted, submissionDecision(200))
        assertEquals(SubmissionDecision.Rejected, submissionDecision(400))
        assertEquals(SubmissionDecision.Rejected, submissionDecision(302))
    }

    @Test fun rateLimitDelayRespectsServerAndBounds() {
        assertEquals(120_000, retryDelayMs("120", 0))
        assertEquals(30_000, retryDelayMs("0", 0))
        assertEquals(86_400_000, retryDelayMs("99999999999", 0))
        assertEquals(60_000, retryDelayMs(null, 0))
        assertEquals(60_000, retryDelayMs("bad", 0))
        assertEquals(60_000, retryDelayMs("Thu, 01 Jan 1970 00:01:00 GMT", 0))
    }
}
