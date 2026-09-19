# QR Payment Queue

Native Android application that lets the user select a banking app, queue QR
payment screenshots, and hand each image straight to that bank, one image at a
time.

> This repository contains a native Android application only. There is no web app,
> no PWA and no WebView wrapper anywhere in this project. The build output is a
> real APK produced by Gradle and by GitHub Actions.

## The one thing to understand first

**QR Payment Queue does NOT decode QR codes.**

It manages an image queue and hands QR images to the banking app you choose. You
verify the recipient and amount inside your bank app, pay inside it, and then
confirm completion manually back in QR Payment Queue.

There is no QR decoder, no QR parsing, no recipient/amount/merchant validation,
no duplicate-payload detection and no multi-QR detection anywhere in this app.
That work belongs to your banking app, which is the only component allowed to
read the QR and to decide what the payment is.

## Purpose

QR Payment Queue walks you through a batch of QR payment screenshots, one image
at a time:

1. **Select a banking app** (K PLUS, SCB EASY, Krungthai NEXT, or Bualuang
   mBanking) — this is saved and survives every app restart.
2. Import **several QR screenshots at once** from Android's photo picker. (The
   import button is disabled until you select a bank.)
3. Return to the home screen **by itself** as soon as the import is finished.
4. Pick the image you want to pay next and tap **แชร์ไปธนาคาร**.
5. The selected bank app opens **directly** — no "share with..." chooser.
6. Complete the payment in the bank app: it reads the QR, shows the recipient
   and amount, takes your PIN / biometric, confirms the transaction and produces
   the e-Slip.
7. Come back and answer **ทำรายการเสร็จแล้ว** (done) or **ลองอีกครั้ง** (not done
   yet). Only you can say a payment is done.
8. Pick the next image and repeat.

## Platform and technology

| Concern | Choice |
| --- | --- |
| Platform | Native Android (min SDK 26, target SDK 35) |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | Single activity, AndroidX, ViewModel + StateFlow, domain/UI separation |
| Build | Gradle (Kotlin DSL), Android Gradle Plugin 8.7.3 |
| Package | `com.enjirad.qrqueue` |
| App version | 0.4.2 (versionCode 6) |
| QR decoding | **None.** No QR library is used or depended on. |
| Persistence | App-private JSON queue + SharedPreferences for bank selection + copied images |
| CI | GitHub Actions (`testDebugUnitTest`, `lintDebug`, `assembleDebug`, APK artifact) |

## The V0.4.2 workflow

```
SELECT BANK  (persisted — survives every restart)
    ↓
IMPORT IMAGES  (1 / 5 … 5 / 5, auto-returns to home)
    ↓
SELECT ONE IMAGE
    ↓
DIRECT TO BANK  (ACTION_SEND addressed to selected bank's package, no chooser)
    ↓
USER COMPLETES PAYMENT IN BANK APP
    ↓
RETURN
    ↓
USER CONFIRMS  (ทำรายการเสร็จแล้ว / ลองอีกครั้ง)
    ↓
NEXT IMAGE
```

### 1. Select a banking app

The home screen always shows the **ธนาคารสำหรับชำระเงิน** (payment bank) card.
On first install it says "ยังไม่ได้เลือกธนาคาร" and the import button is
disabled. Tap **เลือกธนาคาร** to open the selection dialog, which probes the
device in real time and shows the status of each known bank:

- **พร้อมใช้งาน** (ready) — installed and advertises image sharing.
- **ไม่รองรับการส่งรูป** (not supported) — installed but no matching image
  share activity found.
- **ไม่ได้ติดตั้ง** (not installed) — the package was not found.

Your selection is persisted immediately so it survives every form of process
death. You can change it at any time with **เปลี่ยนธนาคาร**.

### 2. Import QR images

Tap **+ เพิ่มรูป QR** (disabled until a bank is selected). Android's own photo
picker opens and you can select several screenshots at once. Each selected image
is copied into app-private storage and added to the queue as a `QUEUED` item.

- The original gallery file is never moved, renamed or deleted.
- The image is **not** read, decoded, validated or inspected in any way.
- As soon as every image has been handled **and the queue is saved**, the import
  screen closes and the queue is shown — no Continue / Done tap needed.

### 3. Share one image at a time

Pick the image you want to pay next and tap **แชร์ไปธนาคาร**. The app builds a
standard `ACTION_SEND` image share addressed to the selected bank's Android
package, so the bank opens directly — no chooser, no app picking.

```
QR image (app-private copy)
        ↓
FileProvider  →  content:// URI  +  FLAG_GRANT_READ_URI_PERMISSION
        ↓
Intent.ACTION_SEND + EXTRA_STREAM + image MIME + setPackage(selectedBank)
        ↓
Bank app (opens directly)
```

While the hand-off is launching the item is `SHARING`; once the bank received it
the item becomes `WAITING_USER`.

### 4. Pay in the bank app

The bank app controls the whole payment: it reads the QR, shows the recipient
and amount, takes your PIN / biometric, confirms the transaction and produces
the e-Slip. This app does none of that and cannot see any of it.

### 5. Confirm the result yourself

When you come back, the app asks: *ทำรายการสำหรับรูปนี้เสร็จแล้วหรือยัง?*

- **ทำรายการเสร็จแล้ว** — the item becomes `COMPLETED`. The next image is yours
  to pick; nothing is shared automatically.
