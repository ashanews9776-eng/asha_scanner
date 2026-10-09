package com.ahoura.asha_scanner_ip.core.guard

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

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

    companion object {
        private const val PREFERENCES = "split_tunneling"
        private const val MODE = "mode"
        private const val PACKAGES = "packages"

        suspend fun loadInstalledApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val ourPkg = context.packageName
            val installed = runCatching {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            }.getOrDefault(emptyList())

            installed
                .filter { it.packageName != ourPkg }
                .map { appInfo ->
                    val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                        .getOrDefault(appInfo.packageName)
                    AppEntry(
                        packageName = appInfo.packageName,
                        label = label,
                        isSystem = isSys,
                    )
                }
                .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
        }
    }
}
