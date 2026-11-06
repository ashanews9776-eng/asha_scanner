package com.ahoura.asha_scanner_ip.core.aether

import android.content.Context
import android.util.Log
import com.ahoura.asha_scanner_ip.core.guard.ConnectionLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Information regarding a provisioned Cloudflare WARP cryptographic identity file.
 */
data class AetherKeyInfo(
    val type: String,
    val fileName: String,
    val isPresent: Boolean,
    val deviceId: String = "",
    val ipv4: String = "",
    val ipv6: String = "",
)

/**
 * Manages Cloudflare WARP identities for Aether v2.4.
 *
 * Automatically detects, verifies, provisions, and renews WARP credentials
 * (WireGuard, MASQUE, WARP-in-WARP, MASQUE-in-MASQUE, WireGuard-over-MASQUE)
 * using the bundled Aether v2.4 CLI engine with anti-censorship parameters
 * (ClientHello fragmentation, Chrome TLS cipher suites, and ECH auto).
 */
object AetherKeyManager {

    private const val TAG = "AetherKeyManager"
    private const val BINARY_NAME = "libaether_v24.so"

    // Canonical identity filenames used by Aether v2.4
    const val FILE_WG_PRIMARY = "aether.toml"
    const val FILE_WG_SECONDARY = "aether-secondary.toml"
    const val FILE_MASQUE_PRIMARY = "aether-masque.toml"
    const val FILE_MASQUE_SECONDARY = "aether-masque-secondary.toml"
    const val FILE_WOM_INNER = "aether-masque-gool.toml"

    private val registrationLock = ReentrantLock()

