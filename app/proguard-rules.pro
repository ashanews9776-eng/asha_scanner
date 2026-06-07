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
