package com.ahoura.asha_scanner_ip.core.guard

import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor

/**
 * Userspace TUN splice that sits in front of VPN tunnels (Aether / Xray / Tun2socks).
 *
 * Implements:
 * - QUIC Rejection: responds immediately with ICMP Port Unreachable (Type 3 Code 3)
 *   so apps (Chrome, Instagram) switch to TCP without 3-5 second timeouts.
 * - WebRTC Leak Protection: drops STUN/TURN packets to prevent real IP exposure.
 * - IPv6 Leak Protection: drops unroutable IPv6 packets on IPv4 tunnels.
 * - DNS Sinkhole: responds to known tracker and ad domain queries with 0.0.0.0.
 */
internal object TunPacketFilter {
    @Volatile
    var isSinkholeEnabled: Boolean = true
        private set

    @Volatile
    var isWebRtcBlockerEnabled: Boolean = true
        private set

    @Volatile
    var isQuicBlockerEnabled: Boolean = true
        private set

    @Volatile
    var isIpv6BlockerEnabled: Boolean = true
        private set

    private val lock = Any()
    private var realTun: ParcelFileDescriptor? = null
    private var filterPeer: ParcelFileDescriptor? = null
    private var upThread: Thread? = null
    private var downThread: Thread? = null

    @Volatile
    private var running = false

    fun applyFlags(
        sinkhole: Boolean = true,
        webRtc: Boolean = true,
        quic: Boolean = true,
        ipv6: Boolean = true,
    ) {
        isSinkholeEnabled = sinkhole
        isWebRtcBlockerEnabled = webRtc
        isQuicBlockerEnabled = quic
        isIpv6BlockerEnabled = ipv6
    }

    fun attach(tun: ParcelFileDescriptor, mtu: Int): ParcelFileDescriptor {
        synchronized(lock) {
            stopLocked()
            val pair = createPacketPair()
            val enginePfd = pair.first
            val filterPfd = pair.second
            runCatching { setBlocking(tun.fileDescriptor, blocking = true) }
            runCatching { setBlocking(filterPfd.fileDescriptor, blocking = true) }
            bumpSocketBuffers(filterPfd.fileDescriptor)
            bumpSocketBuffers(enginePfd.fileDescriptor)
            realTun = tun
            filterPeer = filterPfd
            running = true
            val bufferSize = (mtu + 64).coerceIn(2048, 65535)
            upThread = startLoop("asha-tun-up") {
                forwardDeviceToEngine(tun.fileDescriptor, filterPfd.fileDescriptor, bufferSize)
            }
            downThread = startLoop("asha-tun-down") {
                splice(filterPfd.fileDescriptor, tun.fileDescriptor, bufferSize)
            }
            ConnectionLog.record(
                "TunPacketFilter attached: quic=$isQuicBlockerEnabled webrtc=$isWebRtcBlockerEnabled " +
                    "sinkhole=$isSinkholeEnabled ipv6=$isIpv6BlockerEnabled mtu=$mtu"
            )
            return enginePfd
        }
    }

    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    private fun stopLocked() {
        running = false
        runCatching { filterPeer?.close() }
        runCatching { realTun?.close() }
        filterPeer = null
        realTun = null
        val up = upThread
        val down = downThread
        upThread = null
        downThread = null
        up?.join(750L)
        down?.join(750L)
        if (up?.isAlive == true) up.interrupt()
        if (down?.isAlive == true) down.interrupt()
    }

