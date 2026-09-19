# Real-device test report — V0.4.1 home screen and direct K PLUS hand-off

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
>
> In particular, the following are **unproven** until someone runs TEST 3 on a
> phone: that `setPackage("com.kasikornbank.kplus")` opens K PLUS directly, that
> Android's "share with…" chooser does not appear, and that K PLUS accepts the
> shared image.

Do not fill in a result you did not observe. For TEST 4 / TEST 5 / TEST 6 the
evidence is what the **banking app** shows, not what this app shows.

## Verified so far (no device required)

| Claim | Evidence |
| --- | --- |
| No QR decoding exists | no QR library in `gradle/libs.versions.toml`; no decoder source in `app/src/main` |
| The hand-off is addressed to K PLUS only | `ShareIntentSpecTest` (`targetPackage == com.kasikornbank.kplus`), `QrShare.kPlusShareIntent` uses `setPackage` |
| No chooser can be built in the payment path | `ShareIntentSpecTest.noChooserIsEverBuilt`; `Intent.createChooser` does not appear in `app/src/main` |
| The image is shared as `content://` with an image MIME type and a read grant | `ShareIntentSpecTest`, `QrShare.kt`, `AndroidManifest.xml` |
| K PLUS capability reporting is honest | `KPlusTargetTest` (pure classification) |
| K PLUS is visible to the package resolver on Android 11+ | `<queries>` in `AndroidManifest.xml` |
| Only one image can own the payment hand-off | `PaymentQueueTest.onlyOneImageCanBeInKPlusAtATime` |
| The import screen ends by itself and never sticks on "5 / 5" | `ImportCompletionTest` |
| 1 / 3 / 10 images become 1 / 3 / 10 queued items | `QueueImportTest` |
| Queue state machine rules | `PaymentQueueTest`, `PaymentStatusTest` |
| APK builds, tests pass, lint passes, artifact published | the GitHub Actions run recorded in `AI_HANDOFF.md` |

Not verified on a device: the photo picker UI, whether tapping **แชร์ไป K PLUS**
opens K PLUS directly, whether the chooser is skipped, whether K PLUS accepts the
image, whether it reads a QR from it, the payment flow, and the return trip.

## Test environment (fill in when a device is available)

| Field | Value |
| --- | --- |
| Device model | Xiaomi 15T Pro (target) |
| Android version | Android 16 (target) |
| K PLUS version | _to be filled in_ (from Play Store / app info) |
| App version | 0.4.1 (versionCode 5) |
| Build under test | commit hash of the tested build |
| APK source | GitHub Actions artifact `qr-payment-queue-v0.4.1-debug` |
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

1. Tap **+ เพิ่มรูป QR** and select **1** QR screenshot.

Expected: the import screen shows `1 / 1`, then closes **by itself**, and exactly
**1 item** appears in the queue with status `QUEUED`. No Continue / Done / Next
tap is needed.

Status: `NOT RUN`

### TEST 2 — three images keep selection order and return home automatically

1. Tap **+ เพิ่มรูป QR** and select **3** QR screenshots in a known order.
2. Watch the progress text.

Expected: it goes `1 / 3`, `2 / 3`, `3 / 3`, then the import screen closes **by
itself** and the queue is shown: **3 items** in the same order they were selected,
numbered 01 / 02 / 03, all `QUEUED`. The counts read `รายการทั้งหมด 3`,
`เสร็จแล้ว 0`, `เหลือ 3`. The app must **not** stay on "3 / 3".

Status: `NOT RUN`

### TEST 3 — แชร์ไป K PLUS opens K PLUS directly (no chooser)

1. Tap the **แชร์ไป K PLUS** button on image 01.

Expected (the most important check in this document):

1. K PLUS opens **directly**.
2. Android's "share with…" chooser does **not** appear.
3. No other app is offered as a destination.

Record exactly what appeared. If a chooser appears, that is a `FAIL` for this test
even if K PLUS is in the list.

Status: `NOT RUN`

### TEST 4 — K PLUS receives the shared image

1. Continue from TEST 3.

Expected: K PLUS shows the received image (a QR-reading screen, or an explicit
error — record exactly what appears). Watch for the app's own error path too: if
K PLUS cannot accept the intent, the app shows
"K PLUS ไม่รับรูปที่ส่งไป (ไม่พบกิจกรรมที่รองรับ)" and the item becomes `FAILED`
with the reason "K PLUS did not accept the shared image". If that happens, record
it verbatim — it is a finding, not something to work around.

Status: `NOT RUN`

### TEST 5 — K PLUS can reach its payment flow

1. Continue in K PLUS: read the QR, check the recipient and amount, enter or
   confirm the amount.

Expected: the user — not this app — can complete those steps inside K PLUS. This
app must have no part in them.

Status: `NOT RUN`

### TEST 6 — the app never claims success on its own

1. Complete (or cancel) the payment in K PLUS.
2. Return to QR Payment Queue.

Expected: the app does **not** claim the payment succeeded. The item is not
`COMPLETED`; it is `WAITING_USER`. Nothing is marked paid because an image was
shared, because K PLUS opened, or because the app was resumed.

Status: `NOT RUN`

### TEST 7 — the confirmation UI appears

Expected: on return, the item's panel asks *ทำรายการสำหรับรูปนี้เสร็จแล้วหรือยัง?*
with **ทำรายการเสร็จแล้ว** and **ลองอีกครั้ง**.

Status: `NOT RUN`

### TEST 8 — confirming completion closes that image only

1. Tap **ทำรายการเสร็จแล้ว**.

Expected: item 01 becomes `COMPLETED` (counted in `เสร็จแล้ว`), it shows
"ทำรายการแล้ว และจะไม่ถูกส่งไป K PLUS อีก", it is **not** sent to K PLUS again, and
the other items are still `QUEUED`, each with its own **แชร์ไป K PLUS** button.

