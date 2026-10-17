package com.ahoura.asha_scanner_ip

import com.ahoura.asha_scanner_ip.core.engine.ScanSnapshot
import com.ahoura.asha_scanner_ip.core.ipsrc.TargetParser
import com.ahoura.asha_scanner_ip.core.model.AntiDpiConfig
import com.ahoura.asha_scanner_ip.core.model.ProbeMode
import com.ahoura.asha_scanner_ip.core.model.ScanConfig
import com.ahoura.asha_scanner_ip.core.model.ScanResult
import com.ahoura.asha_scanner_ip.core.net.AntiDpiOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class SenPaiScannerFeaturesTest {

    @Test
    fun antiDpiRecipeDefaultsAndValidation() {
        val cfg = AntiDpiConfig.suggestedValues()
        assertTrue(cfg.enabled)
        assertNull("Default recipe must validate without errors", cfg.validate())

        val recipe = cfg.parseRecipe()
        assertEquals(104, recipe.firstChunk)
        assertEquals(114, recipe.nextChunk)
        assertEquals(1L, recipe.delayMs)
        assertEquals(11, recipe.maxSplit)

        val invalidJson = AntiDpiConfig(enabled = true, finalmask = "not-json")
        assertNotNull("Invalid JSON must produce validation error", invalidJson.validate())
    }

    @Test
    fun antiDpiOutputStreamFragmentsClientHello() {
        val writtenChunks = mutableListOf<Int>()
        val underlyingOut = object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                writtenChunks.add(len)
                super.write(b, off, len)
            }
        }

        val config = AntiDpiConfig.suggestedValues()
        val antiDpiStream = AntiDpiOutputStream(underlyingOut, config)

        // Fabricate a 500-byte TLS ClientHello record:
        // Byte 0: 0x16 (Handshake)
        // Byte 1-2: 0x03, 0x01 (TLS 1.0 record version)
        // Byte 3-4: length 0x01EF (495 bytes payload)
        // Byte 5: 0x01 (ClientHello)
        val dummyHello = ByteArray(500) { it.toByte() }
        dummyHello[0] = 0x16.toByte()
        dummyHello[1] = 0x03.toByte()
        dummyHello[2] = 0x01.toByte()
        dummyHello[3] = 0x01.toByte()
        dummyHello[4] = 0xEF.toByte()
        dummyHello[5] = 0x01.toByte()

        antiDpiStream.write(dummyHello, 0, dummyHello.size)
        antiDpiStream.flush()

        // Verify the flight was fragmented
        assertTrue("ClientHello must be split into multiple chunks", writtenChunks.size > 1)
        assertEquals("First chunk must be 104 bytes per recipe", 104, writtenChunks[0])
        assertEquals("Second chunk must be 114 bytes per recipe", 114, writtenChunks[1])
        assertEquals("Third chunk must be 114 bytes", 114, writtenChunks[2])
        assertEquals("Fourth chunk must be 114 bytes", 114, writtenChunks[3])
        assertEquals("Fifth chunk must be remainder (54 bytes)", 54, writtenChunks[4])
        assertEquals("Total written bytes must match original 500 bytes", 500, writtenChunks.sum())

        // Verify that subsequent writes (e.g. application data) are not fragmented
        val appData = ByteArray(100) { 0xFF.toByte() }
        writtenChunks.clear()
        antiDpiStream.write(appData, 0, appData.size)
        assertEquals(1, writtenChunks.size)
        assertEquals(100, writtenChunks[0])
    }

    @Test
    fun targetParserParsesIpsCidrsRangesAndDomains() = runBlocking {
        val input = """
            # Comments should be ignored
            1.1.1.1, 1.0.0.1:443
            192.168.1.1-5
            10.0.0.0/30
            example.com
            invalid...domain...123
        """.trimIndent()

        val preview = TargetParser.parse(input, resolveDomains = false)
        assertTrue(preview.count > 0)
        assertTrue("Contains literal 1.1.1.1", preview.ips.contains("1.1.1.1"))
        assertTrue("Contains literal 1.0.0.1 (port stripped)", preview.ips.contains("1.0.0.1"))
        assertTrue("Contains range start 192.168.1.1", preview.ips.contains("192.168.1.1"))
        assertTrue("Contains range end 192.168.1.5", preview.ips.contains("192.168.1.5"))
        assertTrue("Contains CIDR host 10.0.0.1", preview.ips.contains("10.0.0.1"))
        assertTrue("Skips invalid domains", preview.skipped.any { it.contains("invalid") })
    }

    @Test
    fun scanSnapshotSerializesAndRestoresCorrectly() {
        val config = ScanConfig(
            mode = ProbeMode.HTTP,
            timeoutMs = 6000L,
            concurrency = 25,
            gentle = true,
            phase2Only = true,
            pastedTargets = "104.16.1.1, 104.16.1.2",
            ports = listOf(443, 8443),
        )

        val healthy = listOf(
            ScanResult(
                ip = "104.16.1.1",
                port = 443,
                mode = ProbeMode.HTTP,
                latenciesMs = listOf(45L, 50L),
                attempts = 2,
                tlsOk = true,
                httpStatus = 200,
                colo = "FRA",
                healthy = true,
            ),
            ScanResult(
                ip = "104.16.1.2",
                port = 8443,
                mode = ProbeMode.HTTP,
                latenciesMs = listOf(60L),
                attempts = 1,
                speedTested = true,
                throughputBytesPerSec = 5_000_000.0,
                healthy = true,
            )
        )

        val remainingTasks = listOf("104.16.1.3" to 443, "104.16.1.4" to 443)

        val snapshot = ScanSnapshot(
            config = config,
            healthyResults = healthy,
            testedCount = 10,
            totalCount = 12,
            remainingTasks = remainingTasks,
        )

        val json = snapshot.toJson()
        val restored = ScanSnapshot.fromJson(json)

        assertNotNull(restored)
        assertEquals(10, restored!!.testedCount)
        assertEquals(12, restored.totalCount)
        assertEquals(2, restored.healthyResults.size)
        assertEquals("104.16.1.1", restored.healthyResults[0].ip)
        assertEquals("FRA", restored.healthyResults[0].colo)
        assertEquals(5_000_000.0, restored.healthyResults[1].throughputBytesPerSec, 0.001)
        assertEquals(2, restored.remainingTasks.size)
        assertEquals("104.16.1.3", restored.remainingTasks[0].first)
        assertTrue(restored.config.gentle)
        assertTrue(restored.config.phase2Only)
        assertEquals("104.16.1.1, 104.16.1.2", restored.config.pastedTargets)
    }

    @Test
    fun gentleModeAppliesWorkersAndTimeoutConstraints() {
        val cfg = ScanConfig(gentle = true, concurrency = 100, timeoutMs = 2000L)
        val effectiveWorkers = if (cfg.gentle) cfg.concurrency.coerceIn(1, 25) else cfg.concurrency
        val effectiveTimeout = if (cfg.gentle) maxOf(cfg.timeoutMs, 6000L) else cfg.timeoutMs

        assertEquals(25, effectiveWorkers)
        assertEquals(6000L, effectiveTimeout)
    }
}
