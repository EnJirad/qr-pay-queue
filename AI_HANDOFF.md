# AI_HANDOFF.md

This handoff reflects reality. Nothing below claims a build, an APK or a device
result that has not been observed. Update it after every meaningful work session.

## Project

QR Payment Queue — native Android application that queues QR payment screenshots
and hands each image to K PLUS through Android's own share sheet.

Package `com.enjirad.qrqueue`. Native Android only: Kotlin + Jetpack Compose +
AndroidX + Material 3 + Gradle Kotlin DSL. No web technology anywhere.

## Current version

0.4.0 (versionCode 4) — V0.4 **Image Queue + K PLUS Handoff**. This is a
deliberate simplification of 0.3.x: the QR decoder and all QR validation were
removed.

## What V0.4 changed (the headline)

The app used to decode and validate QR codes itself. It no longer does, at all.

```
BEFORE (0.3.x):  import → decode QR (ZXing) → validate payload → queue → share → confirm
AFTER  (0.4.0):  import → copy image        → queue          → share → K PLUS → user confirms
```

**QR Payment Queue does NOT decode QR codes.** It manages an image queue and
hands QR images to K PLUS through Android Share. The user verifies the recipient
and amount inside K PLUS and manually confirms completion inside QR Payment
Queue. K PLUS reads the QR, shows the payment, takes PIN / biometric, confirms
the transaction and produces the e-Slip.

Removed permanently: QR decoding (ZXing), EMVCo parsing, recipient / amount /
merchant / bank-account / PromptPay-ID validation, duplicate-payload detection,
multi-QR detection, and every field that stored any of it. **Do not reintroduce
any of it.**

## Version history

| Version | Scope |
| --- | --- |
| 0.1.0 | Native Android foundation, APK build, CI |
| 0.2.0 | Multi-image photo-picker import, app-private image storage |
| 0.3.0 | QR decoding (ZXing) + EMVCo PromptPay parsing + queue audit |
| **0.4.0** | **Image queue + K PLUS share hand-off + manual user confirmation; QR decoding and validation removed** |

## V0.4 architecture

```
Photo Picker → selected URIs → copy into app-private storage → QueueItem (QUEUED)
   → persist → show queue → share current image (ACTION_SEND, content:// URI)
   → K PLUS → user pays and authenticates in K PLUS → return
   → user answers "ทำรายการเสร็จแล้ว" / "ยังไม่เสร็จ"
       ├── done      → COMPLETED, next image becomes current
       └── not yet   → stays WAITING_USER, queue does not advance, retryable
```

### Queue states

`QUEUED → SHARING → WAITING_USER → COMPLETED`, with `FAILED` (known error) and
`UNKNOWN` (result not determinable). `FAILED` and `UNKNOWN` block the queue. The
current item is always the first item that is not `COMPLETED`, so a blocked item
is never skipped.

### QueueItem (V0.4 model)

`id`, `position`, `sourceUri`, `storedImagePath`, `displayName`, `mimeType`,
`status`, `createdAt`, `updatedAt`, plus `failureDetail` (a short diagnostic for
`FAILED` only, never payment data). No decoded payload, recipient, amount,
reference or validation result exists on the model.

### Files added

- `app/src/test/java/com/enjirad/qrqueue/data/ShareIntentSpecTest.kt` — the pure
  share hand-off contract (ACTION_SEND, `content://`, image MIME, read grant).

### Files changed

- `domain/PaymentStatus.kt` — replaced with the V0.4 state machine
  (`QUEUED`, `SHARING`, `WAITING_USER`, `COMPLETED`, `FAILED`, `UNKNOWN`);
  `canShareWithoutWarning` encodes which states may be shared without a
  double-payment warning.
- `domain/QueueItem.kt` — reduced to the image-queue fields listed above; all QR
  fields removed.
