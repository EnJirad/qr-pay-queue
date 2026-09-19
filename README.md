# QR Payment Queue

Native Android application that queues QR payment screenshots and hands each
image to K PLUS through Android's own share sheet.

> This repository contains a native Android application only. There is no web app,
> no PWA and no WebView wrapper anywhere in this project. The build output is a
> real APK produced by Gradle and by GitHub Actions.

## The one thing to understand first

**QR Payment Queue does NOT decode QR codes.**

It manages an image queue and hands QR images to K PLUS through the Android
share sheet. The user verifies the recipient and amount inside K PLUS, pays
inside K PLUS, and then confirms completion manually back in QR Payment Queue.

There is no QR decoder, no QR parsing, no recipient/amount/merchant validation,
no duplicate-payload detection and no multi-QR detection anywhere in this app.
That work belongs to K PLUS, which is the only component allowed to read the QR
and to decide what the payment is.

## Purpose

QR Payment Queue walks you through a batch of QR payment screenshots in order:

1. Import **several QR screenshots at once** from Android's photo picker.
2. Copy those images into the app's own private storage (your gallery originals
   are never moved, modified or deleted).
3. Build an ordered **image queue**.
4. **Share** the current image to K PLUS through the standard Android share
   sheet.
5. Complete the payment in K PLUS; K PLUS reads the QR, shows the payment, and
   handles PIN / biometric / confirmation and the e-Slip.
6. Come back and tell the app what happened: **ทำรายการเสร็จแล้ว** (done) or
   **ยังไม่เสร็จ** (not yet). Only then does the queue move on.

The app prepares, queues and tracks payment tasks. It never performs a payment,
and it never decides on its own that a payment succeeded.

## Platform and technology

| Concern | Choice |
| --- | --- |
| Platform | Native Android (min SDK 26, target SDK 35) |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | Single activity, AndroidX, ViewModel + StateFlow, domain/UI separation |
| Build | Gradle (Kotlin DSL), Android Gradle Plugin 8.7.3 |
| Package | `com.enjirad.qrqueue` |
| App version | 0.4.0 (versionCode 4) |
| QR decoding | **None.** No QR library is used or depended on. |
| Persistence | App-private JSON state file + copied images in `filesDir/qrqueue/` |
| CI | GitHub Actions (`testDebugUnitTest`, `lintDebug`, `assembleDebug`, APK artifact) |

## The V0.4 workflow

```
Photo Picker
    ↓
Selected image URIs
    ↓
Copy each image into app-private storage
    ↓
Create QueueItem (QUEUED) and persist the queue
    ↓
Share the current image through Android's share sheet
    ↓
K PLUS  (reads the QR, shows the payment, PIN / biometric, confirm, e-Slip)
    ↓
Return to QR Payment Queue
    ↓
User answers: ทำรายการเสร็จแล้ว / ยังไม่เสร็จ
    ↓
Next image
```

### 1. Import QR images

Tap **Import QR images**. Android's own photo picker opens and you can select
several screenshots at once (no folder picking, no storage permission, no device
scan). Each selected image is copied into `filesDir/qrqueue/images/` and added to
the queue as a `QUEUED` item.

- The original gallery file is never moved, renamed or deleted.
- The image is **not** read, decoded, validated or inspected in any way.
- If an image cannot be copied, **no queue item is created for it** and the app
  says so instead of pretending to hold an image it does not have.

### 2. Work the queue

The queue lists every image in order with its status. The current image is the
first one you have not completed, and it is the only one with actions. Supported
selections: 1 image, 3 images, 10 images — any count the picker allows.

### 3. Share the current image

Tap **Share image to K PLUS**. The app hands the stored image to Android's share
sheet with a standard `ACTION_SEND` intent. You choose K PLUS there (the app
never launches K PLUS by package name).

While the share is being handed off the item is `SHARING`; once the share sheet
opens it becomes `WAITING_USER`.

### 4. Pay in K PLUS

K PLUS controls the whole payment: it reads the QR, shows the recipient and
amount, lets you enter or confirm the amount, takes your PIN / biometric,
confirms the transaction and produces the e-Slip. This app does none of that and
cannot see any of it.

### 5. Confirm the result yourself

When you come back, the app asks: *ทำรายการสำหรับภาพนี้เสร็จแล้วหรือยัง?*
(Have you completed this payment?)

- **ทำรายการเสร็จแล้ว** — the item becomes `COMPLETED` and the next image
  becomes the current one.
