# Real-device test report — K PLUS share flow

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

Do not fill in a result you did not observe. Do not mark a test PASS based on the
app's own UI alone: for TEST 2, TEST 3 and TEST 4 the evidence is what the
**banking app** shows.

## Verified so far (no device required)

| Claim | Evidence |
| --- | --- |
| APK builds | GitHub Actions `assembleDebug` BUILD SUCCESSFUL |
| APK exists and is non-empty | CI `test -f` / `test -s`, `ls -lh`, `unzip -l` |
| APK artifact published | artifact `qr-payment-queue-debug-apk` |
| QR decoding works on real payloads | `QrImageDecoderTest` renders QR codes and reads them back |
| Multi-QR screenshots are rejected, not guessed | `QrImageDecoderTest`, `QrValidationTest` |
| Parser/validation/queue rules | 85 unit tests in `app/src/test/...` |
| Share path uses `content://` + temporary read grant | `QrShare.kt`, `AndroidManifest.xml`, `res/xml/file_paths.xml` |
| Share target resolution is honest | `ShareTargetsTest` (pure logic) |
| K PLUS package is visible to the resolver on Android 11+ | `<queries>` in `AndroidManifest.xml` |

Not verified on a device: the photo picker UI, real screenshot decoding, the
share sheet listing K PLUS, K PLUS accepting the image, K PLUS reading the QR,
and everything about the return trip from K PLUS.

## Test environment (fill in when a device is available)

| Field | Value |
| --- | --- |
| Device model | _to be filled in_ |
| Android version | _to be filled in_ (Android 10 or newer) |
| K PLUS version | _to be filled in_ (from Play Store / app info) |
| App version | 0.3.0 (versionCode 3) |
| Build under test | commit hash of the tested build |
| APK source | GitHub Actions artifact `qr-payment-queue-debug-apk` |
| Test date | _to be filled in_ |
| Tester | _to be filled in_ |

Install:

```bash
# download the artifact from the workflow run, unzip it, then
adb install -r app-debug.apk
```

## Test cases

Legend for the status column: `NOT RUN` / `PASS` / `FAIL` / `BLOCKED`.

### TEST 1 — Share sheet lists K PLUS

1. Import one QR screenshot, start the queue.
2. Tap **Open / Share QR**.
3. Look at the Android share sheet.

