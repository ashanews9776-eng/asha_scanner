package com.ahoura.asha_scanner_ip.core.net

import com.ahoura.asha_scanner_ip.core.model.AntiDpiConfig
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.channels.SocketChannel

class AntiDpiOutputStream(
    private val delegate: OutputStream,
    private val config: AntiDpiConfig,
) : FilterOutputStream(delegate) {

    private var clientHelloFragmented = false
    private val recipe by lazy { config.parseRecipe() }

    override fun write(b: Int) {
        delegate.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (!config.enabled || clientHelloFragmented) {
            delegate.write(b, off, len)
            return
        }

        // Detect TLS Handshake: ClientHello (0x16 0x03 ... 0x01)
        val isTlsHandshake = len >= 6 &&
            b[off] == 0x16.toByte() &&
            b[off + 1] == 0x03.toByte() &&
            b[off + 5] == 0x01.toByte()

        if (!isTlsHandshake) {
            delegate.write(b, off, len)
            return
        }

        // Fragment the ClientHello record flight into small pieces
        clientHelloFragmented = true
        var currentOff = off
        var remaining = len

        // First chunk
        val firstSize = recipe.firstChunk.coerceIn(1, remaining)
        delegate.write(b, currentOff, firstSize)
        delegate.flush()
        currentOff += firstSize
        remaining -= firstSize

        if (recipe.delayMs > 0 && remaining > 0) {
            runCatching { Thread.sleep(recipe.delayMs) }
        }

        // Subsequent chunks
        var splits = 1
        while (remaining > 0 && splits < recipe.maxSplit) {
            val chunkSize = recipe.nextChunk.coerceIn(1, remaining)
            delegate.write(b, currentOff, chunkSize)
            delegate.flush()
            currentOff += chunkSize
            remaining -= chunkSize
            splits++

            if (recipe.delayMs > 0 && remaining > 0) {
                runCatching { Thread.sleep(recipe.delayMs) }
            }
        }

        // Tail if any remaining
        if (remaining > 0) {
            delegate.write(b, currentOff, remaining)
            delegate.flush()
        }
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}

/**
 * Socket wrapper that intercepts outgoing TLS ClientHello bytes and slices them
 * into small TCP segments separated by delays to prevent DPI SNI inspection.
 */
class AntiDpiSocket(
    private val delegate: Socket,
    private val config: AntiDpiConfig,
) : Socket() {

    private val antiDpiOut: OutputStream by lazy {
        AntiDpiOutputStream(delegate.outputStream, config)
    }

    override fun getInputStream(): InputStream = delegate.inputStream
    override fun getOutputStream(): OutputStream = antiDpiOut

    override fun connect(endpoint: SocketAddress?) = delegate.connect(endpoint)
    override fun connect(endpoint: SocketAddress?, timeout: Int) = delegate.connect(endpoint, timeout)
    override fun bind(bindpoint: SocketAddress?) = delegate.bind(bindpoint)
    override fun getInetAddress(): InetAddress = delegate.inetAddress
    override fun getLocalAddress(): InetAddress = delegate.localAddress
    override fun getPort(): Int = delegate.port
    override fun getLocalPort(): Int = delegate.localPort
    override fun getRemoteSocketAddress(): SocketAddress = delegate.remoteSocketAddress
    override fun getLocalSocketAddress(): SocketAddress = delegate.localSocketAddress
    override fun getChannel(): SocketChannel? = delegate.channel
    override fun setTcpNoDelay(on: Boolean) { delegate.tcpNoDelay = on }
    override fun getTcpNoDelay(): Boolean = delegate.tcpNoDelay
    override fun setSoLinger(on: Boolean, linger: Int) { delegate.setSoLinger(on, linger) }
    override fun getSoLinger(): Int = delegate.soLinger
    override fun sendUrgentData(data: Int) = delegate.sendUrgentData(data)
    override fun setOOBInline(on: Boolean) { delegate.oobInline = on }
    override fun getOOBInline(): Boolean = delegate.oobInline
    override fun setSoTimeout(timeout: Int) { delegate.soTimeout = timeout }
    override fun getSoTimeout(): Int = delegate.soTimeout
    override fun setSendBufferSize(size: Int) { delegate.sendBufferSize = size }
    override fun getSendBufferSize(): Int = delegate.sendBufferSize
    override fun setReceiveBufferSize(size: Int) { delegate.receiveBufferSize = size }
    override fun getReceiveBufferSize(): Int = delegate.receiveBufferSize
    override fun setKeepAlive(on: Boolean) { delegate.keepAlive = on }
    override fun getKeepAlive(): Boolean = delegate.keepAlive
    override fun setTrafficClass(tc: Int) { delegate.trafficClass = tc }
    override fun getTrafficClass(): Int = delegate.trafficClass
    override fun setReuseAddress(on: Boolean) { delegate.reuseAddress = on }
    override fun getReuseAddress(): Boolean = delegate.reuseAddress
    override fun close() = delegate.close()
    override fun shutdownInput() = delegate.shutdownInput()
    override fun shutdownOutput() = delegate.shutdownOutput()
    override fun toString(): String = delegate.toString()
    override fun isConnected(): Boolean = delegate.isConnected
    override fun isBound(): Boolean = delegate.isBound
    override fun isClosed(): Boolean = delegate.isClosed
    override fun isInputShutdown(): Boolean = delegate.isInputShutdown
    override fun isOutputShutdown(): Boolean = delegate.isOutputShutdown
}