- **ยังไม่เสร็จ** — nothing advances. The item stays current and stays
  retryable; you can share it again (with a double-payment warning) or come back
  to it later.

Returning to the app is **not** a payment success, and opening or sharing an
image is never recorded as paid.

## Queue states

The state machine is deliberately small and explicit:

| State | Meaning |
| --- | --- |
| `QUEUED` | The image is in the queue but has not been shared yet. |
| `SHARING` | The image is being handed to Android's share system right now. |
| `WAITING_USER` | The image was shared and the app is waiting for the user to pay in K PLUS and confirm. |
| `COMPLETED` | The user pressed **ทำรายการเสร็จแล้ว**. This is the only finished state. |
| `FAILED` | A known error: the stored file could not be read, a share intent could not be built, or the share flow could not be opened. Nothing was handed off. |
| `UNKNOWN` | The payment result could not be determined (the app was killed while a payment was in flight). The queue stops and only the user can resolve it. |

```
QUEUED → SHARING → WAITING_USER → COMPLETED
               ↘ FAILED
WAITING_USER → UNKNOWN
FAILED / UNKNOWN → QUEUED        (only by an explicit user retry)
```

`FAILED` and `UNKNOWN` block the queue rather than being skipped. The current
image is always the first one that is not `COMPLETED`.

## Image files

- Private storage: copies live in `filesDir/qrqueue/images/`, never in shared
  storage.
- Missing files: an item whose stored image has disappeared is shown as `FAILED`
  with a clear reason instead of crashing.
- Orphan cleanup: image copies that no queue item references (for example after
  an interrupted import) are swept. Gallery originals are never involved.
- Duplicate selection: selecting the same file twice in one selection creates
  one item, not two.
- Restart recovery: the queue, statuses, order and image references survive
  activity recreation, backgrounding and a full restart.

## The Android share hand-off

```
QR image (app-private copy)
        ↓
FileProvider  →  content:// URI  +  FLAG_GRANT_READ_URI_PERMISSION
        ↓
Intent.ACTION_SEND with EXTRA_STREAM and type image/* (or the real image type)
        ↓
Android share sheet (the resolver picks the target — no package is forced)
        ↓
K PLUS
        ↓
K PLUS reads the QR → USER authenticates → USER confirms in K PLUS
        ↓
Back in QR Payment Queue the user records the result
        ↓
Next image
```

Details that matter:

- The image is shared as a `content://` URI through `FileProvider` with a
  temporary read grant — never `file://`, which banking apps cannot read.
- The MIME type is the real type of the stored image (for example `image/png`);
  if nothing on the device handles that exact type, the share falls back to
  `image/*`.
- If no app can receive an image at all, sharing is disabled and explained
  instead of failing silently.
- If K PLUS is not installed, or is installed but does not advertise an image
  share filter, the screen says so in plain language.

What the app deliberately does **not** do: enter a PIN, password or OTP, read
them, touch biometric prompts, click anything inside K PLUS, use Accessibility to
operate the bank app, scrape bank screens, call bank APIs, or mark a payment as
paid because a share succeeded.

## Double-payment protection

The same QR image is never handed off twice without the user knowing.

- A share starts only from a deliberate user action; nothing is shared
  automatically after an activity recreation, a process restart, an app resume
  or a return from K PLUS.
- An image that was already handed off (`WAITING_USER`) is not shared again
  without an explicit **Share again** confirmation that warns about paying the
  same bill twice.
- A `FAILED` share never reached the share sheet, so retrying it is allowed.
- An `UNKNOWN` result is never retried and never completed automatically: the
  user must resolve it first, and only then can the image be shared again.
- A request to hand off an image is transient UI state, so it is never replayed
  after a restart.

## Process death

If the app is killed while an image is `SHARING` or `WAITING_USER`, the item is
restored as `UNKNOWN`: the app does not assume the payment succeeded and does not
assume it failed, and it does not retry. The queue stops on that image until the
user checks K PLUS and records what actually happened.

## Security and payment safety model

- The app never stores or types bank PINs, passwords, OTPs, card PINs or
  biometric credentials.
- The app never automates bank screens, never uses Accessibility to click
  anything, and never makes hidden bank API calls.
- Payment confirmation always happens in K PLUS, and the app always asks the
  user for the result.
- Returning to the app is not a payment result, and the app never claims one.
- An `UNKNOWN` result blocks the queue and is never retried automatically.
- If the queue cannot be written to disk, the app says so instead of pretending
  the result was recorded.
