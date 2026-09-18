package com.ahoura.asha_scanner_ip

import com.ahoura.asha_scanner_ip.core.dns.DnsProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the RFC 1035 wire format used by the DNS Tuner. A resolver silently
 * drops a query that is off by a single byte, and a lenient answer check would
 * rank hijacking middleboxes as "fast" — both failure modes these vectors guard.
 */
class DnsProbeTest {

    @Test
    fun queryHasCorrectHeaderAndQuestion() {
        val q = DnsProbe.buildQuery(0x1234, "digikala.com")
        // 12 header + 1+8 "digikala" + 1+3 "com" + 1 root + 4 type/class
        assertEquals(12 + 9 + 4 + 1 + 4, q.size)
        assertEquals(0x12, q[0].toInt() and 0xFF)
        assertEquals(0x34, q[1].toInt() and 0xFF)
        assertEquals(0x01, q[2].toInt() and 0xFF) // RD set
        assertEquals(0x00, q[3].toInt() and 0xFF)
        // qdcount = 1, all other counts 0
        assertEquals(0, q[4].toInt()); assertEquals(1, q[5].toInt())
        assertEquals(0, q[6].toInt()); assertEquals(0, q[7].toInt())
        assertEquals(0, q[8].toInt()); assertEquals(0, q[9].toInt())
        assertEquals(0, q[10].toInt()); assertEquals(0, q[11].toInt())
        // QNAME labels
        assertEquals(8, q[12].toInt())
        assertEquals("digikala", String(q, 13, 8, Charsets.US_ASCII))
        assertEquals(3, q[21].toInt())
        assertEquals("com", String(q, 22, 3, Charsets.US_ASCII))
        // root + type A + class IN
        assertEquals(0, q[25].toInt())
        assertEquals(0, q[26].toInt()); assertEquals(1, q[27].toInt())
        assertEquals(0, q[28].toInt()); assertEquals(1, q[29].toInt())
    }

    @Test
    fun answerWithARecordIsAccepted() {
        // Valid response: id 0xABCD, QR+RD, RCODE 0, 1 answer, A record for
        // the asked name (compressed pointer to offset 12).
        val answer = byteArrayOf(
            0xAB.toByte(), 0xCD.toByte(),      // id
            0x81.toByte(), 0x80.toByte(),      // QR=1 RD=1 RCODE=0
            0x00, 0x01,                        // qdcount
            0x00, 0x01,                        // ancount
            0x00, 0x00,                        // nscount
            0x00, 0x00,                        // arcount
            0x08, 0x64, 0x69, 0x67, 0x69, 0x6B, 0x61, 0x6C, 0x61, 0x03, 0x63, 0x6F, 0x6D, 0x00, // qname
            0x00, 0x01, 0x00, 0x01,            // qtype/qclass
            0xC0.toByte(), 0x0C,               // name pointer
            0x00, 0x01,                        // type A
            0x00, 0x01,                        // class IN
            0x00, 0x00, 0x00, 0x3C,            // ttl 60
            0x00, 0x04,                        // rdlength
            104, 21, 1, 2,                     // rdata
        )
        assertTrue(DnsProbe.isAnswer(answer, 0xABCD))
    }

    @Test
    fun mismatchedIdIsRejected() {
        val answer = byteArrayOf(0xAB.toByte(), 0xCD.toByte()) + tailWithARecord()
        // Same packet, probed under a different transaction id.
        assertFalse(DnsProbe.isAnswer(answer, 0x1111))
    }

    @Test
    fun queryNotResponseIsRejected() {
        // QR bit clear -> this is a query echo, not an answer.
        val notAResponse = byteArrayOf(
            0xAB.toByte(), 0xCD.toByte(),
            0x01, 0x00,                        // QR=0
            0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
        ) + tailWithARecord().drop(2)
        assertFalse(DnsProbe.isAnswer(notAResponse, 0xABCD))
    }

