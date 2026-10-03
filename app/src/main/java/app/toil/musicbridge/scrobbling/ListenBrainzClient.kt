package app.toil.musicbridge.scrobbling

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

data class ApiResponse(val code: Int, val body: String = "", val retryAfter: String? = null)

fun interface ListenBrainzTransport {
    fun request(path: String, token: String, body: String?): ApiResponse
}

class ListenBrainzHttpTransport(
    endpoint: String = DEFAULT_SCROBBLING_ENDPOINT,
    allowHttp: Boolean = false,
) : ListenBrainzTransport {
    private val endpoint = normalizeScrobblingEndpoint(endpoint, allowHttp)

    override fun request(path: String, token: String, body: String?): ApiResponse {
        require(path == "validate-token" || path == "submit-listens")
        return scrobblingHttpRequest("$endpoint/$path", token, body)
    }
}

internal fun scrobblingHttpRequest(url: String, token: String?, body: String?): ApiResponse {
    if (token != null) require(token.isNotBlank() && token.length <= 256 && token.all { it.code in 33..126 })
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = if (body == null) "GET" else "POST"
        if (token != null) connection.setRequestProperty("Authorization", "Token $token")
        connection.setRequestProperty("User-Agent", "MusicBridge/1.0 (https://github.com/ilyhalight/)")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            val buffer = CharArray(1024)
            val text = StringBuilder()
            while (text.length < 16_384) {
                val count = reader.read(buffer, 0, minOf(buffer.size, 16_384 - text.length))
                if (count < 0) break
                text.append(buffer, 0, count)
            }
            text.toString()
        }.orEmpty()
        return ApiResponse(code, response, connection.getHeaderField("Retry-After") ?: connection.getHeaderField("X-RateLimit-Reset-In"))
    } finally {
        connection.disconnect()
    }
}

enum class SubmissionDecision { Accepted, Retry, Unauthorized, Rejected }

fun submissionDecision(code: Int): SubmissionDecision = when {
    code in 200..299 -> SubmissionDecision.Accepted
    code == 401 || code == 403 -> SubmissionDecision.Unauthorized
    code == 408 || code == 429 || code in 500..599 -> SubmissionDecision.Retry
    else -> SubmissionDecision.Rejected
}

fun retryDelayMs(header: String?, nowMs: Long): Long {
    val seconds = header?.toLongOrNull()
    if (seconds != null) return seconds.coerceIn(30, 86_400) * 1000L
    val date = runCatching { ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
    return date?.let { (it - nowMs).coerceIn(30_000, 86_400_000) } ?: 60_000
}

class ListenBrainzClient(private val transport: ListenBrainzTransport = ListenBrainzHttpTransport()) {
    fun validateToken(token: String): String? {
        val response = transport.request("validate-token", token, null)
        if (response.code == 401 || response.code == 403) return null
        if (response.code != 200) throw IOException("ListenBrainz validation HTTP ${response.code}")
        val json = runCatching { JSONObject(response.body) }.getOrElse { throw IOException("Invalid validation response") }
        if (!json.has("valid")) throw IOException("Missing validation result")
        if (!json.getBoolean("valid")) return null
        return json.optString("user_name").takeIf { it.isNotBlank() } ?: throw IOException("Missing account name")
    }

    fun submit(token: String, payload: String): ApiResponse = transport.request("submit-listens", token, payload)

    companion object {
        fun payload(listen: ScrobbleListen): String {
            val additional = JSONObject()
                .put("submission_client", "MusicBridge")
                .put("submission_client_version", "1.0")
                .put("media_player", listen.packageName)
                .put("duration_played", listen.listenedMs / 1000)
            listen.track.durationMs?.takeIf { it in 1..2_073_600_000L }?.let {
                additional.put("duration_ms", it)
                // Maloja's ListenBrainz adapter reads the legacy duration in seconds.
                if (it >= 1000) additional.put("duration", it / 1000)
            }
            val metadata = JSONObject()
                .put("track_name", listen.track.title.take(256))
                .put("artist_name", listen.track.artist.take(256))
                .put("additional_info", additional)
            listen.track.album?.takeIf { it.isNotBlank() }?.let { metadata.put("release_name", it.take(256)) }
            return JSONObject()
                .put("listen_type", "single")
                .put("payload", JSONArray().put(JSONObject().put("listened_at", listen.listenedAt).put("track_metadata", metadata)))
                .toString()
        }
    }
}
