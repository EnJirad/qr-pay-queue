# AI_HANDOFF.md

This handoff reflects reality. Nothing below claims a build that has not been
observed. Update it after every meaningful work session.

## Project

QR Payment Queue — native Android application for organizing QR payment tasks.

## Current version

0.2.0 (V2 — multi-image QR import + sequential payment queue)

## Current status

**V2 implemented and verified green in GitHub Actions.** This session replaced
the V0.1 "import is planned" placeholder with the real V2 workflow (multi-image
photo-picker import, ZXing decoding, EMVCo validation, duplicate detection, a
sequential queue with explicit confirmation, persistence and a summary).
The sandbox has **no JDK and no Android SDK**, so no local Gradle build was
possible here; verification is the observed CI run for commit `7dd3ed9`
(run [35430531448](https://github.com/EnJirad/qr-pay-queue/actions/runs/35430531448)):
unit tests, lint, `assembleDebug`, APK existence/size checks and artifact upload
all passed in 2m38s.

## V2 features implemented

1. **Multi-image import** through Android's photo picker
   (`ActivityResultContracts.PickMultipleVisualMedia`, no item cap so the
   device's own limit always applies). No folder picking, no storage
   permission, no device scan. Cancellation is handled.
2. **App-private image storage** — every selected image is copied into
   `filesDir/qrqueue/images/<itemId>.<ext>`. Gallery originals are never moved,
   renamed or deleted; clearing a queue deletes only app-created files.
3. **Real QR decoding** with ZXing core (`MultiFormatReader`, `TRY_HARDER`,
   QR_CODE only) from the pixels of the imported image; one downscaled pass
   plus a full-resolution retry when the QR is small in a big screenshot.
4. **Validation layer** distinguishing unreadable image, no QR found, malformed
   payload, CRC mismatch, unsupported payload (AID/currency/country), missing
   payment information and duplicate payload.
5. **Real payload parsing** — EMVCo TLV for PromptPay credit transfer (tag 29,
   AID `A000000677010111`) and Thai QR bill payment (tag 30, AID
   `A000000677010112`), with CRC-16/CCITT-FALSE verification. Amounts are parsed
   from tag 54 exactly (satang); a QR without an amount stays without an amount.
6. **Duplicate detection** on the normalised decoded payload text, so the same
   QR screenshot imported twice is reported instead of paid twice.
7. **Queue review screen** listing every image (ordinal, file name, recipient,
   format, amount, status chip, rejection reason) plus ready/invalid/duplicate
   counts, queue total and a note when QR codes carry no amount.
8. **Start confirmation** summarising QR codes to pay, total, invalid and
   duplicate counts before the run begins.
9. **Sequential processing**, one item at a time, with an explicit state
   machine (`PaymentQueue`): `READY → SUBMITTED → WAITING_CONFIRMATION →
   SUCCESS`, failure path to `PAYMENT_FAILED`, and `UNKNOWN` for anything the
   user cannot resolve on the spot.
10. **Legitimate Android hand-off only** — `ACTION_SEND` chooser through a
    `FileProvider` for the app-private image. No PIN/OTP/biometric entry, no
    Accessibility automation, no hidden bank API calls, no bank credentials.
11. **Explicit payment confirmation** — the app asks "Have you completed this
    payment?" with successful / failed / something-went-wrong. Sharing a QR
    never marks it paid. `UNKNOWN` blocks the queue and is never auto-retried.
12. **Progress display** — `Payment n / m`, a progress bar, completed and
    remaining counts and amounts, all from queue state.
13. **Final summary** — completed, successful, failed, unknown, excluded
    invalid/duplicates, total confirmed paid and queue total.
14. **Persistence / interruption handling** — the whole queue is persisted as
    JSON after every mutation; an item caught mid-hand-off when the app stops
    comes back as `UNKNOWN` and must be resolved explicitly. Restarting the app
    never marks a payment successful.

## Files added (this session)

- `app/src/main/java/com/enjirad/qrqueue/domain/QrPayload.kt` — payload model
  (`QrFormat`, `RecipientKind`, `QrPayload`).
- `app/src/main/java/com/enjirad/qrqueue/domain/EmvCoQrParser.kt` — TLV parser,
  CRC-16/CCITT-FALSE, mobile/national-ID formatting, AID handling.
- `app/src/main/java/com/enjirad/qrqueue/domain/QrImageDecoder.kt` — pure-JVM
  ZXing decode over ARGB pixels (`QrDecodeResult`).
- `app/src/main/java/com/enjirad/qrqueue/domain/QrValidation.kt` — decode result
  to `ItemOutcome` mapping, payload key normalisation, duplicate keys.
- `app/src/main/java/com/enjirad/qrqueue/domain/ValidationIssue.kt` — issues and
  their status mapping.
- `app/src/main/java/com/enjirad/qrqueue/domain/QueueImport.kt` — selection
  de-duplication, storage extension/MIME mapping, item building.
- `app/src/main/java/com/enjirad/qrqueue/domain/PaymentQueue.kt` — queue model,
  state machine transitions, aggregates, interruption resolution.
- `app/src/main/java/com/enjirad/qrqueue/data/QueueRepository.kt` — app-private
  image copies and JSON queue state (single owner of on-disk state).
- `app/src/main/java/com/enjirad/qrqueue/data/QrImageFiles.kt` — bitmap loading
  for decoding and preview.
- `app/src/main/java/com/enjirad/qrqueue/data/QrShare.kt` — share/view intents
  for the stored QR image.
- `app/src/main/res/xml/file_paths.xml` — FileProvider path (only the queue
  image directory).
- Tests: `EmvCoQrParserTest`, `QrImageDecoderTest`, `QrValidationTest`,
  `PaymentQueueTest`, `QueueImportTest`, `AmountParsingTest`.

## Files changed (this session)

- `gradle/libs.versions.toml` — added pinned `zxing = "3.5.4"` and
  `zxing-core`.
- `app/build.gradle.kts` — added the ZXing core dependency; version 0.2.0
  (versionCode 2).
- `app/src/main/AndroidManifest.xml` — added the `FileProvider` (exported
  false, per-use URI grants). Still **no permissions**.
- `app/src/main/java/com/enjirad/qrqueue/domain/PaymentStatus.kt` — added
  processable / pending / excluded / paid groupings.
- `app/src/main/java/com/enjirad/qrqueue/domain/Money.kt` — added strict
  `parseSatang` for amounts taken from QR payloads.
- `app/src/main/java/com/enjirad/qrqueue/domain/QueueItem.kt` — added stored
  image path, MIME type, issue, payload label and raw payload.
- `app/src/main/java/com/enjirad/qrqueue/ui/QueueViewModel.kt` — import
  pipeline, persistence, hand-off and confirmation actions.
- `app/src/main/java/com/enjirad/qrqueue/ui/QueueScreen.kt` — home, review,
  processing and summary stages plus the photo-picker route.
- `app/src/main/res/values/strings.xml` — V2 strings (and removal of the V0.1
  "import planned" copy).
- `README.md` — V2 workflow, format support, limitations.
- `app/src/test/java/com/enjirad/qrqueue/domain/PaymentStatusTest.kt` — added
  queue-grouping assertions.

## Files removed

None. The native project structure, single-activity Compose architecture and the
`domain`/`ui` split were preserved. `MainActivity` and the theme were not
touched.

## Dependencies added

| Dependency | Version | Why |
| --- | --- | --- |
| `com.google.zxing:core` | 3.5.4 | Real QR decoding from image pixels. Pure Java, no Android dependency, so the same decode path is unit tested on the JVM. One decoder only — no second QR library. |

No other dependency was added: image loading uses `BitmapFactory`, storage uses
`Context.filesDir`, state uses `org.json`, and sharing uses
`androidx.core.content.FileProvider` (already present through `core-ktx`).

## Tests performed

72 test methods (JUnit 4, one `@Test` each), all passing in CI run
`35430531448` (`./gradlew testDebugUnitTest` → BUILD SUCCESSFUL). They cover the
pure logic:

- **Parser** — real EMVCo PromptPay fixtures (dynamic with amount and reference,
  static without amount, bill payment, national ID, e-Wallet, unsupported AID,
  wrong CRC, malformed TLV, non-payment text, unsupported currency, missing
  format indicator, zero/invalid amount, missing recipient). Fixtures were
  generated with an independent CRC-16/CCITT-FALSE implementation and the
  standard check value `crc16CcittFalse("123456789") == 0x29B1` is asserted.
- **Decoder** — a QR is rendered with ZXing's encoder and read back through the
  app's own `QrImageDecoder`, then parsed end to end; blank images and
  impossible pixel data are asserted not to decode.
- **Validation** — unreadable, no-QR, decoder failure, duplicate (including
  whitespace-normalised payloads) and unsupported payload routing.
- **Queue state machine** — start at the first payable item and skip excluded
  ones, refusal to start with nothing payable, hand-off never marking paid,
  success only from `WAITING_CONFIRMATION`, failure recorded then continue,
  `UNKNOWN` blocking and requiring resolution, resolution refused for non-unknown
  items, interrupted hand-off becoming `UNKNOWN`, earlier confirmed results
  surviving a restart, pointer repair after recreation, amounts → totals,
  progress fraction, completion only when everything has a result, and
  human-verified statuses counting as paid.
- **Import helpers** — duplicate selected URIs, extension/MIME mapping, accepted
  vs rejected item building, unique ids.
- **Money** — exact satang parsing/formatting including rejection of malformed
  amounts.

**No local Gradle build was run** — this sandbox has no JDK and no Android SDK
(confirmed: `java` absent, `ANDROID_HOME` empty). The first real compile, lint
and APK build for V2 happens in GitHub Actions.

## Build command

```bash
./gradlew assembleDebug
```

## Build result

- LOCAL BUILD: **not performed / not possible** (no JDK, no Android SDK here).
- GITHUB ACTIONS (V2, commit `7dd3ed9`, run `35430531448`): **SUCCESS**, 2m38s.
  - `./gradlew testDebugUnitTest --stacktrace` → `BUILD SUCCESSFUL in 22s`
    (72 test methods, 0 failures).
  - `./gradlew lintDebug --stacktrace` → `BUILD SUCCESSFUL in 28s`.
  - `./gradlew assembleDebug --stacktrace` → `BUILD SUCCESSFUL in 45s`.
  - `test -f` / `test -s` on the APK → passed; `ls -lh` → `9.5M`;
    `unzip -l` shows a real APK (`classes.dex` 18,137,284 bytes and
    `AndroidManifest.xml`).
  - Artifact `qr-payment-queue-debug-apk` uploaded, final size 9,482,077 bytes,
    not expired.
- Failure history for this session:
  - Run `35430354194` (first V2 commit) **FAILED** at `testDebugUnitTest`:
    71 tests completed, 1 failed — `EmvCoQrParserTest > parsesBillPayment`, an
    `assertTrue(outcome is ParseOutcome.Accepted)`. Root cause: the parser chose
    the account type from the tag (29 vs 30) while the *fixture* carried the
    bill-payment AID (`A000000677010112`) under tag 29, so it was rejected as an
    unsupported application id. Fix: identify the scheme by its AID (tag 30
    remains the standard placement, tag 29 + bill-payment AID now also works),
    and correct the fixture to the standard tag 30 payload. Re-run green.
- Historical context: runs #1 and #2 of the old workflow failed inside
  `android-actions/setup-android@v3` before Gradle ran; the workflow was
  rewritten (`ubuntu-24.04`, `setup-android@v4` with explicit packages,
  `checkout@v5`, `setup-java@v5`) in the previous session.

## APK path

Expected: `app/build/outputs/apk/debug/app-debug.apk`

## APK verification result

Verified for commit `7dd3ed9` (run `35430531448`):

- `test -f app/build/outputs/apk/debug/app-debug.apk` → passed (file exists).
- `test -s app/build/outputs/apk/debug/app-debug.apk` → passed (non-zero).
- `ls -lh` → `-rw-r--r-- 1 runner runner 9.5M ... app-debug.apk`.
- `unzip -l` → real APK contents (`classes.dex`, `AndroidManifest.xml`, resources).
- Uploaded as artifact **`qr-payment-queue-debug-apk`** (9,482,077 bytes,
  `if-no-files-found: error`), artifact ID `10580268112`.

## Known limitations

1. **Bank confirmation is manual by design.** The app cannot know whether a bank
   transaction succeeded; unless an official supported integration exists, the
   user records every result. This is intentional, not a defect.
2. Only Thai EMVCo QRs (PromptPay tag 29, bill payment tag 30) are supported. A
   QR with another country code, currency or AID is rejected as unsupported
   rather than guessed at.
3. Duplicate detection compares decoded payload text; two different QR images
   that encode different payloads for the same debt are treated as two items.
4. HEIC/HEIF screenshots need Android 8.1+ (API 28) to decode; on API 26–27 such
   an image is reported as unreadable instead of being guessed.
5. `RECIPIENT_MISMATCH`, `AMOUNT_MISMATCH`, `ORDER_NOT_FOUND`, `EXPIRED` and
   `RECONCILED` exist in the status model but nothing sets them in V2 — they
   belong to a future reconciliation mechanism.
6. Queue state and images are app-private; uninstalling the app removes them.

## Next task

1. Optional/product work only from here: consider V1.0, an optional and
   explicitly supported reconciliation path (official bank/API integration) that
   can verify a payment without ever automating the user's banking app.
2. If a device is available, install the artifact and walk the real flow once
   (multi-image import → review → start → share → confirm → summary → relaunch
   mid-queue) and record what was observed. Nothing in this file claims a
   device run yet — only the CI build and the automated tests are observed.

## Important decisions

- One QR library (ZXing core) for one responsibility; no scanner activity and no
  camera code, because the product imports screenshots.
- Domain logic stays free of Android imports so the parser, decoder, validation
  and queue state machine are exercised by real JVM unit tests.
- The decoder works on ARGB pixels, which makes the exact production decode path
  testable and keeps the Android layer thin.
- Queue state lives in one JSON file plus one image directory; there is no second
  storage or database, and no parallel queue implementation.
- Persistence happens on every mutation, and an interrupted hand-off becomes
  `UNKNOWN` rather than a guess.
- `PaymentQueue` refuses any transition that would mark a payment successful
  without an explicit confirmation state.
- The photo picker contract is used with its default item limit so a device with
  a lower picker limit can never reject the launch.
- The merchant account scheme is identified by its EMVCo application ID rather
  than by the tag alone, so a bill-payment block placed under tag 29 is handled
  while unknown AIDs stay rejected as unsupported.
- The `.gitignore` web-scaffold patterns are anchored to the repository root:
  the previous unanchored `src/` rule silently ignored `app/src/`, which would
  have kept every new Android source file out of a clean checkout.

## Things future agents must NOT repeat

- Do **not** reintroduce any web scaffold (Vite/React/Convex/tsconfig/
  package.json/bun.lock/integrations.md/`src/`).
- Do **not** claim a build or APK exists without CI evidence. V2 is verified by
  run `35430531448` (commit `7dd3ed9`); any later code change needs its own green
  run before it is described as verified.
- Do **not** re-add an unanchored `src/` (or other bare tool-folder) ignore rule:
  it hides `app/src/` from git and breaks a clean checkout.
- Do **not** revert to folder selection, storage permissions or a device-wide
  scan: use the photo picker.
- Do **not** delete or move the user's gallery originals when importing.
- Do **not** create fake queue items, sample payments, demo QR payloads or
  placeholder "success" states.
- Do **not** add bank automation, Accessibility clicking, OTP/PIN handling, bank
  credential storage, or any "confirm payment" automation. Ever.
- Do **not** mark a payment `PAID` because a QR was opened, shared or displayed,
  and do **not** auto-retry an `UNKNOWN` result.
- Do **not** add a second QR library, a second queue store or a second parser.
- Do **not** commit secrets; never touch `.env*` in git.