- Nothing sensitive is logged: the app does not log image contents, credentials
  or payment data, and backs up nothing (`allowBackup=false`).
- Importing uses Android's photo picker and the app's own private storage; no
  runtime permissions and no broad storage access are requested.
- The only exported component is the launcher activity; the `FileProvider` is
  `exported=false` and only shares the app's own queue image directory, per use.

### Payment confirmation limitation

The app cannot know by itself whether a payment completed in K PLUS. There is no
supported bank API in use, so every result is user-confirmed:

```
QR Payment Queue → Share image → K PLUS reads the QR and pays → User confirms → Next image
```

This is intentional. A future verified reconciliation mechanism (official bank
API) could be added as a separate, explicitly supported feature.

## Repository layout

```
qr-queue-app/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml            (no permissions; FileProvider for image sharing)
│       │   ├── java/com/enjirad/qrqueue/
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/                     QueueRepository, QrImageFiles, QrShare, ShareTargets
│       │   │   ├── domain/                   QueueItem, PaymentStatus, PaymentQueue, QueueImport
│       │   │   └── ui/                       QueueRoute, QueueScreen, QueueViewModel, theme
│       │   └── res/                          strings, colors, themes, launcher icon, file_paths
│       └── test/java/com/enjirad/qrqueue/    pure JVM unit tests
├── gradle/libs.versions.toml                 (pinned, compatible tool versions; no QR library)
├── .github/workflows/android.yml             (tests + lint + APK verification + artifact)
├── docs/REAL_DEVICE_TEST.md                  (K PLUS device test plan and status)
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

Note: `gradle/wrapper/gradle-wrapper.jar` is a binary file. The CI workflow
regenerates it with `gradle wrapper --gradle-version 8.11.1` before building, so
a missing jar never blocks a build.

## Install the debug APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Continuous integration and the APK artifact

`.github/workflows/android.yml` runs on every push, pull request and manual
dispatch: unit tests, lint, `assembleDebug`, APK existence and non-zero-size
checks, APK inspection, then upload as the artifact
**`qr-payment-queue-v0.4.0-debug`** with `if-no-files-found: error`. A failing
test, lint run or build fails the workflow; nothing is hidden with `|| true` or
`continue-on-error`.

## Verification status

- Build, unit tests, lint and the APK are verified in GitHub Actions on every
  push.
- K PLUS behaviour is **NOT YET VERIFIED ON A REAL DEVICE**. The share mechanism
  follows Android's documented image-sharing path, but nothing in this
  repository claims a real K PLUS transaction has been observed through it. The
  test plan and the current status live in
  [`docs/REAL_DEVICE_TEST.md`](docs/REAL_DEVICE_TEST.md).

## Known limitations

1. **Bank confirmation stays manual.** Without an official supported bank
   integration the app cannot know whether a transaction completed; the user
   records every result. This is intentional.
2. **K PLUS cannot be verified from this repository.** It needs a real device and
   a real K PLUS install.
3. **K PLUS must accept a shared image.** Whether K PLUS reads a QR out of a
   shared screenshot is KBank's behaviour, not something this app controls. If it
   does not, that is a finding about K PLUS, not a bug this app can work around.
4. K PLUS detection relies on the known package name `com.kasikornbank.kplus`. If
   KBank ships under a different package, the app reports "not found" while the
   share sheet itself still works normally; only the hint would be wrong.
5. Only Thai-localised payment-critical prompts; the rest of the UI copy is
   English.
6. No instrumented UI tests; there is no emulator in CI.
7. Lint fails the build on lint errors (`abortOnError=true`); it runs in CI on
   every push.
8. Queue state and images are app-private; uninstalling removes them.

## Roadmap

| Version | Scope | Status |
| --- | --- | --- |
| V0.1 | Native Android foundation, APK build, CI | delivered in source |
| V0.2 | Multi-image photo-picker import, app-private image storage | delivered in source |
| V0.3 | QR decoding (ZXing) + EMVCo PromptPay parsing | superseded by V0.4 |
| **V0.4 (0.4.0)** | **Image queue + K PLUS share hand-off + user confirmation. QR decoding and validation removed.** | delivered in source |
| V1.0 | Optional verified reconciliation with an official bank/API integration | planned |
| — | Real-device verification of the K PLUS share flow | not yet done — tracked in `docs/REAL_DEVICE_TEST.md` |
