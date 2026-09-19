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

QR Payment Queue walks you through a batch of QR payment screenshots — one
**Payment Item** at a time, from three tabs:

```
หน้าแรก  |  ⚠️ ปัญหา  |  ✓ ชำระแล้ว
```

1. **Select a banking app** (K PLUS, SCB EASY, Krungthai NEXT, Bangkok Bank,
   krungsri, ttb touch, MyMo by GSB, CIMB THAI, UOB TMRW, Dime!, MAKE by KBank,
   Kept, or TrueMoney) — this is saved and survives every app restart.
2. Tap **+ เพิ่ม QR** and import **several QR screenshots at once** from
   Android's photo picker. (The button is disabled until you select a bank.)
   Exact duplicates are skipped; a partial import keeps what succeeded.
3. Return to the home screen **by itself** as soon as the import is finished.
4. **หน้าแรก** shows the next thing to do, in the required priority order:
   an unresolved result first, then a QR that must be replaced, then a failed
   item, then the next item ready to pay.
5. Tap **ชำระเงิน**. The selected bank app opens **directly** — no
   "share with..." chooser.
6. Complete the payment in the bank app: it reads the QR, shows the recipient
   and amount, takes your PIN / biometric, confirms the transaction and produces
   the e-Slip.
7. Come back and answer **ทำรายการเสร็จแล้ว** (done), **QR ใช้งานไม่ได้**
   (this QR cannot be used) or **ยังไม่แน่ใจ** (not sure yet). Only you can say a
   payment is done.
8. If the bank rejected the QR, the item moves to **ปัญหา** with a
   **เปลี่ยน QR** action: picking a new image replaces the QR of the *same*
   Payment Item, which returns to `READY`. The old QR stays in history.
9. Items you confirmed move to **ชำระแล้ว**. Pick the next item and repeat.

## Platform and technology

| Concern | Choice |
| --- | --- |
| Platform | Native Android (min SDK 26, target SDK 35) |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | Single activity, AndroidX, ViewModel + StateFlow, domain/UI separation |
| Build | Gradle (Kotlin DSL), Android Gradle Plugin 8.7.3 |
| Package | `com.enjirad.qrqueue` |
| App version | 0.5.0 (versionCode 7) |
| QR decoding | **None.** No QR library is used or depended on. |
| Persistence | App-private JSON queue + SharedPreferences for bank selection + copied images |
| CI | GitHub Actions (`testDebugUnitTest`, `lintDebug`, `assembleDebug`, APK artifact) |

## The V0.5 workflow

```
SELECT BANK  (persisted — survives every restart)
    ↓
IMPORT IMAGES  (1 / 5 … 5 / 5, duplicates skipped, auto-returns to home)
    ↓
หน้าแรก  →  NEXT ACTION  (problem → QR to replace → failed → ready)
    ↓
DIRECT TO BANK  (ACTION_SEND addressed to selected bank's package, no chooser)
    ↓
USER COMPLETES PAYMENT IN BANK APP
    ↓
RETURN
    ↓
USER ANSWERS  (ทำรายการเสร็จแล้ว / QR ใช้งานไม่ได้ / ยังไม่แน่ใจ)
    ↓
ชำระแล้ว  (confirmed)   or   ปัญหา  (needs a new QR / a decision)
    ↓
NEXT ITEM  (never opened automatically)
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
is copied into app-private storage and added to the queue as a `READY` item with
its own QR v1. An image whose bytes are already in the queue is skipped.

- The original gallery file is never moved, renamed or deleted.
- The image is **not** read, decoded, validated or inspected in any way.
- As soon as every image has been handled **and the queue is saved**, the import
  screen closes and the queue is shown — no Continue / Done tap needed.

### 3. Share one item at a time

Pick the item you want to pay next and tap **ชำระเงิน**. The app builds a
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

The app re-checks the selected bank on the device right before launch: the
package must still be installed and the intent must resolve to it. If it cannot
be opened, the item is marked `FAILED` with an explanation — the app **never**
falls back to Android's "share with…" chooser and never opens another app.

While the hand-off is launching the item is `SHARING` (the payment button is
locked so rapid taps cannot start a second attempt); once the bank received it
the item becomes `AWAITING_USER_CONFIRMATION`.

### 4. Pay in the bank app

The bank app controls the whole payment: it reads the QR, shows the recipient
and amount, takes your PIN / biometric, confirms the transaction and produces
the e-Slip. This app does none of that and cannot see any of it.

### 5. Confirm the result yourself

When you come back, the app asks: *คุณกลับมาจากแอปธนาคารแล้ว — กรุณาตรวจสอบผลการทำรายการในแอปธนาคารก่อน*

- **ทำรายการเสร็จแล้ว** — the item becomes `COMPLETED` and moves to **ชำระแล้ว**.
  The next item is yours to pick; nothing is opened automatically.
- **QR ใช้งานไม่ได้** — the item stays in the queue as
  `REQUIRES_QR_REPLACEMENT`, appears in **ปัญหา** with a **เปลี่ยน QR** action,
  and is never deleted.
- **ยังไม่แน่ใจ** — nothing is marked complete; the item stays open and is only
  shared again if you decide to.

Returning to the app is **not** a payment success.

### 6. Replace a QR

An item the bank rejected keeps its identity. **เปลี่ยน QR** opens the photo
picker for **one** image; the new image becomes the current QR (v2), the old one
stays as history (v1, *ใช้งานไม่ได้*), and the same Payment Item (`QR #08`) goes
back to `READY`. If the replacement cannot be recorded, the old QR is left
exactly as it was and the app says so.

