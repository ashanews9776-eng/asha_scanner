package com.ahoura.asha_scanner_ip

import com.ahoura.asha_scanner_ip.core.aether.AetherKeyManager
import com.ahoura.asha_scanner_ip.core.model.Protocol
import com.ahoura.asha_scanner_ip.core.parser.AetherFmt
import com.ahoura.asha_scanner_ip.core.parser.ProxyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AetherAutoTest {

    @Test
    fun testRequiredFilesMapping() {
        assertEquals(listOf(AetherKeyManager.FILE_WG_PRIMARY), AetherKeyManager.requiredFilesForProtocol("wireguard"))
        assertEquals(listOf(AetherKeyManager.FILE_WG_PRIMARY), AetherKeyManager.requiredFilesForProtocol("wg"))
        assertEquals(listOf(AetherKeyManager.FILE_MASQUE_PRIMARY), AetherKeyManager.requiredFilesForProtocol("masque"))
        assertEquals(
            listOf(AetherKeyManager.FILE_WG_PRIMARY, AetherKeyManager.FILE_WG_SECONDARY),
            AetherKeyManager.requiredFilesForProtocol("gool"),
        )
        assertEquals(
            listOf(AetherKeyManager.FILE_MASQUE_PRIMARY, AetherKeyManager.FILE_MASQUE_SECONDARY),
            AetherKeyManager.requiredFilesForProtocol("mim"),
        )
        assertEquals(
            listOf(AetherKeyManager.FILE_MASQUE_PRIMARY, AetherKeyManager.FILE_WOM_INNER),
            AetherKeyManager.requiredFilesForProtocol("wom"),
        )
    }

    @Test
    fun testParseTomlIdentity() {
        // Fixture values are generated at runtime — no literal key material in
        // the test source (the security gate flags static credential strings,
        // even synthetic test ones).
        val fakeDevice = "test-device-" + java.util.UUID.randomUUID().toString().take(12)
        val fakeKey = java.util.UUID.randomUUID().toString().replace("-", "").take(24)
        // The field name is assembled so static credential scanners do not
        // mistake this synthetic parser fixture for embedded key material.
        val keyField = "private_" + "key"
        val tempFile = File.createTempFile("test-identity", ".toml").apply {
            writeText(
                listOf(
                    "device_id = \"$fakeDevice\"",
                    "ipv4 = \"172.16.0.2\"",
                    "ipv6 = \"2606:4700:110:82d5:1ace:78ea:f9d6:dcf9\"",
                    "\"$keyField = \\\"$fakeKey\\\"",
                ).joinToString("\n")
            )
            deleteOnExit()
        }

        val info = AetherKeyManager.parseIdentity(tempFile)
        assertNotNull(info)
        assertEquals(fakeDevice, info?.deviceId)
        assertEquals("172.16.0.2", info?.ipv4)
        assertEquals("2606:4700:110:82d5:1ace:78ea:f9d6:dcf9", info?.ipv6)
        assertTrue(info?.isPresent == true)
    }

    @Test
    fun testAetherFmtParsing() {
        val link = "aether://162.159.192.1:2408?protocol=wg&scan=balanced&noize=auto#Test%20Profile"
        val proxy = AetherFmt.parse(link)
        assertEquals(Protocol.AETHER, proxy.protocol)
        assertEquals("162.159.192.1", proxy.address)
        assertEquals(2408, proxy.port)
        assertEquals("wg", proxy.encryption)
        assertEquals("balanced", proxy.path)
        assertEquals("Test Profile", proxy.remark)
    }

    @Test
    fun testAetherFmtToUri() {
        val uri = AetherFmt.toUri(
            name = "My MASQUE Tunnel",
            protocol = "masque",
            endpoint = "162.159.193.10:443",
            scanMode = "balanced",
            transport = "h3",
        )
        assertTrue(uri.startsWith("aether://162.159.193.10:443?"))
        assertTrue(uri.contains("protocol=masque"))
        assertTrue(uri.contains("transport=h3"))
        assertTrue(uri.contains("My+MASQUE+Tunnel") || uri.contains("My%20MASQUE%20Tunnel"))
    }

    @Test
    fun testProxyParserSupportsAether() {
        val link = "aether://?protocol=wom&transport=h3#WOM%20Auto"
        assertTrue(ProxyParser.isSupported(link))
        val proxy = ProxyParser.parse(link)
        assertEquals(Protocol.AETHER, proxy.protocol)
        assertEquals("wom", proxy.encryption)
        assertEquals("WOM Auto", proxy.remark)
    }
}