- `domain/PaymentQueue.kt` — rewritten: current item derived from item statuses,
  position-ordered items, and only the V0.4 transitions
  (`startSharing`, `shareLaunched`, `failCurrent`, `confirmCompleted`,
  `resolveUnknownCompleted`, `confirmNotCompleted`, `markUnknown`,
  `retryCurrent`, `resolveInterrupted`).
- `domain/QueueImport.kt` — removed the validation/outcome path; keeps URI
  de-duplication, id/extension/MIME helpers and `buildItem`.
- `data/QrImageFiles.kt` — removed `decodeQr`, `ArgbPixels` and all ZXing usage;
  only the preview bitmap loader remains.
- `data/QueueRepository.kt` — schema v2 (no QR fields), position-ordered items,
  and items whose stored image is missing are marked `FAILED` on load; reads
  0.3.x `fileName`/`importedAtMillis` fields so an old queue still opens.
- `data/QrShare.kt` — added the pure `ShareIntentSpec` / `shareSpec()`; the
  Android `Intent` is now assembled from that spec. Sharing is still
  `content://` + `FLAG_GRANT_READ_URI_PERMISSION`, still chooser-only.
- `data/ShareTargets.kt` — unchanged (still reports the share sheet and K PLUS
  availability honestly).
- `ui/QueueViewModel.kt` — pipeline rewritten: import → copy → QueueItem →
  persist → share → confirm. No decoder, no validator. Import creates no item
  for a copy that failed. Share gating, "share again" confirmation, process-death
  → UNKNOWN and user confirmation all live here.
- `ui/QueueScreen.kt` — rewritten for the V0.4 flow (queue list, current image,
  share, confirmation, failed/unknown handling, finished summary).
- `res/values/strings.xml` — new V0.4 copy; payment-critical prompts are Thai.
- `app/build.gradle.kts` — `versionName = "0.4.0"`, `versionCode = 4`; ZXing
  dependency removed.
- `gradle/libs.versions.toml` — ZXing entry removed.
- `.github/workflows/android.yml` — APK artifact renamed to
  `qr-payment-queue-v0.4.0-debug`.
- `README.md`, `AI_HANDOFF.md`, `docs/REAL_DEVICE_TEST.md` — updated for V0.4.

### Files removed

Production:

- `domain/QrImageDecoder.kt` (ZXing QR decoding)
- `domain/QrValidation.kt` (`ItemOutcome`, duplicate detection)
- `domain/ValidationIssue.kt`
- `domain/QrPayload.kt` (`QrPayload`, `QrFormat`, `RecipientKind`)
- `domain/EmvCoQrParser.kt` (EMVCo TLV parser + CRC)
- `domain/Money.kt` (`formatSatang`, `parseSatang` — no amounts in V0.4)

Tests:

- `domain/QrImageDecoderTest.kt`
- `domain/QrValidationTest.kt`
- `domain/EmvCoQrParserTest.kt`
- `domain/MoneyTest.kt`
- `domain/AmountParsingTest.kt`

## Dependency changes

- **Removed:** `com.google.zxing:core:3.5.4` (`zxing-core` in
  `gradle/libs.versions.toml`). It has no consumer left anywhere.
- **Added:** none. V0.4 uses only the Android platform, AndroidX/Compose and JUnit
  for tests.

## Tests

48 JUnit 4 test methods across 5 classes (no device, no emulator, no new
dependency, no `@Ignore`):

- `PaymentStatusTest` — only `COMPLETED` counts as completed; `SHARING` /
  `WAITING_USER` are never a payment; error states; which states may be shared
  without a warning.
- `PaymentQueueTest` — import order/positions; the happy path
  `QUEUED → SHARING → WAITING_USER → COMPLETED`; the failure path
  `QUEUED → SHARING → FAILED`; the unknown path
  `QUEUED → SHARING → WAITING_USER → UNKNOWN`; UNKNOWN is never completed
  automatically; `FAILED`/`UNKNOWN` block instead of being skipped; retry only on
  request; process death → UNKNOWN while keeping earlier confirmed results;
  finish only when every image is confirmed.
