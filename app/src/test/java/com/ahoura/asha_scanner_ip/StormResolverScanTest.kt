package com.ahoura.asha_scanner_ip

import com.ahoura.asha_scanner_ip.core.ipsrc.IrRangeTable
import com.ahoura.asha_scanner_ip.core.storm.StormResolverScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Locks the WhiteDNS-ported scan engine pieces: the round-robin chunker, the
 * WD_SCAN telemetry parser, the persistent result store, and the geoip-derived
 * Iran range table used to tag the big pool.
 */
class StormResolverScanTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── Chunker ──────────────────────────────────────────────────────────────

    @Test
    fun chunkerSplitsRoundRobinExactly() {
        val chunks = StormResolverScan.chunkResolversRoundRobin(listOf("1", "2", "3", "4", "5"), 2)
        assertEquals(2, chunks.size)
        // Round-robin: indexes 0,2,4 in chunk 0; 1,3 in chunk 1.
        assertEquals(listOf("1", "3", "5"), chunks[0])
        assertEquals(listOf("2", "4"), chunks[1])
    }

    @Test
    fun chunkerHandlesEdgeCases() {
        assertTrue(StormResolverScan.chunkResolversRoundRobin(emptyList(), 8).isEmpty())
        assertTrue(StormResolverScan.chunkResolversRoundRobin(listOf(" ", "", "  "), 8).isEmpty())
        // workerCount larger than pool collapses to pool size.
        val single = StormResolverScan.chunkResolversRoundRobin(listOf("1.1.1.1"), 8)
        assertEquals(1, single.size)
        assertEquals(listOf("1.1.1.1"), single[0])
        // Trims and drops blanks but preserves membership.
        val chunks = StormResolverScan.chunkResolversRoundRobin(listOf(" a ", "", "b"), 2)
        assertEquals(listOf("a", "b"), chunks.flatMap { it })
    }

    @Test
    fun chunkerCoversEveryResolverExactlyOnce() {
        val pool = (1..1000).map { "$it.0.0.$it" }
        val chunks = StormResolverScan.chunkResolversRoundRobin(pool, 7)
        assertEquals(7, chunks.size)
        assertEquals(pool.size, chunks.sumOf { it.size })
        assertEquals(pool.toSet(), chunks.flatten().toSet())
    }

    // ── Telemetry parser ─────────────────────────────────────────────────────

    @Test
    fun parsesValidRejectedComplete() {
        val valid = StormResolverScan.parseScanLine("WD_SCAN event=valid resolver=178.22.122.100")
        assertTrue(valid is StormResolverScan.ScanTelemetry.Valid)
        assertEquals("178.22.122.100", (valid as StormResolverScan.ScanTelemetry.Valid).resolver)

        val rejected = StormResolverScan.parseScanLine("WD_SCAN event=rejected resolver=10.0.0.1")
        assertTrue(rejected is StormResolverScan.ScanTelemetry.Rejected)
        assertEquals("10.0.0.1", (rejected as StormResolverScan.ScanTelemetry.Rejected).resolver)

        val complete = StormResolverScan.parseScanLine("WD_SCAN event=complete total=598 valid=310 rejected=288")
        assertTrue(complete is StormResolverScan.ScanTelemetry.Complete)
        complete as StormResolverScan.ScanTelemetry.Complete
        assertEquals(598, complete.total)
        assertEquals(310, complete.valid)
        assertEquals(288, complete.rejected)
    }

    @Test
    fun stripsAnsiAndIgnoresNoise() {
        val withAnsi = StormResolverScan.parseScanLine("${27.toChar()}[32mWD_SCAN event=valid resolver=1.2.3.4${27.toChar()}[0m")
        assertEquals("1.2.3.4", (withAnsi as StormResolverScan.ScanTelemetry.Valid).resolver)

        assertNull(StormResolverScan.parseScanLine("just some log line about mtu"))
        assertNull(StormResolverScan.parseScanLine("WD_SCAN event=unknown resolver=x"))
        assertNull(StormResolverScan.parseScanLine(""))
    }

    @Test
    fun parsesProgressEvents() {
        val progress = StormResolverScan.parseScanLine("WD_PROGRESS phase=mtu percent=42")
        assertTrue(progress is StormResolverScan.ScanTelemetry.Progress)
        progress as StormResolverScan.ScanTelemetry.Progress
        assertEquals("mtu", progress.phase)
        assertEquals(42, progress.percent)
    }

    // ── Result store ─────────────────────────────────────────────────────────

    @Test
    fun resultStoreRoundtripsAndClears() {
        val file = tmp.newFile("valid.txt")
        val store = StormResolverScan.ScannerResultStore(file)
        assertTrue(store.load().isEmpty())

        store.save(setOf("178.22.122.100", "8.8.8.8", "1.1.1.1"))
        assertEquals(setOf("178.22.122.100", "8.8.8.8", "1.1.1.1"), store.load())

        // Overwrite semantics: a later save replaces, not appends.
        store.save(setOf("9.9.9.9"))
        assertEquals(setOf("9.9.9.9"), store.load())

        store.clear()
        assertTrue(store.load().isEmpty())
    }

    // ── Scan TOML ────────────────────────────────────────────────────────────

    @Test
    fun scanTomlIsInScanMode() {
        val toml = StormResolverScan.renderScanToml("ns.example.com", "key", 1, "stormdns")
        assertTrue(toml.contains("STARTUP_MODE = \"resolvers\""))
        assertTrue(toml.contains("LISTEN_PORT = 0"))
        assertTrue(toml.contains("MTU_TEST_PARALLELISM_RESOLVERS = 1"))
        assertTrue(toml.contains("DOMAINS = [\"ns.example.com\"]"))
        assertFalse("scan mode must not serve SOCKS", toml.contains("LISTEN_PORT = 10853"))
    }

    // ── IrRangeTable ─────────────────────────────────────────────────────────

    /** Hand-encodes a geoip.dat with two entries (IR + US) to test the parser. */
    private fun syntheticGeoip(): ByteArray {
        fun varint(v: Int): ByteArray {
            if (v < 0x80) return byteArrayOf(v.toByte())
            return byteArrayOf(((v and 0x7F) or 0x80).toByte(), (v ushr 7).toByte())
        }
        fun field(num: Int, payload: ByteArray): ByteArray =
            byteArrayOf(((num shl 3) or 2).toByte()) + varint(payload.size) + payload

        // uint32 fields use wire type 0 (varint), matching the real geoip format.
        fun varintField(num: Int, value: Int): ByteArray =
            byteArrayOf(((num shl 3) or 0).toByte()) + varint(value)

        fun cidr(ip: ByteArray, prefix: Int): ByteArray =
            field(1, ip) + varintField(2, prefix)

        val ir = field(1, "IR".toByteArray()) +
            field(2, cidr(byteArrayOf(5.toByte(), 202.toByte(), 100.toByte(), 0), 24)) +
            field(2, cidr(byteArrayOf(178.toByte(), 22.toByte(), 122.toByte(), 0), 24))
        val us = field(1, "US".toByteArray()) +
            field(2, cidr(byteArrayOf(8, 8, 8, 0), 24))
        return field(1, ir) + field(1, us)
    }

    @Test
    fun irRangeTableParsesGeoipAndMatches() {
        val table = IrRangeTable.load(syntheticGeoip())
        assertNotNull(table)
        assertEquals(2, table!!.size)

        assertTrue(table.containsIpv4("5.202.100.55"))
        assertTrue(table.containsIpv4("178.22.122.100"))
        // Inside the /24 but on the network address is still a match.
        assertTrue(table.containsIpv4("5.202.100.0"))
        // Outside the prefix fails.
        assertFalse(table.containsIpv4("5.202.101.1"))
        assertFalse(table.containsIpv4("8.8.8.8"))          // US entry, not IR
        assertFalse(table.containsIpv4("not-an-ip"))
        assertFalse(table.containsIpv4("1.1.1.999"))
    }
}
