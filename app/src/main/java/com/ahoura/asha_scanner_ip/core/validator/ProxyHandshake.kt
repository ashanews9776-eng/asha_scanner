package com.ahoura.asha_scanner_ip.core.validator

import java.security.MessageDigest
import java.util.Locale

/**
 * Pure, side-effect-free byte framing for the VLESS / Trojan request headers and
 * WebSocket transport. Kept separate from [TunnelValidator] so the wire format
 * (the part that must be byte-exact for a server to accept the tunnel) can be
 * unit-tested without opening a socket.
 */
internal object ProxyHandshake {

    /**
     * VLESS request header (no addons):
     * `ver(0) | uuid[16] | addonLen(0) | cmd(1=tcp) | port[2] | atyp(2=domain) | len | host`.
     * The HTTP/payload bytes are appended by the caller.
     */
    fun vlessHeader(uuid: String, host: String, port: Int): ByteArray {
        val id = uuidToBytes(uuid)
        val hostBytes = host.toByteArray(Charsets.US_ASCII)
        val out = ByteArray(1 + 16 + 1 + 1 + 2 + 1 + 1 + hostBytes.size)
        var i = 0
        out[i++] = 0                                  // version
        System.arraycopy(id, 0, out, i, 16); i += 16  // uuid
        out[i++] = 0                                  // addon length
        out[i++] = 1                                  // command: TCP
        out[i++] = (port ushr 8).toByte()
        out[i++] = (port and 0xFF).toByte()
        out[i++] = 2                                  // address type: domain
        out[i++] = hostBytes.size.toByte()
        System.arraycopy(hostBytes, 0, out, i, hostBytes.size)
        return out
    }

    /**
     * Trojan request header:
     * `hex(SHA224(pwd))[56] | CRLF | cmd(1=connect) | atyp(3=domain) | len | host | port[2] | CRLF`.
     */
    fun trojanHeader(password: String, host: String, port: Int): ByteArray {
        val pwd = sha224Hex(password).toByteArray(Charsets.US_ASCII) // 56 bytes
        val hostBytes = host.toByteArray(Charsets.US_ASCII)
        val out = ByteArray(pwd.size + 2 + 1 + 1 + 1 + hostBytes.size + 2 + 2)
        var i = 0
        System.arraycopy(pwd, 0, out, i, pwd.size); i += pwd.size
        out[i++] = 0x0D; out[i++] = 0x0A              // CRLF
        out[i++] = 1                                  // command: CONNECT
        out[i++] = 3                                  // SOCKS5 atyp: domain
        out[i++] = hostBytes.size.toByte()
        System.arraycopy(hostBytes, 0, out, i, hostBytes.size); i += hostBytes.size
        out[i++] = (port ushr 8).toByte()
        out[i++] = (port and 0xFF).toByte()
        out[i++] = 0x0D; out[i] = 0x0A                // CRLF
        return out
    }

    /** Standard UUID -> 16 bytes; non-UUID ids map to a deterministic MD5 digest. */
    fun uuidToBytes(uuid: String): ByteArray {
        val hex = uuid.replace("-", "")
        if (hex.length == 32 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            return ByteArray(16) {
                ((hex[it * 2].digitToInt(16) shl 4) or hex[it * 2 + 1].digitToInt(16)).toByte()
            }
        }
        return MessageDigest.getInstance("MD5").digest(uuid.toByteArray(Charsets.UTF_8))
    }

    fun sha224Hex(s: String): String {
        val d = MessageDigest.getInstance("SHA-224").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(d.size * 2)
        for (b in d) sb.append(String.format(Locale.US, "%02x", b.toInt() and 0xFF))
        return sb.toString()
    }

    /**
     * Encodes a client->server WebSocket binary frame (FIN + opcode 0x2), masked
     * with [mask] (RFC 6455 requires client frames to be masked).
     */
    fun wsBinaryFrame(payload: ByteArray, mask: ByteArray): ByteArray {
        require(mask.size == 4) { "ws mask must be 4 bytes" }
        val len = payload.size
        val header = ArrayList<Byte>(14)
        header.add((0x80 or 0x2).toByte())
        when {
            len < 126 -> header.add((0x80 or len).toByte())
            len < 65536 -> {
                header.add((0x80 or 126).toByte())
                header.add((len ushr 8).toByte()); header.add((len and 0xFF).toByte())
            }
            else -> {
                header.add((0x80 or 127).toByte())
                for (shift in 56 downTo 0 step 8) header.add(((len.toLong() ushr shift) and 0xFF).toByte())
            }
        }
        for (b in mask) header.add(b)
        val out = ByteArray(header.size + len)
        for (j in header.indices) out[j] = header[j]
        for (j in 0 until len) out[header.size + j] = (payload[j].toInt() xor mask[j % 4].toInt()).toByte()
        return out
    }
}