- `QueueImportTest` — URI de-duplication, storage extension / MIME helpers, and
  1 / 3 / 10 selected images becoming 1 / 3 / 10 queue items in order.
- `ShareIntentSpecTest` — the share hand-off contract: standard `ACTION_SEND`, a
  `content://` stream URI, an image MIME type with an `image/*` fallback, a
  read-URI grant, and `file://` URIs refused.
- `ShareTargetsTest` — unchanged; share-target and K PLUS availability
  classification.

Real device tests: **NOT RUN.** See below.

## Build result

- LOCAL BUILD: **not performed / not possible** — this sandbox has no JDK, no
  Android SDK, no emulator and no device. The build authority is GitHub Actions.
- GITHUB ACTIONS: **SUCCESS** on commit `b0d5c8b` (run `35438989074`, 2m35s).

## CI result

**GREEN.** Every step of `Android CI` succeeded on commit
`b0d5c8bf72b865cd23063b22d1d7a236004066b6`:

- Run URL: https://github.com/EnJirad/qr-pay-queue/actions/runs/35438989074
- `testDebugUnitTest`: **PASS** (48 tests, 0 failures)
- `lintDebug`: **PASS** (`abortOnError = true`; 0 errors, warnings only)
- `assembleDebug`: **PASS**
- APK existence / non-empty / inspect: **PASS**
- Artifact upload `qr-payment-queue-v0.4.0-debug`: **PASS**
- Build time: 2m35s

### Earlier V0.4 runs (kept so the failures are not repeated)

1. `35438463394` — **failed** `:app:compileDebugKotlin`. Kotlin nests block
   comments, and the literal `image/*` inside two KDoc blocks in `QrShare.kt`
   opened a nested comment that never closed. Fixed by rephrasing the comments.
2. `35438681659` — **failed** one unit test: `aBlockedItemStopsTheQueueInsteadOfBeingSkipped`
   asserted the pre-resolution `unknownCount`. The production behaviour was
   correct; the assertion was wrong (resolving an UNKNOWN item makes it
   `COMPLETED`). Fixed in the test.
3. `35438830461` — **failed** `:app:lintDebug` with one error that had previously
   been hidden by `abortOnError = false`: `ProduceStateDoesNotAssignValue` in the
   image preview loader. Fixed by loading the preview into
   `remember`/`mutableStateOf` from a `LaunchedEffect`.

## APK artifact

- Workflow artifact name: `qr-payment-queue-v0.4.0-debug` (uploaded, `if-no-files-found: error`)
- Path inside the workflow: `app/build/outputs/apk/debug/app-debug.apk`
- Last observed V0.4 build (run `35438989074`): APK exists and is non-zero —
  `9.2M`, containing `classes.dex` (18,137,284 bytes) and `AndroidManifest.xml`
  (6,512 bytes). For comparison, 0.3.0 produced a 9.5 MB APK.

## Real-device verification

**REAL DEVICE VERIFICATION: NOT YET DONE.** No device or emulator is available in
this environment. No claim anywhere in this repository says K PLUS accepted, read
or paid anything. The plan, including what counts as evidence, is in
`docs/REAL_DEVICE_TEST.md`; its results table is entirely `NOT RUN` and must stay
that way until a human fills it in from a real phone (target device: Xiaomi 15T
Pro, Android 16).

Must be verified on a device before the flow is described as working: the photo
picker import, the share sheet listing K PLUS, K PLUS accepting a shared image,
K PLUS reading a QR from it, and the return-trip confirmation UI.

## Known limitations

1. **Bank confirmation stays manual.** Without an official supported bank
   integration the app cannot know whether a transaction completed; the user
   records every result. Intentional.
