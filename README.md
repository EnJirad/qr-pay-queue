# QR Payment Queue

Native Android application for organizing and processing QR payment tasks.

> This repository contains a native Android application only. There is no web app, no PWA and no WebView wrapper anywhere in this project. The build output is a real APK produced by Gradle and by GitHub Actions.

## Purpose

QR Payment Queue helps you work through a batch of PromptPay / Thai QR payment screenshots in an organized, verifiable way:

1. Import **several QR screenshot images at once** from Android's photo picker.
2. Copy those images into the app's own private storage (your gallery originals are never touched).
3. Decode the QR code in each image with a real QR decoder.
4. Validate each payload: structure, CRC, currency, recipient, amount.
5. Detect duplicates and unreadable or unsupported images.
6. Build a **sequential payment queue** and review it before starting.
7. Process one QR at a time: hand the QR to your banking app, pay it yourself, come back and record the real result.
8. Finish with a summary whose numbers come only from real decoded data and your own confirmations.

The app prepares, organizes and tracks payment tasks. It never performs a payment, and it never decides on its own that a payment succeeded.

## Platform and technology

| Concern | Choice |
| --- | --- |
| Platform | Native Android (min SDK 26, target SDK 35) |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | Single activity, AndroidX, ViewModel + StateFlow, domain/UI separation |
| Build | Gradle (Kotlin DSL), Android Gradle Plugin 8.7.3 |
| Package | `com.enjirad.qrqueue` |
| QR decoding | ZXing core 3.5.4 (pure Java, pinned in `gradle/libs.versions.toml`) |
| Persistence | App-private JSON state file + copied images in `filesDir/qrqueue/` |
| CI | GitHub Actions (`testDebugUnitTest`, `lintDebug`, `assembleDebug`, APK artifact) |

## The V2 user workflow

### 1. Import QR images

Tap **Import QR Images**. Android's own photo picker opens and you can select several screenshots in one go (no folder picking, no storage permission, no device scan). Each selected image is:

