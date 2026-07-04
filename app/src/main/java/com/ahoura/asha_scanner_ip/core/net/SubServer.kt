package com.ahoura.asha_scanner_ip.core.net

import android.util.Base64
import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import com.ahoura.asha_scanner_ip.core.model.ScanResult
import com.ahoura.asha_scanner_ip.core.output.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

/**
 * A tiny, lightweight local HTTP server that serves the current best results
 * as a V2Ray-compatible subscription link (Base64 list).
 */
class SubServer(private val port: Int = 8081) {
    private var serverSocket: ServerSocket? = null
    private val currentResults = AtomicReference<Pair<List<ScanResult>, ProxyConfig?>>(emptyList<ScanResult>() to null)
    private var running = false

    fun updateResults(results: List<ScanResult>, proxy: ProxyConfig?) {
        currentResults.set(results to proxy)
    }

    suspend fun start() = withContext(Dispatchers.IO) {
        if (running) return@withContext
        try {
            serverSocket = ServerSocket(port)
            running = true
            while (running) {
                val client = serverSocket?.accept() ?: break
                client.use { socket ->
                    val (results, proxy) = currentResults.get()
                    val configs = Exporter.configs(results, proxy)
                    val base64 = Base64.encodeToString(configs.toByteArray(), Base64.NO_WRAP)
                    
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain; charset=utf-8\r\n" +
                            "Content-Length: ${base64.length}\r\n" +
                            "Connection: close\r\n" +
                            "\r\n" +
                            base64
                    
                    socket.getOutputStream().write(response.toByteArray())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            running = false
        }
    }

    fun stop() {
        running = false
        serverSocket?.close()
        serverSocket = null
    }

    fun getUrl(): String = "http://127.0.0.1:$port/sub"
}