2. **K PLUS cannot be verified from here** — needs a real device.
3. **Whether K PLUS reads a QR from a shared screenshot is KBank's behaviour.**
   If it does not, that is a finding about K PLUS, not something this app can work
   around.
4. K PLUS detection relies on the package name `com.kasikornbank.kplus`; a
   different package would only make the hint wrong, never the share sheet itself.
5. UI copy is mostly English; the payment-critical prompts are Thai.
6. No instrumented UI tests and no emulator in CI.
7. Lint now fails the build on lint errors (`abortOnError=true`), so a lint error
   cannot masquerade as a pass.
8. A queue written by 0.3.x is read defensively (its QR fields are ignored); a
   queue item whose image file is gone is shown as `FAILED` rather than crashing.
9. Pre-existing web-template leftovers `.env.example` and `.env.keys` are still
   tracked in this repository even though it is a native Android project and
   `.gitignore` ignores `.env*`. They are unused by the build; a future session
   should remove them deliberately (do not print their contents).

## Next task

1. **Real-device verification first** — install the `qr-payment-queue-v0.4.0-debug`
   APK, run the tests in `docs/REAL_DEVICE_TEST.md`, and fill in what was actually
   observed.
2. If K PLUS cannot accept a shared screenshot, document that plainly in
   `docs/REAL_DEVICE_TEST.md` and the README instead of inventing a workaround.
3. Optional: remove the `.env.example` / `.env.keys` web leftovers.
4. Optional V1.0: an explicitly supported reconciliation mechanism (official bank
   API) as a separate, verified layer.

## Important decisions

- The app is an **image queue + share hand-off helper**, not a QR processor. All
  QR reading and payment verification belongs to K PLUS and the user.
- The current item is derived from item statuses, never stored, so a restored
  queue can never point at the wrong image.
- Only an explicit user answer moves an item to `COMPLETED`.
- `UNKNOWN` blocks the queue and is never retried or completed automatically.
- Sharing is only started by a user action, and an item that may already have
  reached K PLUS needs an explicit confirmation before it is shared again.
- The image is handed off with a standard `ACTION_SEND` + `content://` URI + read
  grant, so K PLUS is reached exactly the way Android intends.
- The share contract is a pure `ShareIntentSpec` so it is unit tested without a
  device.
- Queue state lives in one JSON file plus one image directory; persistence
  happens on every mutation.
- No QR library, no second queue store, no second parser.

## Things future agents must NOT repeat

- Do **not** reintroduce QR decoding, ZXing, EMVCo parsing, or any recipient /
  amount / merchant / account / duplicate / multi-QR validation. V0.4 removed
  them on purpose.
- Do **not** claim K PLUS works, or that any real transaction happened, without
  real-device evidence recorded in `docs/REAL_DEVICE_TEST.md`.
- Do **not** claim a build or APK exists without CI evidence.
- Do **not** mark a payment `COMPLETED` because an image was shared, K PLUS was
  opened, or the app was resumed.
- Do **not** auto-retry a `FAILED` or `UNKNOWN` item; retry must be a user
  action.
- Do **not** auto-share on activity recreation, process restart, app resume or
  return from K PLUS.
- Do **not** create a queue item for an image that was not actually copied.
- Do **not** delete or move the user's gallery originals when importing.
- Do **not** add bank automation, Accessibility clicking, OTP/PIN handling, bank
  credential storage, private K PLUS APIs or root requirements. Ever.
- Do **not** reintroduce any web scaffold (Vite/React/Convex/package.json/
  `bun.lock`/root `src/`).
- Do **not** re-add an unanchored `src/` (or similar bare tool-folder) ignore
  rule: it hides `app/src/` from git and breaks a clean checkout.
- Do **not** remove the `<queries>` block: without it share-target detection on
  Android 11+ silently reports nothing.
- Do **not** let a share happen twice on the same item without the explicit
  confirmation dialog.
- Do **not** commit secrets; never touch `.env*` in git.
