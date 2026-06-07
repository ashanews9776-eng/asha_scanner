package com.ahoura.asha_scanner_ip

import com.ahoura.asha_scanner_ip.core.ipsrc.IpSource
import com.ahoura.asha_scanner_ip.core.model.Protocol
import com.ahoura.asha_scanner_ip.core.output.ConfigLinkBuilder
import com.ahoura.asha_scanner_ip.core.parser.ProxyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class ScannerLogicTest {

    @Test
    fun parsesVlessWithAllFields() {
        val link = "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?encryption=none&security=tls&sni=test.com&type=ws&path=%2Fws&host=h.com&fp=chrome#My%20Node"
        val c = ProxyParser.parse(link)
        assertEquals(Protocol.VLESS, c.protocol)
        assertEquals("11111111-2222-3333-4444-555555555555", c.uuid)
        assertEquals("example.com", c.address)
        assertEquals(443, c.port)
        assertEquals("tls", c.security)
        assertEquals("test.com", c.sni)
        assertEquals("ws", c.network)
        assertEquals("/ws", c.path)
        assertEquals("h.com", c.hostHeader)
        assertEquals("chrome", c.fingerprint)
        assertEquals("My Node", c.remark)
    }

    @Test
    fun parsesTrojan() {
        val c = ProxyParser.parse("trojan://secret-pass@1.2.3.4:8443?security=tls&sni=s.com#T")
        assertEquals(Protocol.TROJAN, c.protocol)
        assertEquals("secret-pass", c.password)
        assertEquals("1.2.3.4", c.address)
        assertEquals(8443, c.port)
        assertEquals("s.com", c.sni)
    }

    @Test
    fun recoversMissingQuerySeparator() {
        // No '?' between the port and the query string.
        val c = ProxyParser.parse("vless://uuid-abc@example.com:2053security=tls&sni=x.com")
        assertEquals("example.com", c.address)
        assertEquals(2053, c.port)
        assertEquals("tls", c.security)
        assertEquals("x.com", c.sni)
    }

    @Test
    fun parsesIpv6HostInBrackets() {
        val c = ProxyParser.parse("trojan://pw@[2606:4700::1]:443?security=tls#v6")
        assertEquals("2606:4700::1", c.address)
        assertEquals(443, c.port)
    }

    @Test
    fun generatedIpsFallInsideCloudflareRanges() {
        val src = IpSource.build(useV4 = true, useV6 = false, seed = 42)
        val ips = src.stream(300).toList()
        assertEquals(300, ips.size) // unique, requested count met
        ips.forEach { ip ->
            assertTrue("$ip should be a valid IPv4", InetAddress.getByName(ip).address.size == 4)
            assertTrue("$ip not in any CF range", inAnyCfV4(ip))
        }
    }

    @Test
    fun generatesFromBundledPreciseRangeList() {
        val file = java.io.File("src/main/assets/cf_ipv4.txt")
        assertTrue("bundled cf_ipv4.txt asset must exist", file.exists())
        val ranges = file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        assertTrue("expected thousands of curated ranges", ranges.size > 1000)

        val src = IpSource.build(useV4 = true, useV6 = false, seed = 7, v4Ranges = ranges)
        val ips = src.stream(500).toList()
        assertEquals(500, ips.size)
        ips.forEach { ip ->
            assertEquals(4, InetAddress.getByName(ip).address.size)
            assertTrue("$ip not within any bundled range", inAnyRange(ip, ranges))
        }
    }

    @Test
    fun configLinkSwapsAddressAndPreservesSni() {
        val proxy = ProxyParser.parse(
            "vless://uuid-1@example.com:443?security=tls&sni=test.com&type=ws&path=%2Fp#node"
        )
        val link = ConfigLinkBuilder.withAddress(proxy, "104.16.1.2")
        assertTrue("address swapped", link.contains("@104.16.1.2:443"))
        assertFalse("original domain not left as address", link.contains("@example.com:"))
        assertTrue("existing sni preserved", link.contains("sni=test.com"))
        assertFalse("no duplicate sni", link.contains("sni=example.com"))
        assertTrue("remark tagged with ip", link.endsWith("#node-104.16.1.2"))
    }

    @Test
    fun configLinkInjectsSniWhenMissing() {
        val proxy = ProxyParser.parse("vless://uuid-1@example.com:443?security=tls#node")
        val link = ConfigLinkBuilder.withAddress(proxy, "104.16.1.2")
        assertTrue("address swapped", link.contains("@104.16.1.2:443"))
        assertTrue("sni injected from domain", link.contains("sni=example.com"))
    }

    @Test
    fun configLinkBracketsIpv6() {
        val proxy = ProxyParser.parse("trojan://pw@example.com:443?security=tls#t")
        val link = ConfigLinkBuilder.withAddress(proxy, "2606:4700::1")
        assertTrue("ipv6 bracketed", link.contains("@[2606:4700::1]:443"))
        assertTrue("sni injected", link.contains("sni=example.com"))
    }

    private fun inAnyRange(ip: String, ranges: List<String>): Boolean {
        val ipInt = ipToInt(ip)
        return ranges.any { cidr ->
            val (base, prefix) = cidr.split("/")
            val p = prefix.toInt()
            val mask = if (p == 0) 0 else (-1 shl (32 - p))
            (ipInt and mask) == (ipToInt(base) and mask)
        }
    }

    private fun inAnyCfV4(ip: String): Boolean {
        val ipInt = ipToInt(ip)
        return com.ahoura.asha_scanner_ip.core.ipsrc.CloudflareRanges.V4.any { cidr ->
            val (base, prefix) = cidr.split("/")
            val baseInt = ipToInt(base)
            val mask = if (prefix.toInt() == 0) 0 else (-1 shl (32 - prefix.toInt()))
            (ipInt and mask) == (baseInt and mask)
        }
    }

    private fun ipToInt(ip: String): Int {
        var r = 0
        for (b in InetAddress.getByName(ip).address) r = (r shl 8) or (b.toInt() and 0xFF)
        return r
    }
}
