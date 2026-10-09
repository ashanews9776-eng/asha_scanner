package com.ahoura.asha_scanner_ip.core.guard

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Provisions a WARP identity for a fresh install on a carrier that has blocked
 * api.cloudflareclient.com. Ported from MSN-GUARD upstream.
 *
 * ## The problem
 *
 * WireGuard and MASQUE cannot handshake until the core has registered a device
 * against the Cloudflare account API. On a clean install there is no saved
 * identity, so [GuardVpnService] has to make that call from the device's own
 * link. On an Iranian carrier that lookup is poisoned and the TLS handshake to
 * the API is dropped, so registration fails and every WARP-based transport
 * fails with it — the "first connect dies in seconds" report from a phone that
 * has never connected before, while a phone that connected once keeps working
 * because its identity file is already on disk.
 *
 * ## The fix
 *
 * SHARD is the one transport that does not depend on the account API at all:
 * xray dials its own nodes with credentials shipped in the subscription. So the
 * working order is to bring SHARD up, then register through its SOCKS listener,
 * then hand the saved identity back to the transport the user actually wanted.
 *
 * The core does its half through the `socks_proxy` config key (see
 * [CoreConfig.json]): when present, the account API rides the SOCKS listener
 * instead of the carrier. This class owns the orchestration around it — decide
 * whether it is needed, get a SHARD listener, clear the key afterwards.
 */
object IdentityProvisioner {

    private const val TAG = "IdentityProvisioner"

