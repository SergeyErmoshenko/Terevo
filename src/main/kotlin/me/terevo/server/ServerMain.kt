package me.terevo.server

import me.terevo.app.AppController
import me.terevo.persistence.SqliteProjectService
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID

fun main() {
    val controller = AppController(SqliteProjectService())
    val server = StateServer(controller)
    val token = UUID.randomUUID().toString()
    ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { socket ->
        Runtime.getRuntime().addShutdownHook(Thread { controller.close() })
        System.err.println("TEREVO_SERVER_READY ${socket.localPort} $token")
        while (!socket.isClosed) {
            val client = socket.accept()
            Thread {
                client.use { connection ->
                    runCatching { HttpConnection(connection, token, server).serve() }
                        .onFailure { System.err.println("HTTP request failed: ${it.message}") }
                }
            }.start()
        }
    }
}
