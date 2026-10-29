package com.ahoura.asha_scanner_ip

import com.ahoura.asha_scanner_ip.core.aether.AetherCliConfig
import com.ahoura.asha_scanner_ip.core.aether.AetherCliSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the v2.4.0 CLI flag surface: flag names are the release's exact
 * contract (`--api-address` NOT the removed `--enroll-address`; no
 * `--no-fragment`/`--api-fragment`), optional fields emit nothing when unset,
 * and fragmentation is OFF unless explicitly enabled — all per the Aether
 * v2.4.0 release notes.
 */
class AetherCliConfigTest {

    private fun flags(s: AetherCliSettings): List<String> = AetherCliConfig.buildArgs(s)

    @Test
    fun minimalBuildHasCoreFlagsOnly() {
        val args = flags(
            AetherCliSettings(
                protocol = "wireguard",
                socksPort = 10822,
                identityFile = "/data/aether-v240.toml",
            )
        )
        assertEquals(
            listOf(
                "--config", "/data/aether-v240.toml",
                "--bind", "127.0.0.1:10822",
                "--protocol", "wireguard",
                "--log-level", "info",
            ),
            args,
        )
        // v2.4.0: fragmentation is default-OFF; no legacy flags leak.
        assertFalse(args.contains("--fragment"))
        assertFalse(args.contains("--no-fragment"))
        assertFalse(args.contains("--api-fragment"))
        assertFalse(args.contains("--enroll-address"))
    }

    @Test
    fun fragmentFlagCarriesSizeAndDelayOnlyWhenSet() {
        val base = AetherCliSettings(protocol = "masque", socksPort = 10822, identityFile = "x", fragment = true)
        assertTrue(flags(base).contains("--fragment"))
        assertFalse(flags(base).contains("--fragment-size"))

        val tuned = base.copy(fragmentSize = 8, fragmentDelay = 12)
        val args = flags(tuned)
        assertTrue(args.containsAll(listOf("--fragment", "--fragment-size", "8", "--fragment-delay", "12")))
    }

    @Test
    fun optionalFieldsEmitNothingWhenBlank() {
        val args = flags(
            AetherCliSettings(
                protocol = "masque", socksPort = 10822, identityFile = "x",
                upstream = "", peer = "", apiAddress = "", apiHost = "",
                apiSni = "", masqueSni = "", team = "",
                tlsGroups = "", tlsKeyShares = "", tlsSigalgs = "",
                tls13Ciphers = "", tlsCertCompression = "", tlsAlps = "",
            )
        )
        listOf(
            "--upstream", "--peer", "--api-address", "--api-host",
            "--api-sni", "--masque-sni", "--team", "--tls-groups",
            "--tls-key-shares", "--tls-sigalgs", "--tls13-ciphers",
            "--tls-cert-compression", "--tls-alps",
        ).forEach { flag -> assertFalse("unexpected $flag", args.contains(flag)) }
    }

    @Test
    fun v240FieldsMapToExactReleaseFlags() {
        val args = flags(
            AetherCliSettings(
                protocol = "masque", socksPort = 10822, identityFile = "x",
                apiAddress = "api.devices.cloudflare.com",
                apiHost = "api.host.example",
                apiSni = "api.sni.example",
                masqueSni = "www.cloudflare.com",
                team = "org-name",
                upstream = "socks5://127.0.0.1:1080",
                peer = "162.159.199.238:443",
                tlsGroups = "x25519:P-256",
                tlsKeyShares = "x25519",
                tlsSigalgs = "rsa_pss_rsae_sha256",
                tls13Ciphers = "TLS_AES_128_GCM_SHA256",
                tlsCertCompression = "brotli",
                tlsAlps = "new",
                enableGreaseSigalgs = true,
                enableEchGrease = true,
                tlsVerify = true,
            )
        )
        fun valueAfter(flag: String): String =
            args[args.indexOf(flag) + 1]

        assertEquals("api.devices.cloudflare.com", valueAfter("--api-address"))
        assertEquals("api.host.example", valueAfter("--api-host"))
        assertEquals("api.sni.example", valueAfter("--api-sni"))
        assertEquals("www.cloudflare.com", valueAfter("--masque-sni"))
        assertEquals("org-name", valueAfter("--team"))
        assertEquals("socks5://127.0.0.1:1080", valueAfter("--upstream"))
        assertEquals("162.159.199.238:443", valueAfter("--peer"))
        assertEquals("x25519:P-256", valueAfter("--tls-groups"))
        assertEquals("x25519", valueAfter("--tls-key-shares"))
        assertEquals("rsa_pss_rsae_sha256", valueAfter("--tls-sigalgs"))
        assertEquals("TLS_AES_128_GCM_SHA256", valueAfter("--tls13-ciphers"))
        assertEquals("brotli", valueAfter("--tls-cert-compression"))
        assertEquals("new", valueAfter("--tls-alps"))
        assertTrue(args.contains("--enable-grease-sigalgs"))
        assertTrue(args.contains("--enable-ech-grease"))
        assertTrue(args.contains("--tls-verify"))
    }

    @Test
    fun unsupportedProtocolIsRejected() {
        try {
            flags(AetherCliSettings(protocol = "shard", socksPort = 10822, identityFile = "x"))
            org.junit.Assert.fail("expected rejection")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun jsonRoundTripPreservesSettings() {
        val original = AetherCliSettings(
            protocol = "gool", socksPort = 10822, identityFile = "/x.toml",
            masqueSni = "www.cloudflare.com", fragment = true, fragmentSize = 8,
            team = "org", tlsAlps = "new", tlsVerify = true,
        )
        val json = AetherCliConfig.toJson(original)
        val restored = AetherCliConfig.fromJson(json, identityFile = "/x.toml", socksPort = 10822)
        assertEquals(original.copy(socksPort = 10822), restored)
    }

    @Test
    fun malformedJsonFallsBackToDefaults() {
        val s = AetherCliConfig.fromJson("not json", identityFile = "/x.toml", socksPort = 10822)
        assertEquals("wireguard", s.protocol)
        assertFalse(s.fragment)
        assertEquals("/x.toml", s.identityFile)
    }
}