    fun isValidWarpToml(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            val toml = file.readText()
            (toml.contains("wg_private_key") || toml.contains("private_key")) &&
                (toml.contains("device_id") || toml.contains("account_id"))
        } catch (_: Exception) {
            false
        }
    }

    fun isValidMasqueToml(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            val toml = file.readText()
            val hasKey = toml.contains("wg_private_key") || toml.contains("private_key") ||
                toml.contains("access_token") || toml.contains("key_pem")
            val hasId = toml.contains("device_id") || toml.contains("account_id")
            hasKey && hasId
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Whether a WARP/MASQUE identity is already saved for [protocol].
     *
     * Mirrors `load_or_provision_warp` / `load_or_provision_masque` in the
     * core: the sibling file of aether.toml carries the credentials, and only
     * a file that actually contains valid identity fields and certificates counts.
     */
    fun hasIdentity(context: Context, protocol: String): Boolean {
        val base = File(context.filesDir, "aether.toml")
        return when (protocol.lowercase()) {
            "masque" -> isValidMasqueToml(siblingFile(base, "masque"))
            // GOOL/WoW dials two WARP identities, so both halves must exist.
            "gool", "warp-in-warp", "wow" -> {
                isValidWarpToml(base) && isValidWarpToml(siblingFile(base, "secondary"))
            }
            // MIM dials two MASQUE identities: primary AND secondary sibling.
            "mim", "masque-in-masque", "masque over masque" -> {
                val primaryMasque = siblingFile(base, "masque")
                val secondaryMasque = siblingFile(primaryMasque, "secondary")
                isValidMasqueToml(primaryMasque) && isValidMasqueToml(secondaryMasque)
            }
            else -> isValidWarpToml(base)
        }
    }

    /** `base` with [suffix] inserted before the extension (derive_sibling_path). */
    private fun siblingFile(base: File, suffix: String): File {
        val name = base.name
        val dot = name.lastIndexOf('.')
        return if (dot > 0) {
            File(base.parentFile, "${name.substring(0, dot)}-$suffix${name.substring(dot)}")
        } else {
            File(base.parentFile, "$name-$suffix")
        }
    }

    /**
     * A blocked answer is a property of the *network*: cached for
     * [BLOCKED_CACHE_MS] so the Auto-Scan ladder does not re-pay the probe
     * timeout on every rung. A positive answer is never cached.
     */
    @Volatile
    private var apiBlockedSince = 0L
    private const val BLOCKED_CACHE_MS = 5 * 60_000L

    fun clearApiBlockedCache() {
        apiBlockedSince = 0L
    }

    fun apiAlreadyMeasuredBlocked(): Boolean =
        apiBlockedSince != 0L && System.currentTimeMillis() - apiBlockedSince < BLOCKED_CACHE_MS

    /** Whether the SHARD listener was live before this attempt raised it. */
    @Volatile
    var shardWasAlreadyRunning = false
        private set

    /**
     * Does the device's own link reach the account API?
     *
     * A POST, not a GET: on an Iranian carrier a plain GET answers while the
     * real registration is dropped once its payload appears — the probe has to
     * exercise the same request shape to mean anything. Any HTTP status (even
     * 4xx/5xx) proves the route is open; only failing to get a status at all
     * counts as filtered.
     */
    fun accountApiReachable(context: Context): Boolean {
        if (apiAlreadyMeasuredBlocked()) {
            Log.i(TAG, "account API already measured blocked on this network — skipping the probe")
            return false
        }
        return try {
            val connection = URL(API_REGISTER_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("CF-Client-Version", CLIENT_VERSION)
            connection.doOutput = true
            connection.outputStream.use { it.write(PROBE_BODY.toByteArray()) }
            connection.responseCode in 200..599
        } catch (e: Exception) {
            Log.i(TAG, "account API not reachable directly: ${e.message}")
            apiBlockedSince = System.currentTimeMillis()
            false
        }
    }

    /**
     * Whether the device's own link reaches the account API, and a SHARD
     * listener to register through when it does not — decided *in parallel*.
     * Racing them means a blocked carrier pays max(probe, shard) instead of
     * probe + shard.
     */
    fun probeAndRaiseShard(context: Context, probe: Boolean): ProbeOutcome {
        if (ShardManager.isRunning) {
            // A listener is already live and belongs to the user's own session.
            // Reuse it and skip the probe: if their tunnel is up, the account
            // API can ride it too.
            shardWasAlreadyRunning = true
            return ProbeOutcome(direct = true, listener = "127.0.0.1:${ShardManager.listenPort}")
        }
        shardWasAlreadyRunning = false

        // The probe is the slow half, so start the race first and let it run
        // while the probe is still in flight.
        ConnectionLog.record("Identity: starting SHARD to provision through it")
        val shardStarted = AtomicBoolean(false)
        Thread({
            shardStarted.set(try {
                ShardManager.start(context)
            } catch (e: Exception) {
                Log.w(TAG, "SHARD would not start for provisioning: ${e.message}")
                ConnectionLog.record("Identity: SHARD start failed — ${e.message}")
                false
            })
            if (!shardStarted.get()) {
                ConnectionLog.record(
                    "Identity: SHARD start failed — ${ShardManager.lastError.ifBlank { "no node answered" }}"
                )
            }
        }, "identity-shard-start").start()

        val direct = if (probe) accountApiReachable(context) else false
        if (direct) {
            waitForShard(shardStarted)
            if (shardStarted.get()) releaseShardListener(startedOurselves = true)
            return ProbeOutcome(direct = true, listener = null)
        }

        val listener = waitForShard(shardStarted)
        return ProbeOutcome(direct = false, listener = listener)
    }

    data class ProbeOutcome(val direct: Boolean, val listener: String?)

    private fun waitForShard(started: AtomicBoolean): String? {
        val deadline = System.currentTimeMillis() + START_BUDGET_MS
        val startOfErrors = ShardManager.lastError.length
        while (System.currentTimeMillis() < deadline) {
            if (started.get() || ShardManager.isRunning) {
                return "127.0.0.1:${ShardManager.listenPort}"
            }
            if (ShardManager.lastError.length > startOfErrors) break
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
        return null
    }

    fun ensureShardListener(context: Context): String? {
        if (ShardManager.isRunning) {
            Log.i(TAG, "SHARD already running; reusing its listener")
            return "127.0.0.1:${ShardManager.listenPort}"
        }
        ConnectionLog.record("Identity: starting SHARD to provision through it")
        val started = AtomicBoolean(false)
        Thread({
            started.set(try {
                ShardManager.start(context)
            } catch (e: Exception) {
                Log.w(TAG, "SHARD would not start for provisioning: ${e.message}")
                ConnectionLog.record("Identity: SHARD start failed — ${e.message}")
                false
            })
            if (!started.get()) {
                ConnectionLog.record(
                    "Identity: SHARD start failed — ${ShardManager.lastError.ifBlank { "no node answered" }}"
                )
            }
        }, "identity-shard-start").start()
        val deadline = System.currentTimeMillis() + START_BUDGET_MS
        val startOfErrors = ShardManager.lastError.length
        while (System.currentTimeMillis() < deadline) {
            if (started.get() || ShardManager.isRunning) {
                return "127.0.0.1:${ShardManager.listenPort}"
            }
            if (ShardManager.lastError.length > startOfErrors) break
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
        return null
    }

    /** Take down the SHARD session this class raised (never a user's session). */
    fun releaseShardListener(startedOurselves: Boolean) {
        if (!startedOurselves) return
        if (!ShardManager.isRunning) return
        ConnectionLog.record("Identity: stopping the SHARD session used for provisioning")
        ShardManager.stop()
    }

    /**
     * Register a WARP identity through the SHARD SOCKS listener, without
     * involving the core, and save it in the TOML shape the core reads on the
     * next connect. Returns the saved file on success, null otherwise.
     */
    fun provisionThroughShard(context: Context, protocol: String, socksPort: Int): File? {
        val socks = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        val body = JSONObject().apply {
            put("install_id", "")
            put("fcm_token", "")
            put("tos", nowIso())
            put("model", android.os.Build.MODEL)
            put("serial_number", android.os.Build.SERIAL ?: "unknown")
            put("locale", java.util.Locale.getDefault().toString())
            put("key_type", "curve25519")
            put("tunnel_type", "wireguard")
        }
        return try {
            val connection = URL(API_REGISTER_URL).openConnection(socks) as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("CF-Client-Version", CLIENT_VERSION)
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "registration through SHARD returned HTTP $code")
                return null
            }
            val response = connection.inputStream.bufferedReader().readText()
            saveIdentity(context, protocol, JSONObject(response))
        } catch (e: Exception) {
            Log.w(TAG, "registration through SHARD failed: ${e.message}")
            null
        }
    }

    /**
     * Write the identity as a flat TOML table matching the core's
     * PersistedIdentity serialization — drifting from it would produce a file
     * that parses into empty credentials and silently re-registers.
     */
    private fun saveIdentity(context: Context, protocol: String, response: JSONObject): File? {
        return try {
            val config = response.optJSONObject("config")
                ?: response.optJSONObject("peer_config")
                ?: return null
            val toml = buildString {
                appendLine("device_id = \"${response.optString("id")}\"")
                appendLine("access_token = \"${response.optString("token")}\"")
                appendLine("cert_pem = \"\"")
                appendLine("key_pem = \"\"")
                appendLine("cert_issued_at = 0")
                appendLine("ipv4 = \"${config.optString("interface")}\"")
                appendLine("ipv6 = \"${config.optString("interface_v6")}\"")
                appendLine("wg_private_key = \"${response.optString("private_key")}\"")
                appendLine("wg_peer_public_key = \"${config.optString("public_key")}\"")
                appendLine("client_id = \"${response.optString("client_id")}\"")
                appendLine("organization = \"\"")
                appendLine("gateway_proxy = \"\"")
                appendLine("assigned_endpoint = \"${config.optString("endpoint")}\"")
            }
            val base = File(context.filesDir, "aether.toml")
            val target = when (protocol.lowercase()) {
                "masque" -> siblingFile(base, "masque")
                "gool", "warp-in-warp", "wow" -> {
                    if (isValidWarpToml(base)) {
                        siblingFile(base, "secondary")
                    } else {
                        base
                    }
                }
                "mim", "masque-in-masque", "masque over masque" -> {
                    val primaryMasque = siblingFile(base, "masque")
                    if (isValidMasqueToml(primaryMasque)) {
                        siblingFile(primaryMasque, "secondary")
                    } else {
                        primaryMasque
                    }
                }
                else -> base
            }
            target.writeText(toml)
            Log.i(TAG, "saved a WARP identity to ${target.name} via SHARD")
            target
        } catch (e: Exception) {
            Log.w(TAG, "could not save the identity: ${e.message}")
            null
        }
    }

    private fun nowIso(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
            .format(java.util.Date())

    private const val API_REGISTER_URL = "https://api.cloudflareclient.com/v0a4471/reg"
    private const val USER_AGENT = "okhttp/3.12.1"
    private const val CLIENT_VERSION = "a-6.41-2158"

    /** Deliberately invalid: a 400 still proves the route is open. */
    private const val PROBE_BODY = "{}"

    /** Worst-case pool race budget; the listener usually binds in 1–3 s. */
    private const val START_BUDGET_MS = 15_000L
}
