package app.toil.musicbridge.scrobbling

import java.net.URI
import java.util.Locale

const val DEFAULT_SCROBBLING_ENDPOINT = "https://api.listenbrainz.org/1"

fun normalizeScrobblingEndpoint(value: String, allowHttp: Boolean = false): String {
    val uri = URI(value.trim())
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    require(scheme == "https" || (scheme == "http" && allowHttp))
    require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
    require(uri.port == -1 || uri.port in 1..65535)
    val path = uri.rawPath.orEmpty().trimEnd('/')
    require(uri.path.orEmpty().split('/').none { it == "." || it == ".." })
    require(!path.contains("%2f", ignoreCase = true) && !path.contains("%5c", ignoreCase = true))
    require(path.substringAfterLast('/') !in setOf("validate-token", "submit-listens"))
    val port = uri.port.takeUnless { it == -1 || (scheme == "https" && it == 443) || (scheme == "http" && it == 80) }
    val authority = uri.host.lowercase(Locale.ROOT) + (port?.let { ":$it" } ?: "")
    val versionedPath = if (path.endsWith("/1")) path else "$path/1"
    return "$scheme://$authority$versionedPath"
}

fun sameScrobblingAccount(oldEndpoint: String, oldUserName: String?, endpoint: String, userName: String): Boolean =
    oldEndpoint == endpoint && oldUserName == userName