### 7. The problem badge

The **ปัญหา** tab carries a numeric badge equal to the number of items that
currently need your attention (`UNKNOWN` + `REQUIRES_QR_REPLACEMENT` + `FAILED`).
Completed items, items ready to pay and items already with the bank are never
counted, and a count of zero shows no badge at all.

When every item is confirmed, the home tab shows **วันนี้ชำระครบแล้ว** with the
count and a **+ เพิ่ม QR** action.

## Queue states

| State | Meaning |
| --- | --- |
| `READY` | 🕐 รอชำระ — the QR is in the queue and has not been submitted yet. |
| `SHARING` | The QR is being handed to the bank app right now (the action is locked). |
| `AWAITING_USER_CONFIRMATION` | The bank has the QR; the app is waiting for **your** answer. |
| `COMPLETED` | ✓ ชำระแล้ว — you pressed **ทำรายการเสร็จแล้ว**. The only finished state. |
| `FAILED` | A failure in this app: the file is missing, the intent could not be built, or the bank could not be opened. Nothing reached the bank. |
| `UNKNOWN` | ⚠️ ยังไม่ทราบผล — the result could not be determined. Only you can resolve it. |
| `REQUIRES_QR_REPLACEMENT` | ❌ ต้องเปลี่ยน QR — you reported that this QR cannot be used. The item stays in the queue. |

```
READY → SHARING → AWAITING_USER_CONFIRMATION → COMPLETED
          |                    |
          v                    v
        FAILED               UNKNOWN
                               |
  AWAITING / UNKNOWN / FAILED → REQUIRES_QR_REPLACEMENT → READY (new QR)
                  FAILED / UNKNOWN → READY (explicit user retry)
```

### Payment Items and QR versions

A **Payment Item** is one logical payment task (`QR #08`) with a stable id and a
stable number. It can hold several **QR image versions**: replacing a QR appends
a version and never creates a new item. Each item also records its own payment
**attempts** (`STARTED`, `LAUNCHED`, `FAILED`, `QR_UNUSABLE`, `COMPLETED`,
`UNKNOWN`) as workflow history — never as proof of a bank transaction.

## Double-payment protection

- Only one item may own the payment hand-off at a time.
- A hand-off starts only from a deliberate user action, and the button is
  disabled while a hand-off is in flight (checked in the UI, the ViewModel and
  the domain, so rapid taps cannot start two attempts).
- An item that already reached the bank (`AWAITING_USER_CONFIRMATION`) is not
  shared again: you resolve it first.
- `UNKNOWN` is never retried, completed or re-shared automatically.
- Completing an item does **not** open the next one.

## Process death

If the app is killed while an item is `SHARING` or
`AWAITING_USER_CONFIRMATION`, that item is restored as `UNKNOWN`. The queue
waits on that item until you check the bank app and record what actually
happened. Nothing is retried and nothing is marked paid.

## Known banking apps