Status: `NOT RUN`

### TEST 9 — ลองอีกครั้ง does not complete and does not advance

1. On an item that is waiting for an answer, tap **ลองอีกครั้ง**.

Expected: nothing is marked complete, the queue does not move on, the item stays
retryable, and it is handed to K PLUS again **only** when the user taps
**แชร์ไป K PLUS** — no automatic re-share. Sharing again first shows the
double-payment warning.

Status: `NOT RUN`

### TEST 10 — only one payment at a time

1. Hand image 01 to K PLUS and come back without answering.
2. Try to tap **แชร์ไป K PLUS** on image 02.

Expected: the button on image 02 is disabled and the app explains that an item is
already waiting, so a second payment cannot be started by accident.

Status: `NOT RUN`

### TEST 11 — the app is killed during the payment

1. Hand an image to K PLUS.
2. Kill QR Payment Queue from recents / force stop it.
3. Reopen the app.

Expected: the payment state is **not** assumed successful. The queue is restored
and that item is `UNKNOWN`, with the panel asking the user to check K PLUS. The
queue does not advance, the item is not retried automatically, and no other image
can be handed over until it is resolved.

Status: `NOT RUN`

### TEST 12 — completing everything

1. Confirm every image.

Expected: **ทำรายการครบแล้ว** with `3 / 3 รายการเสร็จสิ้น` and **กลับหน้าแรก**. The
app does **not** open K PLUS by itself and does not share anything
automatically.

Status: `NOT RUN`

### TEST 13 — ten images

1. Import **10** QR screenshots.

Expected: progress runs to `10 / 10`, the import screen closes by itself, and
**10 items** appear numbered 01–10 in the selected order, all `QUEUED`. No crash,
no reordering.

Status: `NOT RUN`

### TEST 14 — a partial import

1. Select several images where at least one cannot be read (for example a corrupt
   file).

Expected: the app reports "นำเข้าได้ 4 จาก 5 รูป" with the number that failed, **no
queue item** is created for the image that failed, the queue is intact, and the
app is on the queue screen (not stuck on a progress step).

Status: `NOT RUN`

### TEST 15 — cancellation and bad input

- Cancel the photo picker: nothing is imported, no error dialog, no crash, and
  the home screen stays as it was.
- Select the same screenshot twice in one selection: only one queue item is
  created.
- Import a corrupt/unreadable image file: it is reported as not copied, no crash,
  and **no queue item** is created for it.

Status: `NOT RUN`

### TEST 16 — missing image file

1. Import an image, then remove its stored copy (for example delete the file
   under the app data directory with root/`adb` on a debug build).

Expected: the app does not crash. The item is shown as `FAILED` with a clear
reason, and handing it over is not attempted.

Status: `NOT RUN`

### TEST 17 — K PLUS not installed

1. On a device without K PLUS, open the app with images in the queue.

Expected: the **แชร์ไป K PLUS** button is disabled and the app explains that K PLUS
was not found. No chooser is shown, because the destination is fixed.

Status: `NOT RUN`

### TEST 18 — gallery originals are untouched

1. Note the original screenshots in the gallery (names, sizes, timestamps).
2. Import them, work a queue, then use **ล้างคิว**.

Expected: the gallery originals are unchanged (identical names, sizes and
timestamps). Only the app's own copies are gone.

Status: `NOT RUN`

## Test matrix and where each case is covered

| Case | Automated | Needs device |
| --- | --- | --- |
| Import 1 / 3 / 10 images | unit test (order + positions) | picker behaviour |
| Import screen returns home by itself | unit test (`ImportCompletionTest`) | the real progress UI |
| Partial import is reported with counts | unit test (`ImportSummary`) | a real unreadable file |
| Duplicate selected image | unit test (URI de-duplication) | picker behaviour |
| Copy failure creates no item | — | real picker / corrupt file |
| Missing stored image → FAILED, no crash | — | real file removal |
| Orphan image cleanup | — | real file system |
| One-by-one queue in any order | unit test | tapping through |
| Only one hand-off at a time | unit test | tapping through |
| Direct intent: ACTION_SEND, `content://`, image MIME, read grant, `setPackage` | unit test | **K PLUS opening directly (TEST 3)** |
| No chooser in the payment path | unit test + source sweep | **TEST 3** |
| K PLUS accepts the image | — | **TEST 4** |
| K PLUS reads the QR and pays | — | **TEST 5 / TEST 6** |
| Queue: QUEUED → SHARING → WAITING_USER → COMPLETED | unit test | tapping through |
| Queue: QUEUED → SHARING → FAILED | unit test | K PLUS refusing the image |
| Queue: QUEUED → SHARING → WAITING_USER → UNKNOWN | unit test | real process death |
| UNKNOWN is never auto-completed / auto-retried | unit test | real restart |
| ลองอีกครั้ง does not complete or advance | unit test | tapping through |
| Double-payment protection on re-share | unit test (state machine) | dialog behaviour |

## Evidence to attach

For each executed test, attach a screenshot named `<test-number>-<step>.png` under
`docs/evidence/` (create the directory when there is something real to put in it)
and reference it from the test above. Do not add screenshots you did not take.

For TEST 3, a screen recording (or two screenshots: the tap and the result) is the
evidence that matters: the chooser either appeared or it did not.

## What this app can never verify

- Whether a bank transaction actually completed. There is no supported bank API
  in use, so the user's own answer is the only source of truth (see README).
- Whether the user verified the recipient and amount correctly inside K PLUS. The
  app does not read the QR, so checking it is the user's job inside K PLUS.
