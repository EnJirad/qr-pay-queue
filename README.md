# QR Payment Queue

Native Android application that queues QR payment screenshots and hands each
image straight to K PLUS, one image at a time.

> This repository contains a native Android application only. There is no web app,
> no PWA and no WebView wrapper anywhere in this project. The build output is a
> real APK produced by Gradle and by GitHub Actions.

## The one thing to understand first

**QR Payment Queue does NOT decode QR codes.**

It manages an image queue and hands QR images to K PLUS. The user verifies the
recipient and amount inside K PLUS, pays inside K PLUS, and then confirms
completion manually back in QR Payment Queue.

There is no QR decoder, no QR parsing, no recipient/amount/merchant validation,
no duplicate-payload detection and no multi-QR detection anywhere in this app.
That work belongs to K PLUS, which is the only component allowed to read the QR
and to decide what the payment is.

The app never enters a PIN, password, OTP or biometric, never clicks anything
inside K PLUS, never reads K PLUS screens and never calls a bank API.

## Purpose

QR Payment Queue walks you through a batch of QR payment screenshots, one image
at a time:

1. Import **several QR screenshots at once** from Android's photo picker.
2. Copy all of them into the app's own private storage (your gallery originals are
   never moved, modified or deleted).
3. Return to the home screen **by itself** as soon as the import is finished.
4. Pick the image you want to pay next and tap **แชร์ไป K PLUS**.
5. K PLUS opens **directly** — no "share with..." chooser, no app picking.
6. Complete the payment in K PLUS: it reads the QR, shows the recipient and
   amount, takes your PIN / biometric, confirms the transaction and produces the
   e-Slip.
7. Come back and answer **ทำรายการเสร็จแล้ว** (done) or **ลองอีกครั้ง** (not done
   yet). Only you can say a payment is done.
8. Pick the next image and repeat.

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
| App version | 0.4.1 (versionCode 5) |
| QR decoding | **None.** No QR library is used or depended on. |
| Persistence | App-private JSON state file + copied images in `filesDir/qrqueue/` |
| CI | GitHub Actions (`testDebugUnitTest`, `lintDebug`, `assembleDebug`, APK artifact) |

## The V0.4.1 workflow

```
SELECT IMAGES
    ↓
IMPORT ALL  (1 / 5 … 5 / 5, copied into app-private storage)
    ↓
RETURN HOME  (automatic — no Continue / Done tap)
    ↓
SELECT ONE IMAGE
    ↓
DIRECT K PLUS  (ACTION_SEND addressed to com.kasikornbank.kplus, no chooser)
    ↓
USER COMPLETES PAYMENT IN K PLUS
    ↓
RETURN
    ↓
USER CONFIRMS  (ทำรายการเสร็จแล้ว / ลองอีกครั้ง)
    ↓
NEXT IMAGE
```

### 1. Import QR images

Tap **+ เพิ่มรูป QR**. Android's own photo picker opens and you can select several
screenshots at once (no folder picking, no storage permission, no device scan).
Each selected image is copied into `filesDir/qrqueue/images/` and added to the
queue as a `QUEUED` item.

- The original gallery file is never moved, renamed or deleted.
- The image is **not** read, decoded, validated or inspected in any way.
- If an image cannot be copied, **no queue item is created for it** and the app
  says so ("นำเข้าได้ 4 จาก 5 รูป") instead of pretending to hold an image it does
  not have.

### 2. The import screen ends by itself

While images are being copied the app shows `1 / 5 … 5 / 5`. As soon as the last
image has been handled **and the queue is saved**, that screen closes and the
queue is shown. There is no Continue, Done or Next button, and the app never
stays on a completed "5 / 5" step.

### 3. The home screen is the queue

The home screen lists every image with its status and the counts (total /
completed / remaining). You choose which image to pay next — the app does not
force one order — and each image can be handed to K PLUS from its own
**แชร์ไป K PLUS** button.

Only **one payment hand-off can be in progress at a time**: while an image is
waiting for your answer (or its result is unknown), the other images cannot be
handed over, so two payments can never be started by accident.

### 4. Direct hand-off to K PLUS