The app probes these packages at runtime. Every package id below was read from
the app's **live Google Play listing on 2026-09-19** (see
`BankRegistry.allBanks` — the single source of truth):

| App | Package | Publisher |
| --- | --- | --- |
| K PLUS | `com.kasikorn.retail.mbanking.wap` | Kasikornbank (KBank) |
| SCB EASY | `com.scb.phone` | Siam Commercial Bank |
| Krungthai NEXT | `ktbcs.netbank` | Krungthai Bank |
| Bangkok Bank Mobile Banking | `com.bbl.mobilebanking` | Bangkok Bank |
| krungsri | `com.krungsri.kma` | Bank of Ayudhya |
| ttb touch | `com.TMBTOUCH.PRODUCTION` | TMBThanachart |
| MyMo by GSB | `com.mobilife.gsb.mymo` | Government Savings Bank |
| CIMB THAI | `com.cimbthai.digital.mycimb` | CIMB Thai Bank |
| UOB TMRW Thailand | `com.uob.mightyth2` | United Overseas Bank (Thai) |
| Dime! | `com.dimekkp.dimeapp` | KKP Dime |
| MAKE by KBank | `com.kasikornbank.makebykbank` | Kasikornbank (KBank) |
| Kept | `com.krungsri.kept` | Bank of Ayudhya |
| TrueMoney | `th.co.truemoney.wallet` | True Money Co. Ltd. |

**Package verified** (the id exists on Google Play and belongs to that publisher)
is *not* the same as **share verified** (the app accepts a shared `image/*`
intent). Package ids are verified; share capability is reported at runtime by the
probe, and no bank is claimed "share verified" until it is tested on a real
device. `BankAvailability` therefore distinguishes *installed*,
*installed but not share-capable*, *not installed* and *unknown* — a failed
share-capability probe is never reported as "not installed".

The runtime probe (`BankTarget.query`) checks the real device; the list above is
the candidate set. The `<queries>` block in the manifest lists exactly these
packages so Android 11+ can see them (no broad visibility).

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
│       │   │   ├── domain/        QueueItem, QrVersion, PaymentStatus, PaymentQueue, QueueImport, BankInfo
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
verification, then upload as **`qr-payment-queue-v0.5.0-debug`**. A failing
test, lint run or build fails the workflow.

## Verification status

- Build, unit tests, lint and the APK are verified in CI on every push (163
  test methods, including the state machine, badge, QR replacement, duplicate
  skipping, double-payment protection, direct-share contract, persistence,
  upload gate and fallback tests).
- The three tabs and every bank selection, upload gate and share flow are
  **NOT YET VERIFIED ON A REAL DEVICE** — no Compose screen has been rendered
  outside CI compilation. The test plan is in
  [`docs/REAL_DEVICE_TEST.md`](docs/REAL_DEVICE_TEST.md).

## Known limitations

1. **Bank confirmation stays manual.** The app cannot know whether a transaction
   completed; you record every result.
2. **Bank package names come from Google Play.** If a bank changes its package
   name, the app reports "not found" and you select another.
3. **Not all banking apps support image sharing.** `INSTALLED_NOT_ADVERTISED`
   means the bank is installed but its share activity was not found. The app
   still tries the hand-off and reports the result honestly.
4. **Changing banks does not affect queued items.** Items in
   `AWAITING_USER_CONFIRMATION` or `UNKNOWN` are not re-shared or reset by a bank
   change; the next share uses whatever bank is selected at that moment.
5. No instrumented UI tests; no emulator in CI.

## Roadmap

| Version | Scope | Status |
| --- | --- | --- |
| V0.1 | Foundation, APK, CI | delivered |
| V0.2 | Photo-picker import, app-private storage | delivered |
| V0.3 | QR decoding (ZXing) + EMVCo parsing | superseded |
| V0.4 (0.4.0) | Image queue + K PLUS hand-off | superseded |
| V0.4.1 (0.4.1) | Home-screen queue, direct K PLUS, no chooser | superseded |
| V0.4.2 (0.4.2) | Bank selection, persistent bank, upload gate, generalized share | delivered |
| **V0.5.0 (0.5.0)** | **Three tabs, item state machine, problem badge, replace QR, explicit confirmation** | **delivered** |
| V1.0 | Optional verified reconciliation with official bank API | planned |
| — | Real-device verification of the tabs and the share flow | not yet done |
