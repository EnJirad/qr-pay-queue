# Real-device test report — V0.4 image queue and K PLUS hand-off

## Status

> **REAL DEVICE VERIFICATION: NOT YET DONE**
>
> No Android device and no emulator were available in the environment where this
> change was written, and CI (GitHub Actions) only compiles, unit-tests, lints and
> packages the APK. It cannot install or drive an app.
>
> Therefore **no K PLUS behaviour is claimed anywhere in this repository**. What
> has been verified is listed under "Verified so far" below. Everything else in
> this document is a plan, not a result.

Do not fill in a result you did not observe. For TEST 4 and TEST 5 the evidence
is what the **banking app** shows, not what this app shows.

## Verified so far (no device required)

| Claim | Evidence |
| --- | --- |
| APK builds | GitHub Actions run `35438989074` (commit `b0d5c8b`): `assembleDebug` PASS |
| Unit tests pass | same run: `testDebugUnitTest` PASS, 48 tests |
| Lint passes | same run: `lintDebug` PASS (`abortOnError = true`) |
| APK exists and is non-empty | CI `test -f` / `test -s`, `ls -lh` (9.2M), `unzip -l` |
| APK artifact published | artifact `qr-payment-queue-v0.4.0-debug` in run `35438989074` |
| No QR decoding exists | no QR library in `gradle/libs.versions.toml`; no decoder source in `app/src/main` |
| Queue state machine rules | `PaymentQueueTest`, `PaymentStatusTest` |
| Import order and 1/3/10 images | `QueueImportTest` |
| Share hand-off contract (`ACTION_SEND`, `content://`, image MIME, read grant) | `ShareIntentSpecTest`, `QrShare.kt`, `AndroidManifest.xml` |
| Share target resolution is honest | `ShareTargetsTest` (pure logic) |
| K PLUS package is visible to the resolver on Android 11+ | `<queries>` in `AndroidManifest.xml` |

Not verified on a device: the photo picker UI, the share sheet listing K PLUS,
K PLUS accepting the shared image, K PLUS reading the QR, and everything about
the return trip from K PLUS.

## Test environment (fill in when a device is available)

| Field | Value |
| --- | --- |
| Device model | Xiaomi 15T Pro (target) |
| Android version | Android 16 (target) |
| K PLUS version | _to be filled in_ (from Play Store / app info) |
| App version | 0.4.0 (versionCode 4) |
| Build under test | commit hash of the tested build |
| APK source | GitHub Actions artifact `qr-payment-queue-v0.4.0-debug` |
| Test date | _to be filled in_ |
| Tester | _to be filled in_ |

Install:

```bash
# download the artifact from the workflow run, unzip it, then
adb install -r app-debug.apk
```

## Test cases

Legend for the status column: `NOT RUN` / `PASS` / `FAIL` / `BLOCKED`.

### TEST 1 — one image becomes one queue item

1. Tap **Import QR images** and select **1** QR screenshot.

Expected: exactly **1 item** appears in the queue, with status `QUEUED`, and its
image is shown on the current image card.

Status: `NOT RUN`

### TEST 2 — three images keep selection order

1. Tap **Import QR images** and select **3** QR screenshots in a known order.

Expected: **3 items** appear, in the same order they were selected, numbered
01 / 02 / 03, all `QUEUED`.

Status: `NOT RUN`

### TEST 3 — share opens the Android share sheet

1. Tap **Share image to K PLUS** on the current item.