Tapping **แชร์ไป K PLUS** builds a standard image share and addresses it to K PLUS
by package, so Android opens K PLUS straight away. The Android "share with…"
chooser is **not** shown, because the workflow fixes the destination: the image
goes to K PLUS.

```
QR image (app-private copy)
        ↓
FileProvider  →  content:// URI  +  FLAG_GRANT_READ_URI_PERMISSION
        ↓
Intent.ACTION_SEND + EXTRA_STREAM + image MIME type + setPackage(com.kasikornbank.kplus)
        ↓
K PLUS (opens directly)
```

While the hand-off is being launched the item is `SHARING`; once K PLUS received
it the item becomes `WAITING_USER`.

### 5. Pay in K PLUS

K PLUS controls the whole payment: it reads the QR, shows the recipient and
amount, lets you enter or confirm the amount, takes your PIN / biometric,
confirms the transaction and produces the e-Slip. This app does none of that and
cannot see any of it.

### 6. Confirm the result yourself

When you come back, the app asks: *ทำรายการสำหรับรูปนี้เสร็จแล้วหรือยัง?*

- **ทำรายการเสร็จแล้ว** — the item becomes `COMPLETED`, and that image is never
  sent to K PLUS again. The next image is yours to pick; nothing is shared
  automatically.
- **ลองอีกครั้ง** — nothing is marked complete and the queue does not move on.
  That image stays retryable, and it is handed to K PLUS again only when you tap
  **แชร์ไป K PLUS** yourself.

Returning to the app is **not** a payment success, and opening, sharing or
receiving an image is never recorded as paid.

When every image is confirmed, the app shows **ทำรายการครบแล้ว** with
`3 / 3 รายการเสร็จสิ้น` and a **กลับหน้าแรก** action. It never opens K PLUS by
itself.

## Queue states

The state machine is deliberately small and explicit:

| State | Meaning |
| --- | --- |
| `QUEUED` | The image is in the queue but has not been handed to K PLUS yet. |
| `SHARING` | The image is being handed to K PLUS right now. |
| `WAITING_USER` | K PLUS has the image and the app is waiting for the user to pay and confirm. |
| `COMPLETED` | The user pressed **ทำรายการเสร็จแล้ว**. This is the only finished state. |
| `FAILED` | A known error: the stored file could not be read, an intent could not be built, or K PLUS did not accept the image. Nothing reached K PLUS. |
| `UNKNOWN` | The payment result could not be determined (the app was killed while a payment was in flight). Only the user can resolve it. |

```
QUEUED → SHARING → WAITING_USER → COMPLETED
               ↘ FAILED
WAITING_USER → UNKNOWN
FAILED / UNKNOWN → QUEUED        (only by an explicit user retry)
```

- `SHARING`, `WAITING_USER` and `UNKNOWN` each mean *this image owns the payment
  hand-off*; only one image may be in one of those states at a time.
- `FAILED` never reached K PLUS, so it does not block the rest of the queue and
  can be retried by the user.
- `UNKNOWN` is never retried and never completed automatically.

## Image files

- Private storage: copies live in `filesDir/qrqueue/images/`, never in shared
  storage.
- Missing files: an item whose stored image has disappeared is shown as `FAILED`
  with a clear reason instead of crashing.
- Orphan cleanup: image copies that no queue item references (for example after
  an interrupted import) are swept. Gallery originals are never involved.
- Duplicate selection: selecting the same file twice in one selection creates one
  item, not two.
- Restart recovery: the queue, statuses, order and image references survive
  activity recreation, backgrounding and a full restart.

## Double-payment protection

The same QR image is never handed to K PLUS twice without the user knowing.

- A hand-off starts only from a deliberate user action; nothing is shared
  automatically after an activity recreation, a process restart, an app resume or
  a return from K PLUS.
- An image that already reached K PLUS (`WAITING_USER`) is not sent again without
  an explicit confirmation dialog that warns about paying the same bill twice.
- Only one image can own the hand-off at a time, so a second payment cannot be
  started while one is waiting.
- A `FAILED` hand-off never reached K PLUS, so retrying it is allowed.
- An `UNKNOWN` result is never retried and never completed automatically: the
  user must resolve it first, and only then can the image be handed over again.
