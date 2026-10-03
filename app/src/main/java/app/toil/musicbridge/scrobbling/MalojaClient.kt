package app.toil.musicbridge.scrobbling

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

class MalojaClient(
    endpoint: String,
    allowHttp: Boolean = false,
    private val transport: (String, String) -> ApiResponse = { url, body -> scrobblingHttpRequest(url, null, body) },
) {
    private val endpoint = requireNotNull(malojaNativeEndpoint(normalizeScrobblingEndpoint(endpoint, allowHttp)))

    fun submit(token: String, payload: String): ApiResponse {
        require(token.isNotBlank() && token.length <= 256 && token.all { it.code in 33..126 })
        // Native Maloja authentication accepts the key in JSON, not an Authorization header.
        val authenticatedPayload = JSONObject(payload).put("key", token).toString()
        val response = transport("$endpoint/newscrobble", authenticatedPayload)
        if (response.code in 200..299) {
            val json = runCatching { JSONObject(response.body) }.getOrElse { throw IOException("Invalid Maloja response") }
            if (json.optString("status") !in setOf("success", "ok")) {
                return response.copy(code = 400)
            }
        }
        return response
    }

    companion object {
        fun artists(value: String): List<String> = value.split(',').map(String::trim).filter(String::isNotEmpty).distinct()

        fun payload(listen: ScrobbleListen): String {
            val names = artists(listen.track.artist)
            require(names.isNotEmpty())
            // Bound the total artist data while keeping individual names intact.
            require(names.size <= 32 && names.all { it.length <= 256 } && names.sumOf { it.length } <= 768)
            val json = JSONObject()
                .put("artists", JSONArray(names))
                .put("title", listen.track.title.take(256))
                .put("time", listen.listenedAt)
                .put("duration", listen.listenedMs / 1000)
                // The explicit artist list must not be split again by server cleanup rules.
                .put("nofix", true)
            listen.track.album?.takeIf { it.isNotBlank() }?.let { json.put("album", it.take(256)) }
            listen.track.durationMs?.takeIf { it in 1000..2_073_600_000L }?.let { json.put("length", it / 1000) }
            return json.toString()
        }
    }
}
