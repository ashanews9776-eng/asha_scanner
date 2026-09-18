package com.ahoura.asha_scanner_ip

import com.ahoura.asha_scanner_ip.core.model.Protocol
import com.ahoura.asha_scanner_ip.core.model.ProxyConfig
import com.ahoura.asha_scanner_ip.core.parser.ProxyParser
import com.ahoura.asha_scanner_ip.core.validator.XrayConfigBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StormDnsTest {

    @Test
    fun testExportAndParseStormDnsRoundtrip() {
        val originalName = "Iran Storm Server"
        val domain = "ns1.freedns.ir"
        val key = "secret_encryption_key_123"
        val method = 1

        val exportedLink = ProxyParser.exportStormDns(
            name = originalName,
            domain = domain,
            encryptionKey = key,
            encryptionMethod = method,
        )

        assertTrue(exportedLink.startsWith("stormdns://"))
        assertTrue(ProxyParser.isSupported(exportedLink))

        val parsed = ProxyParser.parse(exportedLink)
        assertEquals(Protocol.STORMDNS, parsed.protocol)
        assertEquals(domain, parsed.address)
        assertEquals(53, parsed.port)
        assertEquals(key, parsed.password)
        assertEquals("1", parsed.encryption)
        assertEquals(originalName, parsed.remark)
    }

    @Test
    fun testParseMasterDnsAndCottenDnsSchemes() {
        val link = ProxyParser.exportStormDns("Test", "ns2.example.com", "key", 2)
        val masterDnsLink = link.replace("stormdns://", "masterdns://")
        val cottenDnsLink = link.replace("stormdns://", "cottendns://")

        val p1 = ProxyParser.parse(masterDnsLink)
        assertEquals(Protocol.STORMDNS, p1.protocol)
        assertEquals("ns2.example.com", p1.address)

        val p2 = ProxyParser.parse(cottenDnsLink)
        assertEquals(Protocol.STORMDNS, p2.protocol)
        assertEquals("ns2.example.com", p2.address)
    }

    @Test
    fun testXrayConfigBuilderWithStormDns() {
        val proxy = ProxyConfig(
            protocol = Protocol.STORMDNS,
            address = "ns.tunnel.org",
            port = 53,
            password = "testkey",
            encryption = "1",
            remark = "DNS Tunnel Test",
        )

        val config = XrayConfigBuilder.buildClientVpnConfig(
            proxy = proxy,
            bypassIran = true,
            dnsServers = listOf("1.1.1.1"),
        )

        val outbounds = config.getJSONArray("outbounds")
        val proxyOutbound = outbounds.getJSONObject(0)
        assertEquals("proxy", proxyOutbound.getString("tag"))
        assertEquals("socks", proxyOutbound.getString("protocol"))

        val settings = proxyOutbound.getJSONObject("settings")
        val servers = settings.getJSONArray("servers")
        val server = servers.getJSONObject(0)
        assertEquals("127.0.0.1", server.getString("address"))
        assertEquals(com.ahoura.asha_scanner_ip.core.storm.StormDnsProcessManager.SOCKS_PORT, server.getInt("port"))
    }

    @Test
    fun testParseUserSampleConfigs() {
        val cottenLink = "cottendns://eyJzY2hlbWEiOiJ3aGl0ZWRucy5wcm9maWxlIiwidmVyc2lvbiI6MSwiaW1wb3J0X3R5cGUiOiJjb3R0ZW5kbnMiLCJwcm9maWxlIjp7Im5hbWUiOiJ0Lm1lL0JsdWVLbmlnaHRfTmV0Iiwic2VydmVyIjp7ImRvbWFpbiI6InYubmlnaHR5ZGlhbmF3LmRwZG5zLm9yZyIsImRvbWFpbnMiOlsidi5uaWdodHlkaWFuYXcuZHBkbnMub3JnIl0sImVuY3J5cHRpb25fa2V5IjoiNThlMDc4Y2NhZjMxOTJlYyIsImVuY3J5cHRpb25fbWV0aG9kIjozfX19"
        val stormLink = "stormdns://eyJzY2hlbWEiOiJ3aGl0ZWRucy5wcm9maWxlIiwidmVyc2lvbiI6MSwicHJvZmlsZSI6eyJuYW1lIjoidC5tZVwvV2hpdGVETlMgIPCfh7nwn4e3ICAgdGh4IHRvIENvcmVmb3JnZSIsInNlcnZlciI6eyJkb21haW4iOiJ2LmFub255bW91cy5vYnNlcnZlciIsImVuY3J5cHRpb25fa2V5IjoiYjI3NTAzOTE5OWIxYzhjOSIsImVuY3J5cHRpb25fbWV0aG9kIjozfX19"

        val pCotten = ProxyParser.parse(cottenLink)
        assertEquals(Protocol.STORMDNS, pCotten.protocol)
        assertEquals("v.nightydianaw.dpdns.org", pCotten.address)
        assertEquals("58e078ccaf3192ec", pCotten.password)
        assertEquals("3", pCotten.encryption)
        assertEquals("t.me/BlueKnight_Net", pCotten.remark)
        assertTrue(pCotten.isCottenDns())
        assertEquals("cottendns", pCotten.dnsEngine())

        val pStorm = ProxyParser.parse(stormLink)
        assertEquals(Protocol.STORMDNS, pStorm.protocol)
        assertEquals("v.anonymous.observer", pStorm.address)
        assertEquals("b275039199b1c8c9", pStorm.password)
        assertEquals("3", pStorm.encryption)
        assertEquals("t.me/WhiteDNS  \uD83C\uDDF9\uD83C\uDDF7   thx to Coreforge", pStorm.remark)
        assertEquals("stormdns", pStorm.dnsEngine())
    }

    @Test(expected = ProxyParser.ParseException::class)
    fun testInvalidBase64PayloadThrows() {
        ProxyParser.parse("stormdns://!not_valid_base64!")
    }
}
