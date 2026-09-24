package com.ahoura.asha_scanner_ip

import com.ahoura.asha_scanner_ip.core.storm.DnsTunePreset
import com.ahoura.asha_scanner_ip.core.storm.StormAutoTune
import com.ahoura.asha_scanner_ip.core.storm.StormAutoTunePresets
import com.ahoura.asha_scanner_ip.core.storm.renderStormToml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the WhiteDNS-ported MTU preset table and the TOML that reaches the
 * native binary: a mistyped preset silently degrades the DNS tunnel, and the
 * tuner is only as trustworthy as the values it feeds the process.
 */
class StormAutoTuneTest {

    @Test
    fun presetsHaveUniqueIdsAndLabels() {
        assertEquals(StormAutoTunePresets.all.size, StormAutoTunePresets.all.map { it.id }.distinct().size)
        assertEquals(StormAutoTunePresets.all.size, StormAutoTunePresets.all.map { it.label }.distinct().size)
    }

    @Test
    fun stableSubsetIsMajority() {
        assertTrue("expected >=7 stable presets", StormAutoTunePresets.stable.size >= 7)
        assertTrue(
            "aggressive must be a strict subset",
            StormAutoTunePresets.all.containsAll(StormAutoTunePresets.stable),
        )
    }

    @Test
    fun everyPresetHasSaneNumericFields() {
        StormAutoTunePresets.all.forEach { p ->
            assertTrue("${p.id} minUp>maxUp", p.minUploadMtu in 20..p.maxUploadMtu)
            assertTrue("${p.id} minDown>maxDown", p.minDownloadMtu in 100..p.maxDownloadMtu)
            assertTrue("${p.id} bad timeout", p.resolverTimeoutSeconds in 1.0..10.0)
            assertTrue("${p.id} bad fragment store", p.fragmentStoreCapacity in 50..2048)
            assertTrue("${p.id} bad up dup", p.uploadDuplication in 0..40)
            assertTrue("${p.id} bad down dup", p.downloadDuplication in 0..40)
            assertTrue("${p.id} bad compression", p.uploadCompression in 0..2)
            assertTrue("${p.id} bad compression", p.downloadCompression in 0..2)
        }
    }

    @Test
    fun byIdRoundtrip() {
        assertNotNull(StormAutoTunePresets.byId("iran-average"))
        assertNull(StormAutoTunePresets.byId("no-such-preset"))
    }

    @Test
    fun defaultTomlMatchesPreviousHardcodedValues() {
        val toml = renderStormToml(" ns1.example.com ", " key ", 1, 10853, "stormdns", null)
        assertTrue(toml.contains("DOMAINS = [\"ns1.example.com\"]"))
        assertTrue(toml.contains("MIN_UPLOAD_MTU = 40"))
        assertTrue(toml.contains("MAX_UPLOAD_MTU = 140"))
        assertTrue(toml.contains("MIN_DOWNLOAD_MTU = 300"))
        assertTrue(toml.contains("MAX_DOWNLOAD_MTU = 3000"))
        assertTrue(toml.contains("UPLOAD_PACKET_DUPLICATION_COUNT = 0"))
        assertTrue(toml.contains("UPLOAD_COMPRESSION_TYPE = 1"))
        assertTrue(toml.contains("DNS_RESPONSE_FRAGMENT_STORE_CAPACITY = 256"))
        assertFalse("cottendns extras must not leak into stormdns", toml.contains("FAST_CONNECT"))
    }

    @Test
    fun presetTomlCarriesPresetValues() {
        val preset = StormAutoTunePresets.byId("iran-download-heavy")
        assertNotNull(preset)
        val toml = renderStormToml("ns1.example.com", "k", 1, 10853, "stormdns", preset)
        assertTrue(toml.contains("MIN_UPLOAD_MTU = 104"))
        assertTrue(toml.contains("MAX_UPLOAD_MTU = 139"))
        assertTrue(toml.contains("MIN_DOWNLOAD_MTU = 394"))
        assertTrue(toml.contains("MAX_DOWNLOAD_MTU = 1000"))
        assertTrue(toml.contains("UPLOAD_PACKET_DUPLICATION_COUNT = 8"))
        assertTrue(toml.contains("DOWNLOAD_PACKET_DUPLICATION_COUNT = 30"))
    }

    @Test
    fun cottendnsTomlAddsEngineExtras() {
        val toml = renderStormToml("ns1.example.com", "k", 2, 10853, "cottendns", null)
        assertTrue(toml.contains("FAST_CONNECT = true"))
        assertTrue(toml.contains("QUERY_TYPES = [\"TXT\"]"))
    }

    @Test
    fun hostValidationRejectsNonPublicTargets() {
        try {
            StormAutoTune.validatePublicHost("127.0.0.1")
            org.junit.Assert.fail("loopback accepted")
        } catch (_: IllegalArgumentException) {
        }
        try {
            StormAutoTune.validatePublicHost("192.168.1.10")
            org.junit.Assert.fail("private range accepted")
        } catch (_: IllegalArgumentException) {
        }
        try {
            StormAutoTune.validatePublicHost("100.64.0.5")
            org.junit.Assert.fail("CGNAT range accepted")
        } catch (_: IllegalArgumentException) {
        }
        // Public literal passes without DNS.
        StormAutoTune.validatePublicHost("8.8.8.8")
    }

    @Test
    fun dataClassCopySemanticsSurvive() {
        // The runner relies on presets being immutable value objects.
        val a = DnsTunePreset("x", "X", 1, 2, 3, 4, 2.5, 256, 0, 0, 2, 2)
        val b = a.copy(aggressive = true)
        assertEquals(a.id, b.id)
        assertTrue(b.aggressive)
        assertFalse(a.aggressive)
    }
}
