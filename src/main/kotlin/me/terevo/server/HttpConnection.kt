package me.terevo.server

import kotlinx.serialization.json.Json
import java.io.InputStream
import java.net.Socket
import java.nio.charset.StandardCharsets

class HttpConnection(
    private val socket: Socket,
    private val token: String,
    private val stateServer: StateServer,
) {
    fun serve() {
        socket.soTimeout = 30_000
        val input = socket.getInputStream()
        val requestLine = input.readHttpLine() ?: return
        val parts = requestLine.split(' ', limit = 3)
        if (parts.size != 3) return respond(400, "Bad Request", "{}")
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = input.readHttpLine() ?: return
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
        }
        val origin = headers["origin"]
        if (origin != null && !origin.startsWith("file://") && !origin.startsWith("http://127.0.0.1") && !origin.startsWith("http://localhost")) {
            return respond(403, "Forbidden", "{}")
        }
        if (parts[1] == "/health" && parts[0] == "GET") return respond(200, "OK", "{\"ok\":true}")
        if (parts[1] != "/hello" && parts[1] != "/api") return respond(404, "Not Found", "{}")
        if (headers["authorization"] != "Bearer $token") return respond(401, "Unauthorized", "{}")
        if (parts[1] == "/hello" && parts[0] == "GET") return respond(200, "OK", stateServer.hello())
        if (parts[1] != "/api" || parts[0] != "POST") return respond(404, "Not Found", "{}")
        val length = headers["content-length"]?.toIntOrNull() ?: return respond(411, "Length Required", "{}")
        if (length !in 1..1_048_576) return respond(413, "Payload Too Large", "{}")
        val bodyBytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(bodyBytes, offset, length - offset)
            if (count < 0) return respond(400, "Bad Request", "{}")
            offset += count
        }
        val body = bodyBytes.toString(StandardCharsets.UTF_8)
        val response = runCatching {
            StateServer.json.decodeFromString<Request>(body)
            stateServer.handle(body)
        }.getOrElse { error ->
            """{"id":-1,"error":${Json.encodeToString(error.message ?: "Request failed")}}"""
        }
        respond(200, "OK", response)
    }

    private fun respond(status: Int, label: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val output = socket.getOutputStream()
        output.write("HTTP/1.1 $status $label\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Content-Type: application/json; charset=utf-8\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Content-Length: ${bytes.size}\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Cache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }
}

private fun InputStream.readHttpLine(): String? {
    val bytes = ArrayList<Byte>()
    while (bytes.size < 8192) {
        val next = read()
        if (next < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(StandardCharsets.US_ASCII)
        if (next == '\n'.code) break
        if (next != '\r'.code) bytes += next.toByte()
    }
    return bytes.toByteArray().toString(StandardCharsets.US_ASCII)
}