- **ลองอีกครั้ง** — nothing is marked complete. The image stays retryable, and
  it is shared again only when you tap **แชร์ไปธนาคาร** yourself.

Returning to the app is **not** a payment success.

When every image is confirmed, the app shows **ทำรายการครบแล้ว** with the count
and a **กลับหน้าแรก** action.

## Queue states

| State | Meaning |
| --- | --- |
| `QUEUED` | The image is in the queue but has not been handed to the bank yet. |
| `SHARING` | The image is being handed to the bank app right now. |
| `WAITING_USER` | The bank app has the image; the app is waiting for you to pay and confirm. |
| `COMPLETED` | You pressed **ทำรายการเสร็จแล้ว**. The only finished state. |
| `FAILED` | A known error: the stored file is missing, the intent could not be built, or the bank did not accept the image. Nothing reached the bank. |
| `UNKNOWN` | The result could not be determined (the app was killed mid-payment). Only you can resolve it. |

```
QUEUED → SHARING → WAITING_USER → COMPLETED
               ↘ FAILED
WAITING_USER → UNKNOWN
FAILED / UNKNOWN → QUEUED        (only by an explicit user retry)
```

## Double-payment protection

- Only one image may own the payment hand-off at a time.
- A hand-off starts only from a deliberate user action.
- An image that already reached the bank (`WAITING_USER`) is not shared again
  without an explicit confirmation dialog.
- `UNKNOWN` is never retried or completed automatically.

## Process death

If the app is killed while an image is `SHARING` or `WAITING_USER`, that item
is restored as `UNKNOWN`. The queue waits on that image until you check the bank
app and record what actually happened.

## Known banking apps

The app probes these banking packages at runtime:

| Bank | Package | Notes |
| --- | --- | --- |
| K PLUS | `com.kasikornbank.kplus` | KBank |
| SCB EASY | `com.scb.BankApp` | Siam Commercial Bank |
| Krungthai NEXT | `com.krungthai.nextbanking` | Bank of Ayudhya |
| Bualuang mBanking | `com.bblmobilebanking` | Bangkok Bank |

Package names come from each app's Google Play listing. The runtime probe
(`BankTarget.query`) checks the real device; the list above is the candidate
set. The `<queries>` block in the manifest ensures Android 11+ can see these
packages.

## Security and payment safety model

- The app never stores or types bank PINs, passwords, OTPs or biometric
  credentials.
- The app never automates bank screens, never uses Accessibility, and never makes
  hidden bank API calls.
- Payment confirmation always happens in the bank app, and the app always asks
  you for the result.
- An `UNKNOWN` result blocks the queue and is never retried automatically.
- Bank selection is stored in SharedPreferences (app-private); uninstalling
  removes it.

## Repository layout

```
qr-queue-app/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/enjirad/qrqueue/
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/          QueueRepository, QrImageFiles, QrShare, BankTarget
│       │   │   ├── domain/        QueueItem, PaymentStatus, PaymentQueue, QueueImport, BankInfo
│       │   │   └── ui/            QueueRoute, QueueScreen, QueueViewModel, theme
│       │   └── res/               strings, colors, themes, launcher icon, file_paths
│       └── test/java/             pure JVM unit tests
├── gradle/libs.versions.toml
├── .github/workflows/android.yml
├── docs/REAL_DEVICE_TEST.md
├── AI_RULES.md
└── AI_HANDOFF.md
```

## Build

Requirements: JDK 17 and Android SDK 35.

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## CI and APK artifact

GitHub Actions runs on every push: unit tests, lint, assembleDebug, APK
verification, then upload as **`qr-payment-queue-v0.4.2-debug`**. A failing
test, lint run or build fails the workflow.

## Verification status

- Build, 76 unit tests, lint and the APK are verified in CI on every push.
- Bank selection, upload gate and share flow are **NOT YET VERIFIED ON A REAL
  DEVICE**. The test plan is in
  [`docs/REAL_DEVICE_TEST.md`](docs/REAL_DEVICE_TEST.md).

## Known limitations

1. **Bank confirmation stays manual.** The app cannot know whether a transaction
   completed; you record every result.
2. **Bank package names come from Google Play.** If a bank changes its package
   name, the app reports "not found" and you select another.
3. **Not all banking apps support image sharing.** `INSTALLED_NOT_ADVERTISED`
   means the bank is installed but its share activity was not found. The app
   still tries the hand-off and reports the result honestly.
4. **Changing banks does not affect queued items.** Items in `WAITING_USER` or
   `UNKNOWN` reference the bank at share time. The next share uses the current
   bank.
5. No instrumented UI tests; no emulator in CI.

## Roadmap

| Version | Scope | Status |
| --- | --- | --- |
| V0.1 | Foundation, APK, CI | delivered |
| V0.2 | Photo-picker import, app-private storage | delivered |
| V0.3 | QR decoding (ZXing) + EMVCo parsing | superseded |
| V0.4 (0.4.0) | Image queue + K PLUS hand-off | superseded |
| V0.4.1 (0.4.1) | Home-screen queue, direct K PLUS, no chooser | superseded |
| **V0.4.2 (0.4.2)** | **Bank selection, persistent bank, upload gate, generalized share** | **delivered** |
| V1.0 | Optional verified reconciliation with official bank API | planned |
| — | Real-device verification of bank selection and share flow | not yet done |
