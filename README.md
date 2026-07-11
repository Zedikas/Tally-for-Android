# Tally for Android

Native Android conversion of **Tally 1.7**, built with Kotlin and Jetpack Compose.

## Included features

- Independent counters and folders with configurable folder presets
- Hold-to-drag counters between folders and the Unfiled section
- Quick Create for a preset counter or a counter with an immediately started timer
- Counter goals, custom step buttons, exact value entry, notes, pinning, locking, milestones, archive, duplicate, reset, and permanent delete
- Daily, weekly, and monthly automatic resets
- Standalone and counter-linked sessions
- History, statistics, CSV exports, and JSON backup/import
- Light, Dark, true OLED, preset accents, and custom HEX colors
- Alternate Android launcher icons, including distinct Glass and Pearl variants
- JSON model compatible with the field names and Apple-reference dates used by Tally 1.7 for iOS

## APK build

GitHub Actions creates an installable debug APK after every source change to `main`, or manually from **Actions → Build Android APK → Run workflow**.

Artifact name:

```text
Tally_Android_v1_7_APK
```

A diagnostic artifact named `Tally_Android_build_diagnostics` is uploaded on every run.

## Local build

Requires JDK 17, Android SDK 35, and Gradle 8.9:

```bash
gradle :app:assembleDebug
```