    private fun startLoop(name: String, body: () -> Unit): Thread {
        val thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            try {
                body()
            } catch (_: InterruptedException) {
            } catch (error: Throwable) {
                if (running) {
                    ConnectionLog.record("TunPacketFilter $name stopped: ${error.message}")
                }
            }
        }, name)
        thread.isDaemon = true
        thread.start()
        return thread
    }

    private fun forwardDeviceToEngine(tunFd: FileDescriptor, engineFd: FileDescriptor, bufferSize: Int) {
        val packet = ByteArray(bufferSize)
        val reply = ByteArray(bufferSize + 64)
        val qnameScratch = CharArray(256)
        while (running) {
            val n = readPacket(tunFd, packet) ?: break
            if (n <= 0) break
            val sinkhole = isSinkholeEnabled
            val webrtc = isWebRtcBlockerEnabled
            val quic = isQuicBlockerEnabled
            val ipv6 = isIpv6BlockerEnabled
            if (sinkhole || webrtc || quic || ipv6) {
                when (
                    TunPacketPolicy.inspect(
                        packet,
                        n,
                        isSinkholeEnabled = sinkhole,
                        isWebRtcBlockerEnabled = webrtc,
                        isQuicBlockerEnabled = quic,
                        isIpv6BlockerEnabled = ipv6,
                        qnameScratch = qnameScratch,
                    )
                ) {
                    TunPacketAction.SINKHOLE -> {
                        val out = TunPacketPolicy.buildDnsSinkholeReply(packet, n, reply)
                        if (out > 0) writePacket(tunFd, reply, out)
                        continue
                    }
                    TunPacketAction.REJECT -> {
                        val out = TunPacketPolicy.buildPortUnreachable(packet, n, reply)
                        if (out > 0) writePacket(tunFd, reply, out)
                        continue
                    }
                    TunPacketAction.DROP -> continue
                    TunPacketAction.FORWARD -> Unit
                }
            }
            writePacket(engineFd, packet, n)
        }
    }

    private fun splice(from: FileDescriptor, to: FileDescriptor, bufferSize: Int) {
        val packet = ByteArray(bufferSize)
        while (running) {
            val n = readPacket(from, packet) ?: break
            if (n <= 0) break
            writePacket(to, packet, n)
        }
    }

    private fun readPacket(fd: FileDescriptor, buffer: ByteArray): Int? {
        while (running) {
            try {
                val n = Os.read(fd, buffer, 0, buffer.size)
                return if (n <= 0) null else n
            } catch (error: ErrnoException) {
                when (error.errno) {
                    OsConstants.EINTR -> continue
                    OsConstants.EAGAIN -> {
                        waitReadable(fd)
                        continue
                    }
                    OsConstants.EPIPE, OsConstants.EBADF, OsConstants.ECONNRESET -> return null
                    else -> {
                        if (running) throw error
                        return null
                    }
                }
            }
        }
        return null
    }

    private fun writePacket(fd: FileDescriptor, buffer: ByteArray, length: Int) {
        var offset = 0
        while (running && offset < length) {
            try {
                val n = Os.write(fd, buffer, offset, length - offset)
                if (n <= 0) return
                offset += n
            } catch (error: ErrnoException) {
                when (error.errno) {
                    OsConstants.EINTR -> continue
                    OsConstants.EAGAIN -> {
                        waitWritable(fd)
                        continue
                    }
                    OsConstants.EPIPE, OsConstants.EBADF, OsConstants.ECONNRESET -> return
                    else -> {
                        if (running) throw error
                        return
                    }
                }
            }
        }
    }

    private fun waitReadable(fd: FileDescriptor) {
        val poll = android.system.StructPollfd()
        poll.fd = fd
        poll.events = OsConstants.POLLIN.toShort()
        runCatching { Os.poll(arrayOf(poll), 250) }
    }

    private fun waitWritable(fd: FileDescriptor) {
        val poll = android.system.StructPollfd()
        poll.fd = fd
        poll.events = OsConstants.POLLOUT.toShort()
        runCatching { Os.poll(arrayOf(poll), 250) }
    }

    private fun bumpSocketBuffers(fd: FileDescriptor) {
        val size = 512 * 1024
        runCatching { Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVBUF, size) }
        runCatching { Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, OsConstants.SO_SNDBUF, size) }
    }

    private fun createPacketPair(): Pair<ParcelFileDescriptor, ParcelFileDescriptor> {
        val left = FileDescriptor()
        val right = FileDescriptor()
        try {
            Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_SEQPACKET, 0, left, right)
        } catch (_: ErrnoException) {
            Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, left, right)
        }
        val engine = ParcelFileDescriptor.dup(left)
        val filter = ParcelFileDescriptor.dup(right)
        runCatching { Os.close(left) }
        runCatching { Os.close(right) }
        return engine to filter
    }

    private fun setBlocking(fd: FileDescriptor, blocking: Boolean) {
        val flags = Os.fcntlInt(fd, OsConstants.F_GETFL, 0)
        val updated = if (blocking) {
            flags and OsConstants.O_NONBLOCK.inv()
        } else {
            flags or OsConstants.O_NONBLOCK
        }
        Os.fcntlInt(fd, OsConstants.F_SETFL, updated)
    }
}
