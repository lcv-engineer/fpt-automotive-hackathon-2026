package com.sopa.viva_automotive.debug

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dev-only TCP stand-in for Bluetooth RFCOMM on the AAOS emulator.
 * Phone connects via `adb forward tcp:7788 tcp:7788` → PC LAN IP:7788.
 */
class CompanionNotifTcpServer(
    private val port: Int = DEFAULT_PORT,
    private val onPayload: (String) -> Unit = { line -> Log.i(TAG, "NOTIF_RX $line") },
) : AutoCloseable {

    private val running = AtomicBoolean(false)
    private val acceptExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "aaos-notif-tcp-accept").apply { isDaemon = true }
    }
    private val clientExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "aaos-notif-tcp-client").apply { isDaemon = true }
    }

    @Volatile
    private var serverSocket: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        acceptExecutor.execute { acceptLoop() }
    }

    private fun acceptLoop() {
        try {
            val server = ServerSocket(port).also { serverSocket = it }
            Log.i(TAG, "Companion TCP listening on :$port")
            while (running.get()) {
                val client = server.accept()
                Log.i(TAG, "Phone companion connected: ${client.inetAddress.hostAddress}")
                clientExecutor.execute { readClient(client) }
            }
        } catch (t: Throwable) {
            if (running.get()) Log.e(TAG, "Companion TCP server failed", t)
        }
    }

    private fun readClient(client: Socket) {
        client.use { socket ->
            BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).use { reader ->
                while (running.get()) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) onPayload(line)
                }
            }
        }
    }

    override fun close() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Throwable) {
        }
        serverSocket = null
        acceptExecutor.shutdownNow()
        clientExecutor.shutdownNow()
    }

    companion object {
        private const val TAG = "CompanionNotifTcp"
        const val DEFAULT_PORT: Int = 7788
    }
}