Expected: the **Android share sheet** appears and **K PLUS can be selected** if it
is installed and advertises an image share target. If it is not listed, the app
itself says so on the card ("K PLUS was not found on this device" or "K PLUS is
installed but does not appear as an image share target") — record which message
appeared.

Status: `NOT RUN`

### TEST 4 — K PLUS receives the shared image

1. On the share sheet, choose **K PLUS**.

Expected: K PLUS opens with the received image (a QR-reading screen, or an
explicit error — record exactly what appears). If K PLUS cannot read a QR from a
shared screenshot at all, that is a finding about K PLUS, not something this app
can work around.

Status: `NOT RUN`

### TEST 5 — the app never claims success on its own

1. Complete (or cancel) the payment in K PLUS.
2. Return to QR Payment Queue.

Expected: the app does **not** claim the payment succeeded. The item is not
`COMPLETED`; it is `WAITING_USER`. Nothing is marked paid because a share
happened, because K PLUS opened, or because the app resumed.

Status: `NOT RUN`

### TEST 6 — the confirmation UI appears

Expected: on return, the confirmation prompt is shown:
*ทำรายการสำหรับภาพนี้เสร็จแล้วหรือยัง?* with **ทำรายการเสร็จแล้ว** and
**ยังไม่เสร็จ**.

Status: `NOT RUN`

### TEST 7 — confirming completion advances the queue

1. Tap **ทำรายการเสร็จแล้ว**.

Expected: the current item becomes `COMPLETED`, and the **next item becomes the
current one** and is ready to share. With a single-item queue the finished
summary is shown instead.

Status: `NOT RUN`

### TEST 8 — the app is killed during the payment

1. Share an image and open K PLUS.
2. Kill QR Payment Queue from recents / force stop it.
3. Reopen the app.

Expected: the payment state is **not** assumed successful. The queue is restored
and the current item is `UNKNOWN`. The queue does not advance and the item is not
retried automatically; the user must resolve it.

Status: `NOT RUN`

### TEST 9 — "not done yet" does not advance

1. Share an image, return, and tap **ยังไม่เสร็จ**.

Expected: the item stays current and stays retryable. The queue does not advance
to the next image. Sharing again asks for confirmation (**Share again**) with a
double-payment warning.

Status: `NOT RUN`

### TEST 10 — no accidental double share

1. Share an image (item is now `WAITING_USER`).
2. Tap **Share image to K PLUS** again.

Expected: a warning dialog appears: sharing again could pay the same bill twice.
Only **Share again** proceeds. Cancelling does nothing, and nothing is shared
automatically after an activity recreation or an app resume.

Status: `NOT RUN`

### TEST 11 — ten images

1. Import **10** QR screenshots.

Expected: **10 items**, numbered 01–10, in the selected order, all `QUEUED`. No
crash, no reordering.

Status: `NOT RUN`

### TEST 12 — missing image file

1. Import an image, then remove its stored copy (for example clear the app data
   directory's `files/qrqueue/images/` file with root/`adb`, or use a debug build).

Expected: the app does not crash. The item is shown as `FAILED` with a clear
reason, and sharing it is not attempted.

Status: `NOT RUN`

### TEST 13 — cancellation and bad input

- Cancel the photo picker: nothing is imported, no error dialog, no crash.
- Select the same screenshot twice in one selection: only one queue item is
  created.
- Import a corrupt/unreadable image file: it is reported as not copied, no crash,
  and **no queue item** is created for it.

Status: `NOT RUN`

### TEST 14 — gallery originals are untouched

1. Note the original screenshots in the gallery (names, sizes, timestamps).
2. Import them, work a queue, then use **Clear queue**.

Expected: the gallery originals are unchanged (identical names, sizes and
timestamps). Only the app's own copies are gone.

Status: `NOT RUN`

## Test matrix and where each case is covered

| Case | Automated | Needs device |
| --- | --- | --- |
| Import 1 / 3 / 10 images | unit test (order + positions) | picker behaviour |
| Duplicate selected image | unit test (URI de-duplication) | picker behaviour |
| Copy failure creates no item | — | real picker / corrupt file |
| Missing stored image → FAILED, no crash | — | real file removal |
| Orphan image cleanup | — | real file system |
| Queue: QUEUED → SHARING → WAITING_USER → COMPLETED | unit test | tapping through |
| Queue: QUEUED → SHARING → FAILED | unit test | share failure |
| Queue: QUEUED → SHARING → WAITING_USER → UNKNOWN | unit test | real process death |
| UNKNOWN is never auto-completed / auto-retried | unit test | real restart |
| "Not done yet" does not advance | unit test | tapping through |
| Share: ACTION_SEND, content://, image MIME, read grant | unit test | real share sheet |
| Share: K PLUS installed / not installed | classification unit test | share sheet |
| Share: cancelled | — | real chooser |
| Double-share protection | unit test (state machine) | dialog behaviour |
| K PLUS reads the QR image | — | K PLUS |

## Evidence to attach

For each executed test, attach a screenshot named `<test-number>-<step>.png` under
`docs/evidence/` (create the directory when there is something real to put in it)
and reference it from the test above. Do not add screenshots you did not take.

## What this app can never verify

- Whether a bank transaction actually completed. There is no supported bank API
  in use, so the user's own answer is the only source of truth (see README).
- Whether the user verified the recipient and amount correctly inside K PLUS. The
  app does not read the QR, so checking it is the user's job inside K PLUS.