- A request to hand off an image is transient UI state, so it is never replayed
  after a restart.

## Process death

If the app is killed while an image is `SHARING` or `WAITING_USER`, that item is
restored as `UNKNOWN`: the app does not assume the payment succeeded and does not
assume it failed, and it does not retry. The queue waits on that image until the
user checks K PLUS and records what actually happened.

## If K PLUS does not accept the hand-off

Android is asked to open K PLUS directly. If no activity in K PLUS accepts the
image intent, the launch fails and the app records a `FAILED` item with the
reason "K PLUS did not accept the shared image". It does **not** fall back to the
"share with…" chooser, and it does not add an unsafe workaround — no
Accessibility automation, no screen scraping, no private K PLUS API, no root, no
credential injection, no simulated taps. That finding belongs in
[`docs/REAL_DEVICE_TEST.md`](docs/REAL_DEVICE_TEST.md), not in a hack.

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
QR Payment Queue → Direct hand-off to K PLUS → K PLUS reads the QR and pays
                 → User confirms in QR Payment Queue → Next image
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
│       │   ├── AndroidManifest.xml            (no permissions; FileProvider for image hand-off)
│       │   ├── java/com/enjirad/qrqueue/
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/                     QueueRepository, QrImageFiles, QrShare, KPlusTarget
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
./gradlew lintDebug           # Android lint (fails the build on lint errors)
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
**`qr-payment-queue-v0.4.1-debug`** with `if-no-files-found: error`. A failing
test, lint run or build fails the workflow; nothing is hidden with `|| true` or
`continue-on-error`.

## Verification status

- Build, unit tests, lint and the APK are verified in GitHub Actions on every
  push.
- K PLUS behaviour is **NOT YET VERIFIED ON A REAL DEVICE**. The direct hand-off
  follows Android's documented image-sharing path, but nothing in this repository
  claims K PLUS opened directly, accepted a shared image, read a QR or paid
  anything. The test plan and the current status live in
  [`docs/REAL_DEVICE_TEST.md`](docs/REAL_DEVICE_TEST.md).

## Known limitations

1. **Bank confirmation stays manual.** Without an official supported bank
   integration the app cannot know whether a transaction completed; the user
   records every result. This is intentional.
2. **K PLUS cannot be verified from this repository.** It needs a real device and
   a real K PLUS install.
3. **K PLUS must accept a direct image share.** Whether K PLUS reads a QR out of a
   shared screenshot is KBank's behaviour, not something this app controls. If it
   does not, that is a finding about K PLUS.
4. **The direct intent depends on the K PLUS package name.** The hand-off is
   addressed to `com.kasikornbank.kplus`. If KBank ships under a different
   package, the hand-off fails with a clear "K PLUS did not accept the shared
   image" message rather than silently opening the wrong app.
5. **A device without K PLUS cannot hand off at all**: the buttons are disabled
   and the reason is shown, instead of opening a chooser.
6. Only Thai-localised payment-critical prompts; the rest of the UI copy is Thai
   with English status names.
7. No instrumented UI tests; there is no emulator in CI.
8. Lint fails the build on lint errors (`abortOnError=true`); it runs in CI on
   every push.
9. Queue state and images are app-private; uninstalling removes them.

## Roadmap

| Version | Scope | Status |
| --- | --- | --- |
| V0.1 | Native Android foundation, APK build, CI | delivered in source |
| V0.2 | Multi-image photo-picker import, app-private image storage | delivered in source |
| V0.3 | QR decoding (ZXing) + EMVCo PromptPay parsing | superseded by V0.4 |
| V0.4 (0.4.0) | Image queue + K PLUS hand-off + user confirmation. QR decoding and validation removed. | superseded by V0.4.1 |
| **V0.4.1 (0.4.1)** | **Home screen as the queue, import returns home by itself, one-by-one direct hand-off to K PLUS, no Android chooser.** | delivered in source |
| V1.0 | Optional verified reconciliation with an official bank/API integration | planned |
| — | Real-device verification of the K PLUS hand-off | not yet done — tracked in `docs/REAL_DEVICE_TEST.md` |
