# QR Payment Queue

Native Android application for organizing and processing QR payment tasks.

> This repository contains a native Android application only. There is no web app, no PWA and no WebView wrapper anywhere in this project. The build output is a real APK produced by Gradle and by GitHub Actions.

## Purpose

QR Payment Queue helps you work through a large batch of PromptPay QR payment images in an organized, verifiable way:

1. Select a folder containing QR payment images.
2. Discover and decode the QR images.
3. Extract and validate payment information (amount, recipient, reference).
4. Detect duplicates and invalid QR codes.
5. Build a payment queue and review it.
6. Hand the selected QR image to your banking app through Android's normal share mechanism.
7. Keep the actual confirmation — PIN, OTP, biometrics, final "confirm" button — entirely in your hands.
8. Track and record each payment result, then show a processing summary.

The app prepares and organizes payment information. It never performs a payment.

## Platform and technology

| Concern | Choice |
| --- | --- |
| Platform | Native Android (min SDK 26, target SDK 35) |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | Single activity, AndroidX, ViewModel + StateFlow |
| Build | Gradle (Kotlin DSL), Android Gradle Plugin 8.7.3 |
| Package | `com.enjirad.qrqueue` |
| CI | GitHub Actions (`./gradlew assembleDebug`) |

## Current status — V0.1 (version 0.1.0)

The V0.1 goal is a real, installable Android app with a clean foundation:

- [x] Native Gradle project that builds a real APK
- [x] Single-activity Compose UI: header, queue stats, import entry point, empty state
- [x] Payment status model (`PaymentStatus`) and queue item model (`QueueItem`)
- [x] Unit tests for money formatting and status model invariants
- [x] GitHub Actions workflow that builds, verifies and uploads the debug APK
- [ ] Image/folder import (V0.2) — not implemented yet; the button says so honestly

Features that are not implemented yet are labeled as planned in the UI and in this roadmap. No fake or demo transactions are ever created or displayed.

## Repository layout

```
qr-queue-app/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/enjirad/qrqueue/
│       │   │   ├── MainActivity.kt
│       │   │   ├── domain/          (PaymentStatus, QueueItem, Money)
│       │   │   └── ui/              (QueueRoute, QueueScreen, QueueViewModel, theme)
│       │   └── res/                 (strings, colors, themes, launcher icon)
│       └── test/java/com/enjirad/qrqueue/domain/
├── gradle/
│   ├── libs.versions.toml           (pinned, compatible tool versions)
│   └── wrapper/
├── .github/workflows/android.yml    (builds + verifies + uploads the APK)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── AI_RULES.md
└── AI_HANDOFF.md
```

## Build

Requirements: JDK 17 and Android SDK 35 (Android Studio installs both).

```bash
./gradlew assembleDebug
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

Note: `gradle/wrapper/gradle-wrapper.jar` is a binary file. The CI workflow regenerates it with `gradle wrapper --gradle-version 8.11.1` before building, so a missing jar never blocks a build. For local command-line builds run that same `gradle wrapper` command once, or just open the project in Android Studio.

Useful tasks:

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug           # Android lint report
./gradlew assembleDebug       # debug APK
```

## Install the debug APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the device and open it (allow installation from unknown sources for this one app).

## Continuous integration and the APK artifact

`.github/workflows/android.yml` runs on every push, pull request and manual dispatch:

1. Checkout the repository
2. Set up JDK 17
3. Set up the Android environment and SDK packages
4. Ensure the Gradle wrapper exists and is executable
5. Run unit tests (`testDebugUnitTest`)
6. Run lint (`lintDebug`)
7. Build the APK (`./gradlew assembleDebug`)
8. Verify `app/build/outputs/apk/debug/app-debug.apk` exists (`test -f`)
9. Verify the APK is not empty (`test -s`)
10. Inspect the APK contents and upload it as the artifact **`qr-payment-queue-debug-apk`**

If the APK is missing or zero bytes, the workflow fails on purpose. A build is only considered successful when the real APK exists.

## Security and payment safety model

- The app never stores or types bank PINs, passwords, OTPs, card PINs or biometric credentials.
- The app never automates bank screens, never uses Accessibility to click payment buttons, and never makes hidden bank API calls.
- Payment confirmation always happens in the user's own banking app.
- A payment is never marked `PAID` just because a QR was opened, shared or submitted — only a human-verified result may set a final state.
- Unknown transactions are never retried automatically.
- V0.1 requests no runtime permissions; folder access will use Android's Storage Access Framework, not broad storage permissions.

## Payment status model

Progress states: `DISCOVERED → DECODED → VALIDATED → READY → SUBMITTED → WAITING_CONFIRMATION → SUCCESS → RECONCILED / PAID`

Error states: `INVALID`, `RECIPIENT_MISMATCH`, `AMOUNT_MISMATCH`, `ORDER_NOT_FOUND`, `DUPLICATE`, `EXPIRED`, `PAYMENT_FAILED`, `UNKNOWN`

## Roadmap

| Version | Scope | Status |
| --- | --- | --- |
| V0.1 | Native Android foundation, APK build, CI | delivered in source |
| V0.2 | Folder selection (Storage Access Framework) | planned |
| V0.3 | Discover QR images | planned |
| V0.4 | Decode QR payloads (QR/barcode scanner) | planned |
| V0.5 | PromptPay (EMVCo) payload parser | planned |
| V0.6 | Extract amount, recipient, reference, image URI, filename, timestamp | planned |
| V0.7 | Validation and duplicate detection | planned |
| V0.8 | Payment queue review UI | planned |
| V0.9 | Android Share Intent / payment handoff | planned |
| V1.0 | Payment result tracking and reconciliation | planned |
