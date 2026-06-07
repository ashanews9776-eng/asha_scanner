package com.ahoura.asha_scanner_ip

import com.ahoura.asha_scanner_ip.core.validator.ProxyHandshake
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks down the byte-exact VLESS / Trojan / WebSocket framing used by the
 * tunnel validator. A server silently drops a tunnel whose header is off by a
 * single byte, so these vectors guard against regressions.
 */
class ProxyHandshakeTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun uuidParsesToSixteenBytes() {
        val id = ProxyHandshake.uuidToBytes("00112233-4455-6677-8899-aabbccddeeff")
        assertArrayEquals(
            bytes(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff),
            id,
        )
    }

    @Test
    fun nonUuidIdMapsToSixteenDeterministicBytes() {
        val a = ProxyHandshake.uuidToBytes("not-a-uuid")
        val b = ProxyHandshake.uuidToBytes("not-a-uuid")
        assertEquals(16, a.size)
        assertArrayEquals(a, b) // deterministic
    }

    @Test
    fun vlessHeaderLayoutIsExact() {
        val h = ProxyHandshake.vlessHeader("00112233-4455-6677-8899-aabbccddeeff", "speed.cloudflare.com", 80)
        // 1 ver + 16 uuid + 1 addonLen + 1 cmd + 2 port + 1 atyp + 1 hostLen + 20 host
        assertEquals(43, h.size)
        assertEquals(0, h[0].toInt())              // version
        assertEquals(0, h[17].toInt())             // addon length
        assertEquals(1, h[18].toInt())             // command TCP
        assertEquals(0, h[19].toInt())             // port hi (80 = 0x0050)
        assertEquals(80, h[20].toInt())            // port lo
        assertEquals(2, h[21].toInt())             // atyp = domain
        assertEquals(20, h[22].toInt())            // host length
        assertEquals("speed.cloudflare.com", String(h.copyOfRange(23, 43), Charsets.US_ASCII))
    }

    @Test
    fun trojanHeaderLayoutIsExact() {
        val h = ProxyHandshake.trojanHeader("p", "a.com", 443)
        // 56 pwd + CRLF + cmd + atyp + len + 5 host + 2 port + CRLF
        assertEquals(56 + 2 + 1 + 1 + 1 + 5 + 2 + 2, h.size)
        assertEquals(0x0D, h[56].toInt() and 0xFF)
        assertEquals(0x0A, h[57].toInt() and 0xFF)
        assertEquals(1, h[58].toInt())             // CONNECT
        assertEquals(3, h[59].toInt())             // SOCKS5 domain
        assertEquals(5, h[60].toInt())             // host length
        assertEquals("a.com", String(h.copyOfRange(61, 66), Charsets.US_ASCII))
        assertEquals(0x01, h[66].toInt() and 0xFF) // port hi (443 = 0x01BB)
        assertEquals(0xBB, h[67].toInt() and 0xFF) // port lo
        assertEquals(0x0D, h[68].toInt() and 0xFF)
        assertEquals(0x0A, h[69].toInt() and 0xFF)
    }

    @Test
    fun sha224MatchesKnownVector() {
        // NIST/known: SHA-224("") = d14a028c2a3a2bc9476102bb288234c415a2b01f828ea62ac5b3e42f
        assertEquals("d14a028c2a3a2bc9476102bb288234c415a2b01f828ea62ac5b3e42f", ProxyHandshake.sha224Hex(""))
    }

    @Test
    fun webSocketBinaryFrameMasksPayload() {
        val payload = bytes(0x01, 0x02, 0x03)
        val mask = bytes(0xAA, 0xBB, 0xCC, 0xDD)
        val frame = ProxyHandshake.wsBinaryFrame(payload, mask)
        // header(2) + mask(4) + payload(3)
        assertEquals(9, frame.size)
        assertEquals(0x82, frame[0].toInt() and 0xFF) // FIN + binary
        assertEquals(0x83, frame[1].toInt() and 0xFF) // masked + len 3
        assertArrayEquals(mask, frame.copyOfRange(2, 6))
        // unmasking the body restores the original payload
        val body = frame.copyOfRange(6, 9)
        val unmasked = ByteArray(3) { (body[it].toInt() xor mask[it % 4].toInt()).toByte() }
        assertArrayEquals(payload, unmasked)
    }
}
