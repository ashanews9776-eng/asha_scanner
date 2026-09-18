# ── Asha Scanner R8 / ProGuard rules ─────────────────────────────────────────
# Conservative keeps for the few libraries that touch reflection. Compose,
# AndroidX lifecycle/DataStore and Lottie all ship their own consumer rules in
# their AARs, so this file only adds project-specific safety nets.

# Keep line numbers for readable crash reports, strip the source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Domain models are plain data classes only consumed in-process (no reflection /
# serialization), so they're safe to shrink/obfuscate. Listed here as a reminder
# that if a reflective exporter is ever added, it must be kept.

# Lottie parses bundled raw JSON via its own code path; its AAR provides the
# needed rules. Keep its annotations defensively.
-dontwarn com.airbnb.lottie.**

# org.json is provided by the Android platform.
-dontwarn org.json.**

# Kotlin coroutines internal — standard keeps (also covered by the shrinker, kept
# explicitly so debug-info-free internals don't trip strict configs).
-dontwarn kotlinx.coroutines.**

# ── Asha Guard Anti-Censorship VPN Engine & JNI ─────────────────────────────
-keep class com.ahoura.asha_scanner_ip.core.guard.** { *; }
-keep interface com.ahoura.asha_scanner_ip.core.guard.** { *; }
-keepclassmembers class com.ahoura.asha_scanner_ip.core.guard.** { *; }
-dontwarn com.ahoura.asha_scanner_ip.core.guard.**
# NativeCore stays at this legacy package: libaether_jni.so exports
# Java_com_msnguard_vpn_NativeCore_* symbols, so the class name is load-bearing.
-keep class com.msnguard.vpn.** { *; }
-keepclassmembers class com.msnguard.vpn.** { *; }
-dontwarn com.msnguard.vpn.**

# ── Psiphon Tunnel (libgojni.so) & Tun2Socks ─────────────────────────────────
-keep class ca.psiphon.** { *; }
-keep interface ca.psiphon.** { *; }
-keepclassmembers class ca.psiphon.** { *; }
-keep class psi.** { *; }
-keep interface psi.** { *; }
-keepclassmembers class psi.** { *; }
-keep class go.** { *; }
-keep interface go.** { *; }
-keepclassmembers class go.** { *; }
-dontwarn ca.psiphon.**
-dontwarn psi.**
-dontwarn go.**

# ── Asha VPN Core, Scanners, & Process Managers ──────────────────────────────
-keep class com.ahoura.asha_scanner_ip.AshaApplication { *; }
-keep class com.ahoura.asha_scanner_ip.core.vpn.** { *; }
-keep interface com.ahoura.asha_scanner_ip.core.vpn.** { *; }
-keepclassmembers class com.ahoura.asha_scanner_ip.core.vpn.** { *; }
-keep class com.ahoura.asha_scanner_ip.core.storm.** { *; }
-keep class com.ahoura.asha_scanner_ip.core.validator.** { *; }

# ── Preserve ALL native JNI methods across all classes ───────────────────────
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── Keep methods called by native C++ code (aether_jni.cpp) ───────────────────
-keepclassmembers class * extends android.net.VpnService {
    public boolean protectSocket(int);
    public void onEvent(java.lang.String);
}
