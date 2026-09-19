# AI_HANDOFF.md

This handoff reflects reality. Nothing below claims a build, an APK or a device
result that has not been observed. Update it after every meaningful work session.

## Project

QR Payment Queue — native Android application for organizing QR payment tasks and
handing each QR to a banking app (K PLUS) through Android's own share sheet.

Package `com.enjirad.qrqueue`. Native Android only: Kotlin + Jetpack Compose +
AndroidX + Material 3 + Gradle Kotlin DSL. No web technology anywhere.

## Current version

0.3.0 (versionCode 3) — V2 workflow plus the pre-production audit and the
K PLUS share integration. Version 0.2.0 was the V2 workflow itself.

## Current status

**Audit + K PLUS share integration implemented; CI verification pending for this
commit.** The sandbox has **no JDK, no Android SDK, no emulator and no device**,
so nothing here can be compiled or run locally: the build authority is GitHub
Actions, and device behaviour can only be verified by a human with a phone.

Honest summary of what is and is not proven:

| Area | Status |
| --- | --- |
| Builds, unit tests, lint, APK, artifact | CI (see "Build result") |
| QR import / decode / validate / queue logic | unit tested in CI (85 tests) |
| Share intent construction (`content://`, grant, `image/*`) | source-reviewed + unit tested classification logic |
| Share sheet listing K PLUS | **not verified** (needs a device) |
| K PLUS accepting/reading the shared QR | **not verified** (needs a device, and is ultimately KBank's behaviour) |
| Return-from-K PLUS flow, process death on device | **not verified** (needs a device) |

## K PLUS integration (implemented this session)

The hand-off is standard Android only — no private API, no automation:

```
QR image (app-private copy)
  → FileProvider content:// URI + FLAG_GRANT_READ_URI_PERMISSION
  → Intent.ACTION_SEND + EXTRA_STREAM, type image/png (fallback image/*)
  → Android share sheet (resolver chooses the target; no package is forced)
  → K PLUS: reads the QR, shows payment data
  → USER: PIN / biometric / confirm inside K PLUS
  → QR Payment Queue: user records the result (successful / failed / unknown)
  → next item
```

Details that matter:

- `data/QrShare.kt` builds the chooser; the URI is always `content://` from
  `FileProvider` (never `file://`), with a per-use read grant.
- `data/ShareTargets.kt` asks the platform what can actually receive the image.
  `classify()` is pure and unit tested; `query()` is the Android layer. It also
  reports whether **K PLUS** is a share target, installed but not a share target,
  or absent — the processing screen then explains that in plain language instead
  of leaving an empty share sheet unexplained.
- The MIME type starts as the real stored type and falls back to `image/*` when
  nothing handles the specific one, which is a common reason a bank app does not
  appear in the chooser.
- The chooser stays in the app's own task when launched from the activity, so
  returning from K PLUS lands back on the queue. `FLAG_ACTIVITY_NEW_TASK` is only
  added when there is no activity task to launch into.
- `<queries>` in `AndroidManifest.xml` (ACTION_SEND + `image/*`, plus the K PLUS
  package) makes share-target detection accurate on Android 11+ without any
  permission and without exposing data.
- Sharing still never means paid: the item moves to WAITING_CONFIRMATION and only
  an explicit user answer can set a result.

## Pre-production risk audit (this session)

Found and fixed, ordered by how much damage the issue could do in production.

1. **Multi-QR screenshots were silently resolved to one code (fixed, high).**
   `QrImageDecoder` used the first QR ZXing returned. Real screenshots often hold
   a payment QR next to an unrelated code; picking one could pay the wrong code.
   Now, after a successful decode, ZXing's `GenericMultipleBarcodeReader` scans
   the rest of the image: one distinct payload is a normal result, two or more
   become `QrDecodeResult.Ambiguous` → `MULTIPLE_QR_CODES` → `INVALID`, excluded
   from the queue with a clear reason. `RGBLuminanceSource`/`GrayscaleLuminanceSource`
   crop support (required by the multi-reader) and the recursive cost were checked
   in the ZXing 3.5.4 sources before adopting it.
2. **A repeated share could pay the same bill twice (fixed, high).**
   The share button was active on an item that had already been handed off, so
   two taps meant two shares of the same QR. Now a share request is ignored while
   one is still in flight, and any item that is not `READY` requires an explicit
   **Share again** confirmation with a double-payment warning
   (`ReShareDialog`, `QueueViewModel.onShareQrRequested`).
3. **An empty share sheet was unexplained (fixed, medium).** Nothing checked
   whether any app could receive the image, and package visibility on Android 11+
   would have hidden the answer anyway. Now the app probes the resolver (with
   `<queries>`), disables sharing when no app can receive an image, offers to open
   the image instead, and states truthfully whether K PLUS is missing, installed
   without a share filter, or available.
4. **A failed queue save was swallowed (fixed, medium).** `saveQueue` now returns
   a result and the UI reports `QUEUE_NOT_SAVED`, so a payment result is never
   believed to be recorded when it never reached disk.
5. **Orphan image copies could accumulate (fixed, low/medium).** Images written
   by an import that never produced a saved queue are now swept
   (`sweepOrphanImages`), and an import that finishes after the user cleared the
   queue is discarded along with the copies it just wrote instead of resurrecting
   a deleted queue.
6. **Timestamps were not persisted (fixed, low).** Items now carry
   `importedAtMillis` and `decidedAtMillis`, and the queue carries
   `updatedAtMillis` (stamped on every save). This makes "when was this decided?"
   answerable after a restart.
7. **MIME fallback for share targets (fixed, medium).** A HEIC/WebP image shared
   as `image/heic` can be rejected by every receiver; the share is retried as
   `image/*`.
8. **`.gitignore` hid the whole Android source tree (fixed in the previous
   session, still critical).** The unanchored `src/` rule ignored `app/src/`.
   Patterns are now anchored to the repository root.

Checked and found acceptable, with reasons:

- **Original images**: the app only ever copies. `QueueRepository` writes to
  `filesDir/qrqueue/`, and clearing a queue deletes only that directory. Nothing
  in the app can touch a gallery file.
- **URI permissions after restart**: imports are copied immediately, so no picker
  grant is needed later. A process killed mid-import leaves a copy that ends up
  as an orphan, which is swept.
- **Memory**: decoding uses `inSampleSize` (≤1600 px first pass), the
  full-resolution retry is skipped above an 8 MP budget, `Bitmap`s are recycled
  after their pixels are copied, and only the current item's preview (≤1080 px) is
  held by the UI. Images are processed strictly one at a time.
- **Queue safety rules**: no transition can reach SUCCESS/PAYMENT_FAILED without
  WAITING_CONFIRMATION or an explicit UNKNOWN resolution; an interrupted hand-off
  returns as UNKNOWN; UNKNOWN blocks the queue and is never retried.
- **Validation**: an item only becomes `READY` when the payload parses as a
  supported Thai EMVCo QR with a valid CRC; otherwise it lands in a specific error
  state that stays visible in the review list.
- **Manifest/permissions**: no runtime permissions; the only exported component is
  the launcher activity; `FileProvider` is `exported=false` and exposes only
  `qrqueue/images/`; `allowBackup=false`; no logging of payloads or secrets
  (the app does not log at all).
- **Lint**: `abortOnError=false` is deliberate (lint must not be confused with the
  APK gate, and CI still runs it). Lint currently completes clean as a task.

Not fixed / not fixable here, recorded honestly:

- Lint is not configured to fail the build (`abortOnError=false`).
- There are no instrumented UI tests and no emulator in CI.
- K PLUS behaviour cannot be verified from this environment at all.

## Files added (this session)

- `docs/REAL_DEVICE_TEST.md` — the device test plan, marked
  **NOT YET VERIFIED ON REAL DEVICE**.
- `app/src/main/java/com/enjirad/qrqueue/data/ShareTargets.kt` — share target and
  K PLUS availability resolution (pure `classify`, Android `query`).
- `app/src/test/java/com/enjirad/qrqueue/data/ShareTargetsTest.kt` — 7 tests.

## Files changed (this session)

- `domain/QrImageDecoder.kt` — `Ambiguous` result, multi-QR scan, documented
  failure behaviour.
- `domain/ValidationIssue.kt` — `MULTIPLE_QR_CODES`.
- `domain/QrValidation.kt` — ambiguous images are rejected before parsing.
- `domain/PaymentQueue.kt` — `updatedAtMillis`, decision timestamps on settle.
- `domain/QueueItem.kt` — `importedAtMillis`, `decidedAtMillis`.
- `domain/QueueImport.kt` — `buildItem` records the import time.
- `data/QueueRepository.kt` — `saveQueue` reports success, `deleteImages`,
  `sweepOrphanImages`, timestamps persisted.
- `data/QrShare.kt` — MIME-driven chooser, task behaviour fixed, `content://` +
  grant unchanged.
- `ui/QueueViewModel.kt` — share gating and in-flight guard, re-share
  confirmation, new notices, import/clear race guard, orphan sweep, timestamps.
- `ui/QueueScreen.kt` — share-sheet status notes, re-share dialog, "open QR image"
  fallback, new notices.
- `AndroidManifest.xml` — `<queries>` for share targets and the K PLUS package.
- `app/build.gradle.kts` — version 0.3.0 (versionCode 3).
- `res/values/strings.xml` — new strings, version chip.
- `README.md` — K PLUS share flow, audit-driven behaviour, verification status.

## Dependencies added

None this session. ZXing core 3.5.4 (added with V2) remains the only third-party
dependency, and it is used for both single and multi QR decoding.

## Tests performed

85 JUnit 4 test methods (up from 72), all run in CI with
`./gradlew testDebugUnitTest`. New this session:

- `QrImageDecoderTest` — an image with two different QR codes is reported as
  `Ambiguous` with both payloads; an image with one QR is not ambiguous.
- `QrValidationTest` — ambiguous images become `MULTIPLE_QR_CODES` / `INVALID`.
- `ShareTargetsTest` — declared MIME used when handled, `image/*` fallback, no
  target at all, distinct target counting, K PLUS as share target / installed but
  not a share target / not installed.
- `PaymentQueueTest` — success and failure record the user's decision time; an
  undecided item has none.
- `QueueImportTest` — the import timestamp is carried onto the item.

**No local Gradle run.** No JDK and no Android SDK exist in this sandbox.

## Build result

- LOCAL BUILD: **not performed / not possible** (no JDK, no Android SDK, no
  device here).
- GITHUB ACTIONS for 0.2.0 (commit `7dd3ed9`, run `35430531448`): **SUCCESS** —
  `testDebugUnitTest` (72 tests), `lintDebug`, `assembleDebug`, APK checks and
  artifact upload, 2m38s.
- GITHUB ACTIONS for 0.3.0: **PENDING** for the commit containing this file.
  Update this line with the run URL and conclusion once observed, then state the
  test count, APK size and artifact ID here.
- Earlier failures worth knowing: the old workflow died inside
  `android-actions/setup-android@v3` before Gradle (fixed by pinning
  `ubuntu-24.04` + `setup-android@v4` with explicit packages); the first V2 run
  failed one parser test because a fixture carried the bill-payment AID under tag
  29 (fixed by identifying the scheme by AID and correcting the fixture).

## APK result

Expected path: `app/build/outputs/apk/debug/app-debug.apk`.

Last observed build (0.2.0, run `35430531448`): APK exists, non-zero (`9.5M`,
`classes.dex` + `AndroidManifest.xml` present), uploaded as artifact
`qr-payment-queue-debug-apk` (9,482,077 bytes, artifact ID `10580268112`). The
0.3.0 run must be observed and recorded here before this section is treated as
current.

## Real-device verification

**REAL DEVICE VERIFICATION: NOT YET DONE.** No device or emulator was available.
No claim in this repository says K PLUS accepted, read or paid anything. The plan,
including what counts as evidence, is in `docs/REAL_DEVICE_TEST.md`; the results
table there is entirely `NOT RUN` and must stay that way until a human fills it in
from a real phone.

## Known limitations

1. **Bank confirmation stays manual.** Without an official supported bank
   integration the app cannot know whether a transaction completed; the user
   records every result. This is intentional.
2. **K PLUS cannot be verified from here** — see above.
3. **Multi-QR images are rejected, not resolved.** An image holding two different
   QR codes is refused (safely) rather than guessing; the user must re-screenshot
   just the payment code.
4. K PLUS detection relies on the known package name `com.kasikornbank.kplus`. If
   KBank ships under a different package, the app reports "not found" while the
   share sheet itself still works normally; only the hint would be wrong.
5. Only Thai EMVCo QRs (PromptPay tag 29 / bill payment tag 30, AIDs
   `A000000677010111` / `A000000677010112`) are supported; anything else is
   rejected as unsupported rather than guessed.
6. Duplicate detection compares decoded payload text: two different QRs encoding
   different payloads for the same debt are treated as two items.
7. HEIC/HEIF screenshots need API 28+; on API 26–27 they are reported as
   unreadable.
8. `RECIPIENT_MISMATCH`, `AMOUNT_MISMATCH`, `ORDER_NOT_FOUND`, `EXPIRED` and
   `RECONCILED` exist in the status model but nothing sets them — they belong to a
   future reconciliation mechanism.
9. No instrumented UI tests; there is no emulator in CI.
10. Lint does not fail the build (`abortOnError=false`).
11. UI copy is English (with the existing Thai subtitle); a Thai localization of
    the payment-critical prompts has not been done.
12. Queue state and images are app-private; uninstalling removes them.

## Next task

1. **Real-device verification first** — nothing below matters more while the K PLUS
   flow is unproven. Install the artifact, run TEST 1–13 in
   `docs/REAL_DEVICE_TEST.md`, fill in the tables with what was actually observed,
   and only then describe the flow as working.
2. If TEST 2/TEST 3 show that K PLUS cannot accept a shared screenshot, document
   that finding plainly in `docs/REAL_DEVICE_TEST.md` and the README instead of
   inventing a workaround; the honest outcome is that the share path depends on
   KBank's support for it.
3. Optional: Thai localization of the payment-critical prompts, and a queue
   history so a finished queue can be reviewed later.
4. Optional V1.0: an explicitly supported reconciliation mechanism (official bank
   API) as a separate, verified layer.

## Important decisions

- One QR library (ZXing core) for both single and multi decoding; no second
  scanner library, no camera code.
- Domain logic stays free of Android imports, so parser, decoder, validation,
  queue state machine and share classification are covered by JVM unit tests.
- The decoder works on ARGB pixels, so the production decode path is testable and
  the Android layer stays thin.
- Ambiguity is refused rather than resolved: paying the wrong code is worse than
  skipping an item.
- A repeated share of the same QR requires an explicit confirmation.
- Share targets are described, never forced: Android's resolver picks the app.
- `<queries>` is used rather than `QUERY_ALL_PACKAGES`, so share detection works
  on Android 11+ without a blanket package query.
- Queue state lives in one JSON file plus one image directory; there is no second
  storage or database.
- Persistence happens on every mutation, and an interrupted hand-off becomes
  UNKNOWN rather than a guess.

## Things future agents must NOT repeat

- Do **not** claim K PLUS works, or that any real transaction happened, without
  real-device evidence recorded in `docs/REAL_DEVICE_TEST.md`. The current status
  is NOT YET VERIFIED ON REAL DEVICE.
- Do **not** claim a build or APK exists without CI evidence.
- Do **not** reintroduce any web scaffold (Vite/React/Convex/package.json/
  `bun.lock`/root `src/`).
- Do **not** re-add an unanchored `src/` (or similar bare tool-folder) ignore
  rule: it hides `app/src/` from git and breaks a clean checkout.
- Do **not** remove the `<queries>` block: without it share-target detection on
  Android 11+ silently reports nothing.
- Do **not** go back to picking one QR out of an image that contains several.
- Do **not** let a share happen twice on the same item without the explicit
  confirmation dialog.
- Do **not** mark a payment `PAID` because a QR was opened, shared or displayed,
  and do **not** auto-retry an UNKNOWN result.
- Do **not** delete or move the user's gallery originals when importing.
- Do **not** create fake queue items, sample payments, demo QR payloads,
  placeholder success states, or device test results.
- Do **not** add bank automation, Accessibility clicking, OTP/PIN handling, bank
  credential storage, private K PLUS APIs or root requirements. Ever.
- Do **not** add a second QR library, a second queue store or a second parser.
- Do **not** commit secrets; never touch `.env*` in git.
