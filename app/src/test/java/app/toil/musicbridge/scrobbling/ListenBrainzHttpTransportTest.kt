package app.toil.musicbridge.scrobbling

import java.net.InetAddress
import java.net.ServerSocket
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenBrainzHttpTransportTest {
    @Test fun malojaCompatibleServerReceivesValidationAndSubmissionAtCustomPrefix() {
        TestServer(2) { request ->
            assertEquals("Token test-key", request.headers["authorization"])
            if (request.method == "GET") {
                assertEquals("/maloja/apis/listenbrainz/1/validate-token", request.path)
                Reply(body = "{\"code\":200,\"message\":\"Token valid.\",\"valid\":true,\"user_name\":\"My Maloja\"}")
            } else {
                assertEquals("POST", request.method)
                assertEquals("/maloja/apis/listenbrainz/1/submit-listens", request.path)
                assertEquals("single", JSONObject(request.body).getString("listen_type"))
                Reply(body = "{\"status\":\"ok\"}")
            }
        }.use { server ->
            val client = ListenBrainzClient(ListenBrainzHttpTransport("${server.url}/maloja/apis/listenbrainz", true))
            assertEquals("My Maloja", client.validateToken("test-key"))
            val listen = ScrobbleListen("id", "account", "player", ScrobbleTrack("Track", "Artist"), 1_790_000_000, 30_000)
            assertEquals(200, client.submit("test-key", ListenBrainzClient.payload(listen)).code)
            server.await()
        }
    }

    @Test fun redirectsAreNotFollowedWithCredentials() {
        TestServer(1) { request ->
            assertEquals("/1/validate-token", request.path)
            Reply(code = 302, headers = "Location: http://127.0.0.1:1/redirected\r\n")
        }.use { server ->
            val transport = ListenBrainzHttpTransport(server.url, true)
            assertEquals(302, transport.request("validate-token", "test-key", null).code)
            server.await()
        }
    }

    @Test fun unapprovedHttpCannotCreateTransport() {
        assertTrue(runCatching { ListenBrainzHttpTransport("http://127.0.0.1:42010/apis/listenbrainz") }.isFailure)
    }

    private data class Request(val method: String, val path: String, val headers: Map<String, String>, val body: String)
    private data class Reply(val code: Int = 200, val body: String = "", val headers: String = "")

    private class TestServer(requestCount: Int, handler: (Request) -> Reply) : AutoCloseable {
        private val socket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5000 }
        val url = "http://127.0.0.1:${socket.localPort}"
        private val executor = Executors.newSingleThreadExecutor()
        private val task = executor.submit {
            repeat(requestCount) {
                socket.accept().use { client ->
                    client.soTimeout = 5000
                    val reader = client.getInputStream().bufferedReader(Charsets.UTF_8)
                    val start = reader.readLine().split(' ')
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        headers[line.substringBefore(':').lowercase(Locale.ROOT)] = line.substringAfter(':').trim()
                    }
                    val body = CharArray(headers["content-length"]?.toInt() ?: 0)
                    var offset = 0
                    while (offset < body.size) {
                        val count = reader.read(body, offset, body.size - offset)
                        check(count > 0)
                        offset += count
                    }
                    val reply = handler(Request(start[0], start[1], headers, String(body)))
                    val bytes = reply.body.toByteArray(Charsets.UTF_8)
                    client.getOutputStream().apply {
                        write("HTTP/1.1 ${reply.code} Response\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n${reply.headers}\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
        }

        fun await() { task.get(10, TimeUnit.SECONDS) }

        override fun close() {
            socket.close()
            executor.shutdownNow()
        }
    }
}