Expected: the share sheet opens and **K PLUS is listed** as a target for the image.
If it is not listed, the app itself says so on the processing card (either "K PLUS
was not found on this device" or "K PLUS is installed but does not appear as a QR
image share target") — record which message appeared.

Status: `NOT RUN`

### TEST 2 — K PLUS accepts a `content://` image

1. Tap **Open / Share QR**.
2. Choose **K PLUS**.

Expected: K PLUS opens and shows something for the received image (a QR reading
screen, or an explicit error — record exactly what appears).

Status: `NOT RUN`

### TEST 3 — K PLUS reads the QR from the shared image

Expected: K PLUS shows payment details, or clearly states it cannot read the QR.
If K PLUS cannot read screenshots at all, that is a finding about the banking app,
not something this app can work around — record it and stop relying on TEST 4.

Status: `NOT RUN`

### TEST 4 — recipient shown by K PLUS matches the app

Expected: the recipient K PLUS shows matches **Recipient** on the queue card
(same phone number / ID / biller), and the amount matches **Amount**.

Status: `NOT RUN`

### TEST 5 — amount

Expected: the amount K PLUS shows equals the amount the queue card shows, for a
QR that contains an amount.

Status: `NOT RUN`

### TEST 6 — QR without an amount

1. Use a static PromptPay QR screenshot (no amount).

Expected: the queue card shows "No amount in this QR — enter it in your banking
app", and K PLUS asks for the amount as its normal flow does. The app must never
have invented a figure.

Status: `NOT RUN`

### TEST 7 — user cancels in K PLUS

1. Share the QR, open K PLUS, then back out without paying.
2. Return to QR Payment Queue.

Expected: the item is **not** successful. It is still waiting for the user's
answer, and the app asks "Have you completed this payment?".

Status: `NOT RUN`

### TEST 8 — user pays successfully

1. Share the QR, pay in K PLUS, return.
2. Tap **Payment successful**.

Expected: the item is recorded as paid, the queue advances to the next QR, and the
progress figures increase by that item's amount.

Status: `NOT RUN`

### TEST 9 — user is unsure

1. Share the QR, return without a clear result.
2. Tap **Something went wrong**.

Expected: the item becomes UNKNOWN, the queue **stops**, nothing advances
automatically, and the screen asks the user to resolve the result. Confirming
"Payment successful" or "Payment failed" afterwards is the only way forward.

Status: `NOT RUN`

### TEST 10 — process death with a payment in flight

1. Share a QR and open K PLUS.
2. Kill QR Payment Queue from the recents/OS (or force stop).
3. Reopen the app.

Expected: the queue is restored, the current item is shown, its status is
**UNKNOWN**, and nothing is marked paid. The user must resolve it.

Status: `NOT RUN`

### TEST 11 — no repeated sharing by accident (double payment)

1. Share a QR (item now waits for confirmation).
2. Tap **Open / Share QR** again.

Expected: instead of sharing again immediately, a warning dialog appears: sharing
again could pay the same bill twice. Only **Share again** proceeds. Cancelling
does nothing.

Status: `NOT RUN`

### TEST 12 — cancellation and bad input

- Cancel the photo picker: nothing is imported, no error dialog, no crash.
- Import the same screenshot twice in one selection: the second is listed as a
  duplicate and excluded.
- Import a non-QR photo: listed as "No QR code could be read from this image".
- Import a screenshot with two different QR codes: listed as "This image contains
  more than one QR code" and excluded.
- Import a corrupt/unreadable image file: listed as unreadable, no crash.

Status: `NOT RUN`

### TEST 13 — uninstall data hygiene

1. Note the original screenshots in the gallery (files, names, timestamps).
2. Import them, work a queue, then use **Clear queue**.

Expected: the gallery originals are unchanged (identical names, sizes and
timestamps). The app's own copies are gone.

Status: `NOT RUN`

## Test matrix and where each case is covered

| Case | Automated | Needs device |
| --- | --- | --- |
| Valid PromptPay QR | unit test | decoding real screenshots |
| Valid Thai bill QR | unit test | decoding real screenshots |
| Invalid CRC | unit test | — |
| Malformed payload | unit test | — |
| Unreadable image | unit test | corrupt real files |
| No QR in image | unit test | — |
| Duplicate QR (same payload) | unit test | — |
| Duplicate selected image | unit test (URI de-duplication) | picker behaviour |
| Unsupported QR | unit test | — |
| Amount missing | unit test | K PLUS flow |
| Amount valid | unit test | K PLUS flow |
| Multiple QR codes in one image | unit test | real screenshots |
| Queue: one item / many items | unit test | tapping through |
| Queue: failure / unknown / completed | unit test | tapping through |
| Queue: app restart, process death | unit test (restore → UNKNOWN) | real restart |
| Share: K PLUS installed / not installed | classification unit test | share sheet |
| Share: cancelled | — | real chooser |
| Share: no target available | classification unit test | device without any image share app |
| Share: URI permission failure / invalid image | — | corrupt stored copy |
| Double-share protection | unit test (state machine) | dialog behaviour |

## Evidence to attach

For each executed test, attach a screenshot with the file named
`<test-number>-<step>.png` under `docs/evidence/` (create the directory when there
is something real to put in it) and reference it in the table above. Do not add
screenshots you did not take.

## What this app can never verify

- Whether a bank transaction actually completed. There is no supported bank API
  in use, so the user's own answer is the only source of truth (see README).
- Whether the user paid the wrong bill inside K PLUS. The app shows what the QR
  contained; checking it against what the bank shows is the user's job.