    private fun binary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)

    /**
     * Reads and parses a TOML identity file.
     */
    fun parseIdentity(file: File): AetherKeyInfo? {
        if (!file.exists() || !file.canRead() || file.length() < 20) return null
        return try {
            val text = file.readText()
            var deviceId = ""
            var ipv4 = ""
            var ipv6 = ""

            text.lines().forEach { rawLine ->
                val line = rawLine.trim()
                when {
                    line.startsWith("device_id") -> {
                        deviceId = line.substringAfter("=").trim().trim('"', '\'')
                    }
                    line.startsWith("ipv4") -> {
                        ipv4 = line.substringAfter("=").trim().trim('"', '\'')
                    }
                    line.startsWith("ipv6") -> {
                        ipv6 = line.substringAfter("=").trim().trim('"', '\'')
                    }
                }
            }

            if (deviceId.isNotBlank()) {
                AetherKeyInfo(
                    type = file.name,
                    fileName = file.name,
                    isPresent = true,
                    deviceId = deviceId,
                    ipv4 = ipv4,
                    ipv6 = ipv6,
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse identity file ${file.name}: ${e.message}")
            null
        }
    }

    /**
     * Returns the required identity files for a given protocol.
     */
    fun requiredFilesForProtocol(protocol: String): List<String> = when (protocol.trim().lowercase()) {
        "wireguard", "wg" -> listOf(FILE_WG_PRIMARY)
        "masque" -> listOf(FILE_MASQUE_PRIMARY)
        "gool", "warp-in-warp", "wiw" -> listOf(FILE_WG_PRIMARY, FILE_WG_SECONDARY)
        "mim", "masque-in-masque" -> listOf(FILE_MASQUE_PRIMARY, FILE_MASQUE_SECONDARY)
        "wom", "wg-over-masque" -> listOf(FILE_MASQUE_PRIMARY, FILE_WOM_INNER)
        else -> listOf(FILE_WG_PRIMARY)
    }

    /**
     * Checks if valid identity files exist for the specified protocol.
     */
    fun hasKeysFor(context: Context, protocol: String): Boolean {
        val files = requiredFilesForProtocol(protocol)
        return files.all { fileName ->
            val file = File(context.filesDir, fileName)
            parseIdentity(file) != null
        }
    }

    /**
     * Returns status for all 5 Cloudflare WARP key types.
     */
    fun getAllKeysStatus(context: Context): List<AetherKeyInfo> {
        val defs = listOf(
            "WireGuard" to FILE_WG_PRIMARY,
            "WireGuard Inner" to FILE_WG_SECONDARY,
            "MASQUE" to FILE_MASQUE_PRIMARY,
            "MASQUE Inner" to FILE_MASQUE_SECONDARY,
            "WOM (WireGuard over MASQUE)" to FILE_WOM_INNER,
        )

        return defs.map { (label, fileName) ->
            val file = File(context.filesDir, fileName)
            val parsed = parseIdentity(file)
            parsed?.copy(type = label) ?: AetherKeyInfo(
                type = label,
                fileName = fileName,
                isPresent = false,
            )
        }
    }

    /**
     * Maps protocol string to Aether CLI --register target.
     */
    private fun registerTargetForProtocol(protocol: String): String = when (protocol.trim().lowercase()) {
        "wireguard", "wg" -> "wg"
        "masque" -> "masque"
        "gool", "warp-in-warp", "wiw" -> "gool-classic"
        "mim", "masque-in-masque" -> "mim"
        "wom", "wg-over-masque" -> "gool"
        else -> "all"
    }

    /**
     * Automatically ensures required keys for [protocol] exist and are valid.
     * If missing, performs automatic background registration with anti-censorship parameters.
     */
    suspend fun ensureKeysFor(
        context: Context,
        protocol: String,
        onProgress: (String) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        if (hasKeysFor(context, protocol)) {
            Log.d(TAG, "WARP keys for $protocol already present and verified")
            return@withContext true
        }

        val target = registerTargetForProtocol(protocol)
        Log.i(TAG, "WARP keys missing for $protocol, auto-provisioning target '$target'…")
        onProgress("Acquiring Cloudflare credentials ($protocol)…")
        registerKeys(context, target, onProgress)
    }

    /**
     * Renews all 5 Cloudflare WARP identities in one unified operation.
     */
    suspend fun renewAllKeys(
        context: Context,
        onProgress: (String) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        onProgress("Renewing all Cloudflare credentials…")
        registerKeys(context, "all", onProgress)
    }

    /**
     * Executes Aether v2.4 CLI registration command with anti-censorship arguments.
     */
    private fun registerKeys(
        context: Context,
        kind: String,
        onProgress: (String) -> Unit,
    ): Boolean = registrationLock.withLock {
        val bin = binary(context)
        if (!bin.exists() || !bin.canExecute()) {
            val err = "Aether v2.4 binary missing or not executable at ${bin.absolutePath}"
            Log.e(TAG, err)
            ConnectionLog.record("$TAG: $err")
            onProgress("Aether binary missing or not executable")
            return false
        }

        val args = mutableListOf(
            bin.absolutePath,
            "--register", kind,
            "--fragment",
            "--fragment-size", "10-30",
            "--fragment-delay", "10-50",
            "--tls-ciphers", "ALL:!aPSK:!ECDSA+SHA1:!3DES",
            "--tls-groups", "X25519MLKEM768:X25519:P-256:P-384",
            "--tls-sigalgs", "ecdsa_secp256r1_sha256:rsa_pss_rsae_sha256:rsa_pkcs1_sha256:ecdsa_secp384r1_sha384:rsa_pss_rsae_sha384:rsa_pkcs1_sha384:rsa_pss_rsae_sha512:rsa_pkcs1_sha512",
            "--log-level", "info",
        )

        // Check if user has a scanned clean Cloudflare IP to bypass potential DNS poisoning
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val storedClean = prefs.getString("clean_ip", null)?.trim().orEmpty()
        if (storedClean.isNotBlank()) {
            val ipOnly = storedClean.substringBefore(":")
            args.addAll(listOf(
                "--api-address", ipOnly,
                "--api-sni", "api.devices.cloudflare.com",
                "--api-host", "api.devices.cloudflare.com",
            ))
        }

        Log.i(TAG, "Starting key registration: ${args.joinToString(" ")}")
        ConnectionLog.record("$TAG: Starting registration for '$kind'")

        val builder = ProcessBuilder(args)
        builder.directory(context.filesDir)
        builder.redirectErrorStream(true)
        builder.environment().apply {
            put("HOME", context.filesDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("AETHER_CONFIG", File(context.filesDir, FILE_WG_PRIMARY).absolutePath)
            put("AETHER_MASQUE_CONFIG", File(context.filesDir, FILE_MASQUE_PRIMARY).absolutePath)
            put("AETHER_WG_CONFIG", File(context.filesDir, FILE_WG_PRIMARY).absolutePath)
            val certDirs = listOf("/apex/com.android.conscrypt/cacerts", "/system/etc/security/cacerts")
                .filter { File(it).isDirectory }
            if (certDirs.isNotEmpty()) {
                put("SSL_CERT_DIR", certDirs.joinToString(":"))
            }
        }

        val proc = try {
            builder.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Aether registration: ${e.message}", e)
            onProgress("Failed to execute key registration: ${e.message}")
            return false
        }

        var successSeen = false
        var lastErr = ""
        val readerThread = Thread({
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).forEachLine { line ->
                    if (line.isNotBlank()) {
                        Log.d(TAG, line)
                        ConnectionLog.record("$TAG $line")
                        if (line.contains("identities ready") || line.contains("identity ready")) {
                            successSeen = true
                        }
                        if (line.contains("Error") || line.contains("error:") || line.contains("failed") || line.contains("Refused")) {
                            lastErr = line
                        }
                        // Send user-friendly progress updates
                        when {
                            line.contains("enrolling MASQUE key") -> onProgress("Enrolling MASQUE certificate…")
                            line.contains("provisioning dedicated wireguard") -> onProgress("Provisioning dedicated WireGuard identity…")
                            line.contains("warp enabled for device") -> onProgress("Enabling WARP service on device…")
                            line.contains("wireguard identity ready") -> onProgress("WireGuard identity ready ✓")
                            line.contains("masque identity ready") -> onProgress("MASQUE identity ready ✓")
                            line.contains("gool identity ready") -> onProgress("WireGuard-over-MASQUE identity ready ✓")
                            line.contains("identities ready") -> onProgress("Cloudflare WARP identities ready ✓")
                        }
                    }
                }
            } catch (_: Exception) {}
        }, "aether-reg-reader").apply {
            isDaemon = true
            start()
        }

        val completed = proc.waitFor(120, TimeUnit.SECONDS)
        if (!completed) {
            proc.destroyForcibly()
            Log.w(TAG, "Registration process timed out after 120s")
            onProgress("Registration process timed out")
            return false
        }

        runCatching { readerThread.join(1000) }

        val exitCode = proc.exitValue()
        Log.i(TAG, "Aether registration finished with exitCode=$exitCode, successSeen=$successSeen")

        val filesOk = if (kind == "all") {
            getAllKeysStatus(context).count { it.isPresent } >= 2
        } else {
            hasKeysFor(context, kind)
        }

        return if (exitCode == 0 || successSeen || filesOk) {
            onProgress("Cloudflare WARP credentials verified ✓")
            true
        } else {
            val friendlyErr = if (lastErr.isNotBlank()) {
                "Key registration failed: ${lastErr.take(65)}"
            } else {
                "Cloudflare API unreachable (exit code: $exitCode)"
            }
            onProgress(friendlyErr)
            false
        }
    }
}
