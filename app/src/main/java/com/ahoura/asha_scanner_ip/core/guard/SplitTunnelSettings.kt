package com.ahoura.asha_scanner_ip.core.guard

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

object AppIconCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun get(pm: PackageManager, packageName: String): ImageBitmap? {
        return cache.getOrPut(packageName) {
            runCatching {
                pm.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull() ?: return null
        }
    }

    fun clear() {
        cache.clear()
    }
}

class SplitTunnelSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    enum class Mode(val label: String) {
        ALL("All apps"),
        INCLUDE("Only selected apps"),
        EXCLUDE("Exclude selected apps"),
    }

    fun mode(): Mode = preferences.getString(MODE, Mode.ALL.name)
        ?.let { runCatching { Mode.valueOf(it) }.getOrNull() }
        ?: Mode.ALL

    fun packages(): Set<String> = preferences.getStringSet(PACKAGES, emptySet()).orEmpty()

    fun save(mode: Mode, packages: Set<String>) {
        preferences.edit()
            .putString(MODE, mode.name)
            .putStringSet(PACKAGES, packages.toHashSet())
            .apply()
    }

    fun cleanup(installedPackages: Set<String>) {
        val current = packages()
        val filtered = current.filter { it in installedPackages }.toSet()
        if (filtered.size != current.size) {
            preferences.edit().putStringSet(PACKAGES, filtered).apply()
        }
    }

    fun applyTo(builder: VpnService.Builder, context: Context): VpnService.Builder {
        val mode = mode()
        val pkgs = packages()

        if (mode == Mode.ALL) {
            // GLOBAL: all apps through VPN, but MUST exclude ourselves to prevent routing loop.
            runCatching { builder.addDisallowedApplication(context.packageName) }
            return builder
        }

        if (mode == Mode.INCLUDE) {
            // INCLUDE (whitelist): only listed apps go through VPN.
            // Do NOT add our own packageName — it's excluded by default.
            // Do NOT use addDisallowedApplication here (mixing with addAllowedApplication crashes).
            if (pkgs.isEmpty()) {
                Log.w(TAG, "No apps selected for Per-App routing. Falling back to all apps.")
                ConnectionLog.record("Split tunnel include: no apps selected, falling back to all")
                runCatching { builder.addDisallowedApplication(context.packageName) }
                return builder
            }

            var addedCount = 0
            pkgs.forEach { pkg ->
                try {
                    builder.addAllowedApplication(pkg)
                    addedCount++
                } catch (_: PackageManager.NameNotFoundException) {
                    Log.w(TAG, "Split tunnel skipped missing app: $pkg")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add $pkg to split tunnel: ${e.message}")
                }
            }

            if (addedCount == 0) {
                Log.w(TAG, "Selected apps are no longer installed. Falling back to all apps.")
                ConnectionLog.record("Split tunnel include: selected apps not found, falling back")
                runCatching { builder.addDisallowedApplication(context.packageName) }
                return builder
            }

            ConnectionLog.record("Split tunnel include: $addedCount app(s)")
            return builder
        }

        if (mode == Mode.EXCLUDE) {
            // EXCLUDE (blacklist): selected apps bypass VPN directly.
            var addedCount = 0
            pkgs.forEach { pkg ->
                if (pkg != context.packageName) {
                    try {
                        builder.addDisallowedApplication(pkg)
                        addedCount++
                    } catch (_: PackageManager.NameNotFoundException) {
                        Log.w(TAG, "Split tunnel skipped missing app: $pkg")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to add $pkg to split tunnel: ${e.message}")
                    }
                }
            }

            // Exclude our own app as well to avoid routing loops
            runCatching { builder.addDisallowedApplication(context.packageName) }
            ConnectionLog.record("Split tunnel exclude: $addedCount app(s)")
            return builder
        }

        return builder
    }

    companion object {
        private const val TAG = "SplitTunnel"
        private const val PREFERENCES = "split_tunneling"
        private const val MODE = "mode"
        private const val PACKAGES = "packages"

        suspend fun loadInstalledApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val ourPkg = context.packageName

            // 1. Identify launchable apps (apps with a launcher activity that user can launch from home screen)
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launchablePackages: Set<String> = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(launcherIntent, 0)
                }
            }.getOrDefault(emptyList()).mapNotNull { it.activityInfo?.packageName }.toSet()

            // 2. Fetch all installed applications fast with flag 0 (avoid slow GET_META_DATA and TransactionTooLargeException)
            val installed: List<ApplicationInfo> = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(0)
                }
            }.getOrElse {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0)).mapNotNull { it.applicationInfo }
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstalledPackages(0).mapNotNull { it.applicationInfo }
                    }
                }.getOrDefault(emptyList())
            }

            installed
                .filter { it.packageName != ourPkg }
                .map { appInfo ->
                    val isSysFlag = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSys = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    val isLaunchable = appInfo.packageName in launchablePackages

                    // Pure system daemon / background framework service that has NO launcher activity
                    // and has not been updated from store. User apps and pre-installed launcher apps
                    // (like Chrome, YouTube, Gmail, Maps) will have isSystem = false so they appear in main list.
                    val isSystem = isSysFlag && !isLaunchable && !isUpdatedSys

                    val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                        .getOrDefault(appInfo.packageName)
                        .ifBlank { appInfo.packageName }

                    AppEntry(
                        packageName = appInfo.packageName,
                        label = label,
                        isSystem = isSystem,
                    )
                }
                .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
        }
    }
}