    @Test
    fun serverFailureIsRejected() {
        // RCODE=2 (SERVFAIL) with zero answers — fast, but useless.
        val servfail = byteArrayOf(
            0xAB.toByte(), 0xCD.toByte(),
            0x81.toByte(), 0x82.toByte(),      // RCODE=2
            0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        assertFalse(DnsProbe.isAnswer(servfail, 0xABCD))
    }

    @Test
    fun nxDomainWithNoAnswersIsRejected() {
        val nxdomain = byteArrayOf(
            0xAB.toByte(), 0xCD.toByte(),
            0x81.toByte(), 0x83.toByte(),      // RCODE=3
            0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        assertFalse(DnsProbe.isAnswer(nxdomain, 0xABCD))
    }

    @Test
    fun truncatedGarbageIsRejected() {
        val garbage = ByteArray(8) { 0xFF.toByte() }
        assertFalse(DnsProbe.isAnswer(garbage, 0xABCD))
        assertFalse(DnsProbe.isAnswer(ByteArray(0), 0xABCD))
    }

    @Test
    fun testIsValidIp() {
        assertTrue(DnsProbe.isValidIp("1.1.1.1"))
        assertTrue(DnsProbe.isValidIp("8.8.8.8"))
        assertTrue(DnsProbe.isValidIp("178.22.122.100"))
        assertTrue(DnsProbe.isValidIp("2606:4700:4700::1111"))

        assertFalse(DnsProbe.isValidIp("256.0.0.1"))
        assertFalse(DnsProbe.isValidIp("1.1.1.-1"))
        assertFalse(DnsProbe.isValidIp("1.1.1"))
        assertFalse(DnsProbe.isValidIp("1.1.1.1.1"))
        assertFalse(DnsProbe.isValidIp("not-an-ip"))
        assertFalse(DnsProbe.isValidIp(""))
        assertFalse(DnsProbe.isValidIp("   "))
    }

    @Test
    fun testParseResolverLine() {
        // Comment and blank lines return null
        assertEquals(null, DnsProbe.parseResolverLine("# comment"))
        assertEquals(null, DnsProbe.parseResolverLine("   "))
        assertEquals(null, DnsProbe.parseResolverLine(""))

        // Category|Name|IP
        val irResolver = DnsProbe.parseResolverLine("ANTI_SANCTION|Shecan 1|178.22.122.100")
        assertEquals("Shecan 1", irResolver?.name)
        assertEquals("178.22.122.100", irResolver?.ip)
        assertEquals(DnsProbe.ResolverCategory.ANTI_SANCTION, irResolver?.category)

        val irAlias = DnsProbe.parseResolverLine("IR|Electro 1|78.157.42.101")
        assertEquals("Electro 1", irAlias?.name)
        assertEquals(DnsProbe.ResolverCategory.ANTI_SANCTION, irAlias?.category)

        val globalResolver = DnsProbe.parseResolverLine("GLOBAL|Cloudflare Primary|1.1.1.1")
        assertEquals("Cloudflare Primary", globalResolver?.name)
        assertEquals(DnsProbe.ResolverCategory.GLOBAL, globalResolver?.category)

        val customResolver = DnsProbe.parseResolverLine("CUSTOM|My DNS|192.168.1.1")
        assertEquals("My DNS", customResolver?.name)
        assertEquals(DnsProbe.ResolverCategory.CUSTOM, customResolver?.category)

        // Name|IP (defaults to GLOBAL)
        val nameIp = DnsProbe.parseResolverLine("Google DNS|8.8.8.8")
        assertEquals("Google DNS", nameIp?.name)
        assertEquals("8.8.8.8", nameIp?.ip)
        assertEquals(DnsProbe.ResolverCategory.GLOBAL, nameIp?.category)

        // Bare IP (defaults to CUSTOM, name = IP)
        val bareIp = DnsProbe.parseResolverLine("9.9.9.9")
        assertEquals("9.9.9.9", bareIp?.name)
        assertEquals("9.9.9.9", bareIp?.ip)
        assertEquals(DnsProbe.ResolverCategory.CUSTOM, bareIp?.category)

        // Invalid IPs should return null
        assertEquals(null, DnsProbe.parseResolverLine("Bad IP|999.999.999.999"))
        assertEquals(null, DnsProbe.parseResolverLine("GLOBAL|Bad IP|invalid"))
        assertEquals(null, DnsProbe.parseResolverLine("too|many|parts|1.1.1.1"))
    }

    /** Answer-section tail: type A, class IN, ttl, rdlength 4, rdata. */
    private fun tailWithARecord(): ByteArray = byteArrayOf(
        0xC0.toByte(), 0x0C,                   // name compression pointer
        0x00, 0x01,                            // type A
        0x00, 0x01,                            // class IN
        0x00, 0x00, 0x00, 0x3C,                // ttl
        0x00, 0x04,                            // rdlength
        104, 21, 1, 2,                         // rdata
    )
}