- copied into `filesDir/qrqueue/images/` (the app's private storage),
- decoded with ZXing,
- validated as an EMVCo PromptPay credit-transfer or bill-payment payload,
- checked against the payloads already in the queue for duplicates.

The original gallery files are never moved, renamed or deleted.

### 2. Review the queue

The review screen lists every imported image with the values the QR actually contains:

```
Payment queue                                    4 QR codes

QR in queue 2                Queue total        ฿1,250.00

Ready to pay        2
Invalid             1
Duplicates          0

01  500.00.png · 081-234-5678 · PromptPay            ฿500.00   READY
02  1250.00.png · METROPOLITAN WATERWORKS · Bill…   ฿1,250.00  DUPLICATE
03  broken.png — No QR code could be read from…         —      INVALID
04  750.00.png · 081-234-5678 · PromptPay            ฿750.00   READY
```

Invalid and duplicate images stay visible with their reason and are excluded from the queue. Nothing is silently skipped and no amount, recipient or reference is ever invented.

### 3. Start the queue

**Start Queue** first shows a confirmation summary — QR codes to pay, total, invalid, duplicates — and only **Start** begins the run.

### 4. Work through it one QR at a time

For the current item the screen shows the amount, recipient, reference, QR format and the QR image itself, plus:

- **Open / Share QR** — hands the stored QR image to the app you pick (typically your banking app) through a normal Android share intent. If you already paid it outside the app, use **I already paid this one in my banking app** instead.
- Then the app asks: *Have you completed this payment?* with **Payment successful**, **Payment failed** and **Something went wrong**.

Only your explicit answer moves an item forward. Sharing, opening or displaying a QR never marks it paid. **Something went wrong** marks the result `UNKNOWN`: the queue stops there and waits for you to check your banking app and record what really happened — it is never retried automatically.

Progress is always on screen:

```
Payment 2 / 4
Completed 1 · ฿500.00        Remaining 2 · ฿750.00
```

### 5. Finish

When every payable item has a result you get the final summary: completed, successful, failed, unknown, excluded invalid/duplicates, total confirmed paid and the queue total — all computed from the queue's own state.

### Interruptions

The queue (id, items, statuses, decoded values, image references and your recorded results) is persisted after every change. If the app is killed or backgrounded while a payment was in flight, that item comes back as `UNKNOWN` and must be resolved by you. A payment is never marked successful just because the app restarted.

## Repository layout

```
qr-queue-app/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml            (no permissions; FileProvider for QR sharing)
│       │   ├── java/com/enjirad/qrqueue/
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/                     QueueRepository, QrImageFiles, QrShare
│       │   │   ├── domain/                   parser, decoder, validation, queue state machine
│       │   │   └── ui/                       QueueRoute, QueueScreen, QueueViewModel, theme
│       │   └── res/                          strings, colors, themes, launcher icon, file_paths
│       └── test/java/com/enjirad/qrqueue/domain/
├── gradle/libs.versions.toml                 (pinned, compatible tool versions)
├── .github/workflows/android.yml             (tests + lint + APK verification + artifact)
├── AI_RULES.md
└── AI_HANDOFF.md
```

## Build

Requirements: JDK 17 and Android SDK 35 (Android Studio installs both).

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew lintDebug           # Android lint report
./gradlew assembleDebug       # debug APK
```

APK output:

```
app/build/outputs/apk/debug/app-debug.apk
```

Note: `gradle/wrapper/gradle-wrapper.jar` is a binary file. The CI workflow regenerates it with `gradle wrapper --gradle-version 8.11.1` before building, so a missing jar never blocks a build.

## Install the debug APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Continuous integration and the APK artifact

`.github/workflows/android.yml` runs on every push, pull request and manual dispatch: unit tests, lint, `assembleDebug`, APK existence and non-zero-size checks, APK inspection, then upload as the artifact **`qr-payment-queue-debug-apk`** with `if-no-files-found: error`.

## Security and payment safety model

- The app never stores or types bank PINs, passwords, OTPs, card PINs or biometric credentials.
- The app never automates bank screens, never uses Accessibility to click anything, and never makes hidden bank API calls.
- Payment confirmation always happens in your own banking app, and the app always asks you for the result.
- A payment is never marked `PAID` just because a QR was opened, shared or handed over — only your explicit confirmation sets a result.
- An `UNKNOWN` result blocks the queue and is never retried automatically.
- Importing uses Android's photo picker and the app's own private storage; no runtime permissions and no broad storage access are requested.

### Banking confirmation limitation

The app cannot know by itself whether a payment completed in a banking app. Unless an official, supported bank integration exists, V2 deliberately treats every result as user-confirmed:

```
QR Payment Queue → Show/share QR → You pay in your banking app → You record the result → Next QR
```

A future verified reconciliation mechanism (official bank API) could be added as a separate, explicitly supported feature.

## Payment status model

Progress states: `DISCOVERED → DECODED → VALIDATED → READY → SUBMITTED → WAITING_CONFIRMATION → SUCCESS → RECONCILED / PAID`

Error states: `INVALID`, `RECIPIENT_MISMATCH`, `AMOUNT_MISMATCH`, `ORDER_NOT_FOUND`, `DUPLICATE`, `EXPIRED`, `PAYMENT_FAILED`, `UNKNOWN`

Validation issues: `UNREADABLE_IMAGE`, `QR_NOT_FOUND`, `MALFORMED_PAYLOAD`, `CRC_MISMATCH`, `UNSUPPORTED_PAYLOAD`, `MISSING_PAYMENT_INFO`, `DUPLICATE_PAYLOAD`

## Supported QR format

Thai EMVCo merchant-presented QR payloads:

- PromptPay credit transfer (tag 29, AID `A000000677010111`): mobile number, national/tax ID, e-Wallet ID
- PromptPay / Thai QR bill payment (tag 30, AID `A000000677010112`): biller ID and references
- The scheme is identified by the application ID inside the merchant account block, so a bill-payment block issued under tag 29 is still parsed correctly; an unknown AID, currency or country is rejected as unsupported instead of guessed at
- Tags read: `00`, `01`, `53` (THB), `54` (amount), `58` (TH), `59`, `60`, `62`, `63` (CRC-16/CCITT-FALSE)

A QR without an amount (static PromptPay) is valid and stays without an amount: the app shows no invented figure and your banking app decides what to pay.

## Roadmap

| Version | Scope | Status |
| --- | --- | --- |
| V0.1 | Native Android foundation, APK build, CI | delivered in source |
| V0.2 | Multi-image photo-picker import, app-private image storage | delivered in source |
| V0.3 | Real QR decoding (ZXing) + EMVCo PromptPay parsing | delivered in source |
| V0.4 | Validation, duplicate detection, queue review | delivered in source |
| V0.5 | Sequential queue, share hand-off, explicit confirmation, persistence, summary | delivered in source |
| V1.0 | Optional verified reconciliation with an official bank/API integration | planned |
