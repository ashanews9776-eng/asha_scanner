# AGENTS.md — Asha Scanner (asha_scanner_ip)

Native Android app (Kotlin + Jetpack Compose, MVVM) that finds fast/reliable
Cloudflare "clean" IPs for VLESS/Trojan proxy configs. A Kotlin port of the
Go-based SenPaiScanner, optimized for users on restricted networks. Bilingual
(English + Persian/Farsi) with a cyberpunk terminal UI.

## Build & Run

Gradle Kotlin DSL, single `:app` module. Use the wrapper, not a system Gradle.

```bash
./gradlew assembleDebug            # debug APK
./gradlew assembleRelease          # signed release APK (needs signing config — see below)
./gradlew test                     # JVM unit tests (JUnit4)
./gradlew connectedAndroidTest     # instrumented tests (needs a device/emulator)
./gradlew lint                     # Android lint
./gradlew clean
```

On Windows the wrapper is `gradlew.bat`. JDK 17 is what CI uses (see
`.github/workflows/release.yml`); `compileOptions` pins Java 11 source/target.

Versions are centralized in `gradle/libs.versions.toml` (version catalog) — add
deps there, then reference via `libs.*` in `app/build.gradle.kts`. SDK:
`compileSdk`/`targetSdk` 37, `minSdk` 26. Bump `versionCode` + `versionName`
together in `app/build.gradle.kts` for any release.

## Signing (release builds)

The `release` build type enables R8 full mode (`isMinifyEnabled`,
`isShrinkResources`) with `app/proguard-rules.pro`. Signing config reads, in
priority order:

1. Env vars (CI): `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
   `KEY_PASSWORD`.
2. `local.properties` keys (local): `keystore.password`, `key.alias`,
   `key.password`, plus a `release-key.jks` at repo root.

`local.properties`, `*.jks`, and `keystore_base64.txt` are gitignored — never
commit secrets. A debug build runs fine without any of this.

## Release / CI

Tag push matching `v*` (or `workflow_dispatch`) triggers
`.github/workflows/release.yml`, which decodes the `SIGNING_KEY` secret to
`app/release-key-ci.jks`, builds `assembleRelease`, renames the APK to
`asha_scanner.apk`, and publishes a GitHub Release. The in-app update checker
(`ScanViewModel.checkUpdate`) polls the GitHub releases API and compares
`v{versionName}`.

## Architecture & layer boundaries

Package root: `com.ahoura.asha_scanner_ip`. Keep layers separated:

- `core/model/` — domain models (`ScanConfig`, `ProxyConfig`, `ScanResult`,
  `ScanProgress`, enums). Plain data classes, no Android/Compose deps. Safe to
  shrink/obfuscate (noted in proguard-rules.pro) — if you add a reflective
  serializer, you must add a keep rule.
- `core/engine/ScanEngine` — orchestrates a scan as a `Flow<ScanProgress>`:
  Phase-1 fan-out probing → Phase-2 throughput validation of top survivors, with
  an open-site fallback if Phase-1 finds nothing. This is the central seam.
- `core/prober/`, `core/validator/`, `core/net/`, `core/parser/`,
  `core/ipsrc/`, `core/output/` — the engine's collaborators. `Validator` is an
  interface the engine accepts via `validatorOverride`; swap implementations
  without touching the engine.
- `data/SettingsStore` — DataStore Preferences persistence (`asha_settings`).
- `ui/` — Jetpack Compose. `ScanViewModel` (AndroidViewModel) holds all state in
  a single `UiState` exposed as `StateFlow`; screens are stateless and read from
  it. `ui/i18n/Strings.kt` holds all user-facing strings for both languages.
- `MainActivity` — the only Activity; everything else is Composables.

**Rule:** `core` must not import from `ui`. UI talks to `core` only through
`ScanEngine`, `ProxyParser`, and the models. Inject new `Validator`/`IpSource`
implementations through the engine's constructor params, not by editing the
engine flow.

## i18n (English + Persian)

All user-facing prose lives in `ui/i18n/Strings.kt` as the `AppStrings` data
class, with `stringsFor(Lang)` resolving FA/EN. Strings are provided via
`LocalStrings`/`LocalLang` CompositionLocals and the in-app toggle is instant.
**When adding or changing any UI text, update both languages in `Strings.kt`** —
don't hardcode strings in composables. Short technical tokens (HTTP/TLS/TCP,
port numbers, column codes) are intentionally identical across languages.

## Phase-2 validation note (gotcha)

`XrayValidator` is a documented stub — it throws `NotImplementedError`. The app
ships with `DirectThroughputValidator` (no native code). Full end-to-end
validation through a real xray-core tunnel requires bundling the xray-core AAR
and implementing the start/stop glue as described in `XrayValidator.kt`. Don't
wire `XrayValidator` into the engine until that's done.

## Bundled assets

`app/src/main/assets/`: `cf_ipv4.txt` (precise Cloudflare IPv4 ranges) and
`cf_domains.txt` (open-site fallback domains). `ScanViewModel.init` loads both
on a background thread and merges user-added domains with the bundled list. If
you edit these, keep one CIDR/domain per line; `#` comments and blank lines are
stripped.

## Local sub server

`core/net/SubServer` runs a tiny HTTP server on port 8081 during a scan,
serving the current best results as a base64 V2Ray subscription link. Started
in `ScanViewModel.start()`, stopped in `stop()`/`onCleared()`. Lifecycle is tied
to scan state — don't leave it running.

## Coding conventions

- Kotlin code style: `official` (see `gradle.properties`).
- Compose-only UI (Material3); no XML layouts. Animations use Canvas API + Lottie.
- Coroutines throughout: engine uses `channelFlow` + worker pool; ViewModel uses
  `viewModelScope`/`Dispatchers.IO` for asset loads and network.
- Network is plain `java.net` / `HttpURLConnection` (no Retrofit/OkHttp dependency).
- Comments are dense and explain *why* (porting notes from SenPaiScanner,
  ProGuard rationale, fallback logic). Match that style.

## Before editing sensitive areas

- R8/ProGuard: read `app/proguard-rules.pro` — it documents which libs ship
  their own consumer rules and which need explicit keeps.
- Scan flow: read `core/engine/ScanEngine.kt` end-to-end before changing phases,
  the worker pool, or the fallback path.
- Signing/CI: read `.github/workflows/release.yml` and the `signingConfigs`
  block in `app/build.gradle.kts` before touching release output or secrets.
