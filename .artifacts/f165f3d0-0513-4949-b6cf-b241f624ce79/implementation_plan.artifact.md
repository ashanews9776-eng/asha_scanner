# Implementation Plan - Aether-GUI Features Integration

This plan integrates key features from the [Aether-GUI](https://github.com/MatinSenPai/Aether-GUI) project into the Asha Scanner app, focusing on user experience, simplified configuration via Tiers, and better quality visualization.

## Proposed Changes

### [Component] Core Models & Logic

#### [MODIFY] [Models.kt](file:///C:/Users/Ashkan/AndroidStudioProjects/asha_scanner_ip/app/src/main/java/com/ahoura/asha_scanner_ip/core/model/Models.kt)
- Add `ScanTier` enum: `TURBO`, `BALANCED`, `THOROUGH`, `STEALTH`, `IRONCLAD`, `CUSTOM`.
- Add `tier` field to `ScanConfig` (defaulting to `BALANCED`).

#### [MODIFY] [ScanViewModel.kt](file:///C:/Users/Ashkan/AndroidStudioProjects/asha_scanner_ip/app/src/main/java/com/ahoura/asha_scanner_ip/ui/ScanViewModel.kt)
- Implement `setTier(tier: ScanTier)` logic to apply preset parameters to `ScanConfig`.
- Presets will define `count`, `concurrency`, `timeoutMs`, `mode`, and `speedTest` based on the selected tier.

---

### [Component] Localization (i18n)

#### [MODIFY] [Strings.kt](file:///C:/Users/Ashkan/AndroidStudioProjects/asha_scanner_ip/app/src/main/java/com/ahoura/asha_scanner_ip/ui/i18n/Strings.kt)
- Add names and descriptions for each Scan Tier in both English and Persian.
- Examples:
    - **Turbo**: "Fastest discovery, raw TCP."
    - **Ironclad**: "High reliability, deep verification."

---

### [Component] UI Enhancements

#### [MODIFY] [QuickScanScreen.kt](file:///C:/Users/Ashkan/AndroidStudioProjects/asha_scanner_ip/app/src/main/java/com/ahoura/asha_scanner_ip/ui/screens/QuickScanScreen.kt)
- Add a `TierSelector` component to the `ScanSettings` section.
- This allows users to quickly toggle between scanning modes without touching advanced sliders.

#### [MODIFY] [CustomScanScreen.kt](file:///C:/Users/Ashkan/AndroidStudioProjects/asha_scanner_ip/app/src/main/java/com/ahoura/asha_scanner_ip/ui/screens/CustomScanScreen.kt)
- Ensure the tier selector is also available here, providing a "One-Click" starting point for custom scans.

## Verification Plan

### Automated Tests
- N/A (UI and Logic focused)

### Manual Verification
- Deploy to device.
- Select "Turbo" tier and verify that the "Workers" and "Count" parameters in the UI (if visible) or the scan behavior matches the preset.
- Verify that the Persian descriptions for tiers are correct and localized.
- Run a scan and verify the "Grade" (S/A/B/C) is displayed correctly on results.
