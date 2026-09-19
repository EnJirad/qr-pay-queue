# AI_HANDOFF.md

This handoff reflects reality. Nothing below claims a build, an APK or a device
result that has not been observed. Update it after every meaningful work session.

## Project

QR Payment Queue — native Android application that queues QR payment screenshots
and hands each image straight to K PLUS, one image at a time.

Package `com.enjirad.qrqueue`. Native Android only: Kotlin + Jetpack Compose +
AndroidX + Material 3 + Gradle Kotlin DSL. No web technology anywhere.

## Current version

0.4.1 (versionCode 5) — V0.4.1 **Home Screen + One-by-One Direct K PLUS Flow**.

## What V0.4.1 changed (the headline)

The queue is now the home screen, the import screen closes itself, and K PLUS is
opened **directly** instead of through Android's "share with…" chooser.

```
V0.4.0:  import → progress card → queue screen with ONE current item → share sheet
         (chooser: the user picks K PLUS) → K PLUS → confirm → next item

V0.4.1:  SELECT IMAGES → IMPORT ALL → HOME (the queue, automatically) →
         SELECT ONE IMAGE → DIRECT K PLUS (package-addressed ACTION_SEND) →
         USER PAYS IN K PLUS → RETURN → USER CONFIRMS → NEXT IMAGE
```

1. **The import screen ends by itself.** Import progress is a single nullable
   `ImportProgress` in the UI state (`QueueUiState.importProgress`), and the
   progress screen is up exactly while it is non-null. It is cleared as soon as
   every selected image has been handled **and the queue is saved**, so the app
   returns to the queue without a Continue / Done / Next tap and can never stay on
   a completed "5 / 5" step.
2. **The home screen is the queue.** Every image is listed with its status, and
   each one has its own **แชร์ไป K PLUS** button, so the user chooses which image
   to pay next. The counts are total / completed / remaining.
3. **One payment hand-off at a time.** `PaymentStatus.holdsHandoff`
   (`SHARING`, `WAITING_USER`, `UNKNOWN`) may be true for at most one item;
   `PaymentQueue.canStartHandoff(itemId)` refuses a second hand-off while one is
   in flight or unresolved. A `FAILED` hand-off never reached K PLUS, so it does
   not block the rest of the queue.
4. **Direct K PLUS hand-off.** `QrShare.kPlusShareIntent` builds the image share
   and calls `setPackage("com.kasikornbank.kplus")`, so Android opens K PLUS
   directly. `Intent.createChooser` is gone from the payment path entirely
   (`ShareIntentSpec.targetPackage` is part of the pure, unit-tested contract).
5. **Per-item transitions.** `PaymentQueue` no longer has a "current item"; every
   transition takes an `itemId` (`startSharing`, `shareLaunched`, `failItem`,
   `confirmCompleted`, `resolveUnknownCompleted`, `keepWaiting`, `markUnknown`,
   `retryItem`). There is no hidden pointer that could drift after a restore.
6. **"ยังไม่เสร็จ" is now "ลองอีกครั้ง".** It does not mark anything complete and
   does not move the queue on; the image stays retryable and is handed over again
   only when the user taps **แชร์ไป K PLUS** (which first shows the
   double-payment warning).
7. **Import results are reported with counts.** A run that copied 4 of 5 images
   says so ("นำเข้าได้ 4 จาก 5 รูป") and creates no item for the image that failed.

### Bug found and fixed on the way (V0.4.0)

In 0.4.0 the import screen **never closed**. `QueueViewModel.onImagesPicked` set
`importing = true` and the completion path only called `persist(...)`, which
copied `queue` and `notice` but never reset `importing` — so after "5 of 5" the
app stayed on the progress card forever. V0.4.1 removes the separate boolean
entirely: `importing` is derived (`QueueImport.isImportRunning(importProgress)`),
`importProgress` is the single source of truth, and the completion path clears it
after the queue is saved. This is exactly the requirement "ห้ามค้างอยู่หน้า
Importing images 5 of 5 หลัง import สำเร็จ"; it was a real defect, not a
nice-to-have.

## Version history

| Version | Scope |
| --- | --- |
| 0.1.0 | Native Android foundation, APK build, CI |
| 0.2.0 | Multi-image photo-picker import, app-private image storage |
| 0.3.0 | QR decoding (ZXing) + EMVCo PromptPay parsing + queue audit |
| 0.4.0 | Image queue + K PLUS hand-off + manual user confirmation; QR decoding and validation removed |
| **0.4.1** | **Home-screen queue, self-closing import, one-by-one direct K PLUS hand-off (no chooser)** |

## V0.4.1 architecture

```
QueueUiState.importProgress != null   → import progress card (1 / 5 … 5 / 5)
QueueUiState.queue == null            → home (empty state + "+ เพิ่มรูป QR")
otherwise                             → the queue is the home screen

per item:  QUEUED ─ startSharing ─► SHARING ─ shareLaunched ─► WAITING_USER
                                                              │
                            user: "ทำรายการเสร็จแล้ว" ────────┴──► COMPLETED
                            user: "ลองอีกครั้ง" ────────────────► WAITING_USER (unchanged)
                            app stopped ─► resolveInterrupted ──► UNKNOWN
                            K PLUS refused the intent ─────────► FAILED
FAILED / UNKNOWN ─ retryItem (user only) ─► QUEUED
```

### Queue states (unchanged from V0.4)

`QUEUED`, `SHARING`, `WAITING_USER`, `COMPLETED`, `FAILED`, `UNKNOWN`.
Only the user's own answer completes an item; `UNKNOWN` is never retried or
completed automatically.

### QueueItem (unchanged)

`id`, `position`, `sourceUri`, `storedImagePath`, `displayName`, `mimeType`,
`status`, `createdAt`, `updatedAt`, plus `failureDetail` (a short diagnostic for
`FAILED` / `UNKNOWN` only, never payment data). No decoded payload, recipient,
amount, reference or validation result exists on the model. The persisted schema
is unchanged, so a 0.4.0 queue still opens.

### Files changed

- `domain/PaymentStatus.kt` — added the hand-off ownership helpers
  (`holdsHandoff`, `awaitsUserAnswer`, `isRetryable`, `canShare`,
  `needsReShareWarning`) and Thai labels for the queue chips.
- `domain/PaymentQueue.kt` — rewritten around **per-item** transitions
  (`startSharing(itemId)`, `shareLaunched(itemId)`, `failItem(itemId, detail)`,
  `confirmCompleted(itemId)`, `resolveUnknownCompleted(itemId)`,
  `keepWaiting(itemId)`, `markUnknown(itemId, detail)`, `retryItem(itemId)`) plus
  `canStartHandoff(itemId)`, `handoffItem`, `awaitingAnswerItem`, `unknownItem`.
  `currentItem` / `currentIndex` / `currentOrdinal` are gone.
- `domain/QueueImport.kt` — added `ImportProgress` (with `advance()`,
  `isComplete`, `fraction`), `ImportSummary` (`imported`, `failed`, `selected`)
  and `isImportRunning(progress)`; the single source of truth for "is the import
  screen up".
- `data/QrShare.kt` — `shareIntent(...)` (chooser) replaced by
  `kPlusShareIntent(...)` with `setPackage(K_PLUS_PACKAGE)`; `ShareIntentSpec`
  gained `targetPackage`; added the `ACTION_CHOOSER` constant so a test can prove
  a chooser is never built.
- `data/KPlusTarget.kt` **(new)** — replaces `data/ShareTargets.kt`. Reports
  `READY` / `INSTALLED_NOT_ADVERTISED` / `NOT_INSTALLED` for the direct hand-off:
  `classify(...)` is pure and unit tested, `query(...)` probes the real device.
  The button is offered whenever K PLUS is installed (a refused hand-off is
  reported honestly) and is disabled when it is not installed.
- `ui/QueueViewModel.kt` — import completion clears the progress state; hand-off
  and confirmation are per item (`onShareItemRequested(itemId)`,
  `onConfirmCompleted(itemId)`, `onKeepWaiting(itemId)`, `onRetryItem(itemId)`);
  `QueueNotice` updated; `QueueStage` removed (derived from `queue` +
  `importProgress`).
- `ui/QueueScreen.kt` — rewritten: the queue IS the home screen, per-item share
  buttons, an answer panel for the item that was handed to K PLUS, an unknown
  panel, a finished banner, and an import-result banner.
- `res/values/strings.xml` — V0.4.1 copy; `แชร์ไป K PLUS`, `ทำรายการเสร็จแล้ว`,
  `ลองอีกครั้ง`, `กลับหน้าแรก`.
- `app/build.gradle.kts` — `versionName = "0.4.1"`, `versionCode = 5`.
- `.github/workflows/android.yml` — artifact renamed to
  `qr-payment-queue-v0.4.1-debug`.
- `app/src/main/AndroidManifest.xml` — comments only (the `<queries>` block and
  the `FileProvider` are unchanged).
- `README.md`, `AI_HANDOFF.md`, `docs/REAL_DEVICE_TEST.md` — updated for V0.4.1.

### Files added

- `app/src/main/java/com/enjirad/qrqueue/data/KPlusTarget.kt`
- `app/src/test/java/com/enjirad/qrqueue/data/KPlusTargetTest.kt`
- `app/src/test/java/com/enjirad/qrqueue/ui/ImportCompletionTest.kt`

### Files removed

- `app/src/main/java/com/enjirad/qrqueue/data/ShareTargets.kt` — its job (find
  any app that can receive an image, for a chooser) no longer exists: the
  destination is K PLUS and nothing else.
- `app/src/test/java/com/enjirad/qrqueue/data/ShareTargetsTest.kt`

Nothing QR-related came back: there is still no decoder, no ZXing, no parser and
no validation anywhere in the project.

## Dependency changes

**None.** V0.4.1 removes a chooser and adds a package-targeted intent; both use
the Android platform. No dependency was added or removed, and
`gradle/libs.versions.toml` is unchanged (it still has no QR entry).

## Tests

69 JUnit 4 test methods across 6 classes, all pure JVM (no device, no emulator,
no `@Ignore`, no new dependency):

- `PaymentStatusTest` — only `COMPLETED` counts as completed; hand-off ownership
  (`holdsHandoff`); only `WAITING_USER` owes the user an answer; retry targets;
  which states may be handed over and which need the double-payment warning.
- `PaymentQueueTest` — import order/positions; the happy path
  `QUEUED → SHARING → WAITING_USER → COMPLETED`; the failure path
  `QUEUED → SHARING → FAILED`; the unknown path
  `QUEUED → SHARING → WAITING_USER → UNKNOWN`; UNKNOWN is never completed
  automatically; the user can work the queue in any order; **only one image can
  be in K PLUS at a time**; a failed hand-off does not block the rest of the
  queue; retry only on request; process death → UNKNOWN while keeping earlier
  confirmed results; finish only when every image is confirmed.
- `QueueImportTest` — URI de-duplication, storage extension / MIME helpers, and
  1 / 3 / 10 selected images becoming 1 / 3 / 10 queue items in order, each one
  hand-off ready.
- `ImportCompletionTest` **(new)** — `ImportProgress` reaches `isComplete` on the
  last image for 1 / 3 / 10 selections and never overshoots;
  `isImportRunning` is false once every image has been handled, so the queue/home
  content is shown and never a "5 / 5" progress card; `ImportSummary` reports full
  and partial imports (4 of 5) with both counts.
- `ShareIntentSpecTest` — the hand-off contract: standard `ACTION_SEND`, a
  `content://` stream URI, an image MIME type with the generic image fallback, a
  read-URI grant, `file://` refused, and **the target package is always
  `com.kasikornbank.kplus` with no chooser action**.
- `KPlusTargetTest` **(new)** — direct-target classification: ready when K PLUS
  advertises the image type, wildcard fallback, installed-but-not-advertised is
  still attempted, not installed cannot be handed off, non-image declared types
  become the generic image type.

Test methods per class: `PaymentQueueTest` 20, `QueueImportTest` 12,
`ImportCompletionTest` 11, `PaymentStatusTest` 10, `KPlusTargetTest` 9,
`ShareIntentSpecTest` 7.

## Build result

- LOCAL BUILD: **not performed / not possible** — this sandbox has no JDK, no
  Android SDK, no emulator and no device (`java` is not on `PATH`). The build
  authority is GitHub Actions.
- GITHUB ACTIONS: **SUCCESS** on commit `3f95a7c` (run `35440865143`, 2m1s). See
  the CI result section for the per-step detail.

## CI result

**GREEN.** Every step of `Android CI` succeeded on commit
`3f95a7c5261520d63fa0eaeb264cd07017a47a6a` (the tip of this change):

- Run URL: https://github.com/EnJirad/qr-pay-queue/actions/runs/35440865143
- `testDebugUnitTest`: **PASS** (69 test methods, `:app:testDebugUnitTest`
  succeeded — Gradle fails the task on any failing test)
- `lintDebug`: **PASS** (`abortOnError = true`; report written to
  `app/build/reports/lint-results-debug.html`)
- `assembleDebug`: **PASS**
- APK existence / non-empty / inspect: **PASS**
- Artifact upload `qr-payment-queue-v0.4.1-debug`: **PASS**
- Build time: 2m1s

### V0.4.1 runs that failed first (kept so the failures are not repeated)

1. `35440551412` — **failed** `:app:compileDebugUnitTestKotlin` with 8 errors, all
   in `PaymentQueueTest`: the per-item transitions take an `itemId`, and several
   calls still used the old V0.4 shape (`.confirmCompleted()`, `.markUnknown()`)
   or passed a timestamp where the item id belongs. Production code compiled on
   this run (`:app:compileDebugKotlin` succeeded).
2. `35440704872` — **failed** 2 of 69 unit tests, both wrong assertions rather
   than wrong production behaviour: a `FAILED` hand-off does not block the queue,
   so a second hand-off really does start (`SHARING`), and a refused hand-off
   leaves the item unchanged (`QUEUED`) rather than moving it to `UNKNOWN`.
   Fixed the assertions and spelled out the intent in both tests.

### Earlier V0.4 runs (kept so the failures are not repeated)

1. `35438463394` — **failed** `:app:compileDebugKotlin`. Kotlin nests block
   comments, and the literal text for the image wildcard MIME type inside two
   KDoc blocks in `QrShare.kt` opened a nested comment that never closed. Fixed
   by rephrasing the comments. **This can happen again**: never write the
   character sequence that opens a block comment inside a comment.
2. `35438681659` — **failed** one unit test that asserted a pre-resolution count.
   The production behaviour was correct; the assertion was wrong.
3. `35438830461` — **failed** `:app:lintDebug` with one error that had previously
   been hidden by `abortOnError = false` (`ProduceStateDoesNotAssignValue`).

## APK artifact

- Workflow artifact name: `qr-payment-queue-v0.4.1-debug`
  (uploaded with `if-no-files-found: error`)
- Path inside the workflow: `app/build/outputs/apk/debug/app-debug.apk`
- Last observed V0.4.1 build (run `35440865143`, commit `3f95a7c`): APK exists and
  is non-zero — `9.2M`, containing `classes.dex` (18,137,284 bytes) and
  `AndroidManifest.xml` (6,512 bytes).

## Real-device verification

**REAL DEVICE VERIFICATION: NOT YET DONE.** No device or emulator is available in
this environment. No claim anywhere in this repository says K PLUS opened
directly, accepted, read or paid anything.

Not verified on a device: whether the direct intent really opens K PLUS without a
chooser, whether K PLUS accepts a shared image at all, whether it reads a QR from
it, the return trip, and the confirmation UI. The plan, including what counts as
evidence, is in `docs/REAL_DEVICE_TEST.md`; its results table is entirely
`NOT RUN` until a human fills it in from a real phone (target device: Xiaomi 15T
Pro, Android 16).

## Known limitations

1. **Bank confirmation stays manual.** Without an official supported bank
   integration the app cannot know whether a transaction completed; the user
   records every result. Intentional.
2. **K PLUS cannot be verified from here** — needs a real device.
3. **Whether K PLUS accepts a direct image share is KBank's behaviour.** If it
   does not, the item is recorded as `FAILED` with that exact reason; the app does
   not fall back to a chooser and does not add an unsafe workaround.
4. **The direct hand-off relies on the package name `com.kasikornbank.kplus`.**
   A different package would make the hand-off fail with a clear message instead
   of opening the wrong app.
5. **No K PLUS means no hand-off**: the button is disabled and the reason is
   shown, because the destination is fixed.
6. UI copy is Thai with English status names.
7. No instrumented UI tests and no emulator in CI.
8. Lint fails the build on lint errors (`abortOnError=true`).
9. A queue written by 0.3.x is read defensively (its QR fields are ignored); an
   item whose image file is gone is shown as `FAILED` rather than crashing.
10. Pre-existing web-template leftovers `.env.example` and `.env.keys` are still
    tracked in this repository even though it is a native Android project and
    `.gitignore` ignores `.env*`. They are unused by the build; a future session
    should remove them deliberately (do not print their contents).

## Next task

1. **Real-device verification first** — install the
   `qr-payment-queue-v0.4.1-debug` APK, run the tests in
   `docs/REAL_DEVICE_TEST.md`, and fill in what was actually observed. The most
   important question is TEST 3: does tapping **แชร์ไป K PLUS** open K PLUS
   directly with no chooser?
2. If K PLUS does not accept the targeted `ACTION_SEND` image intent, record
   exactly that in `docs/REAL_DEVICE_TEST.md` and the README. Do not build an
   Accessibility / automation / private-API workaround.
3. Optional: remove the `.env.example` / `.env.keys` web leftovers.
4. Optional V1.0: an explicitly supported reconciliation mechanism (official bank
   API) as a separate, verified layer.

## Important decisions

- The app is an **image queue + hand-off helper**, not a QR processor. All QR
  reading and payment verification belongs to K PLUS and the user.
- The destination is fixed by the product: K PLUS only. That is why the chooser
  was removed and why an image cannot be handed off when K PLUS is missing.
- One hand-off at a time is enforced in the domain (`holdsHandoff` +
  `canStartHandoff`), not only in the UI, so a mis-tap or a restored queue cannot
  start two payments.
- Every transition names its item, so a restored queue can never act on the wrong
  image.
- Only an explicit user answer moves an item to `COMPLETED`.
- `UNKNOWN` blocks the queue and is never retried or completed automatically.
- The image is handed off with a standard `ACTION_SEND` + `content://` URI + read
  grant + `setPackage(K PLUS)`, so K PLUS is reached exactly the way Android
  intends.
- The hand-off contract is a pure `ShareIntentSpec` and the target decision is a
  pure `KPlusTarget.classify`, so both are unit tested without a device.
- Queue state lives in one JSON file plus one image directory; persistence
  happens on every mutation.
- No QR library, no second queue store, no second parser, no second navigation
  framework.

## Things future agents must NOT repeat

- Do **not** reintroduce QR decoding, ZXing, EMVCo parsing, or any recipient /
  amount / merchant / account / duplicate / multi-QR validation.
- Do **not** bring back `Intent.createChooser` in the payment path. The workflow
  is "แชร์ไป K PLUS": a chooser must never appear before K PLUS.
- Do **not** fall back to a chooser or to an unsafe workaround when the direct
  K PLUS intent fails. Record the failure honestly instead.
- Do **not** add bank automation, Accessibility clicking, OTP/PIN handling, bank
  credential storage, private K PLUS APIs, screen scraping or root. Ever.
- Do **not** claim K PLUS works, or that any real transaction happened, without
  real-device evidence recorded in `docs/REAL_DEVICE_TEST.md`.
- Do **not** claim a build or APK exists without CI evidence.
- Do **not** mark a payment `COMPLETED` because an image was shared, K PLUS was
  opened, or the app was resumed.
- Do **not** auto-retry a `FAILED` or `UNKNOWN` item; retry must be a user action.
- Do **not** auto-share on activity recreation, process restart, app resume,
  returning from K PLUS, or after a completed item.
- Do **not** let a second image be handed to K PLUS while one is `SHARING`,
  `WAITING_USER` or `UNKNOWN`.
- Do **not** reintroduce a separate `importing` boolean: `importProgress` is the
  single source of truth for whether the import screen is up, so the app can
  never get stuck on a finished "5 / 5" step again.
- Do **not** create a queue item for an image that was not actually copied.
- Do **not** delete or move the user's gallery originals when importing.
- Do **not** reintroduce any web scaffold (Vite/React/Convex/package.json/
  `bun.lock`/root `src/`).
- Do **not** re-add an unanchored `src/` (or similar bare tool-folder) ignore
  rule: it hides `app/src/` from git and breaks a clean checkout.
- Do **not** remove the `<queries>` block: without it the K PLUS lookup on
  Android 11+ silently reports "not found".
- Do **not** write the character sequence that opens a block comment inside a
  KDoc comment (Kotlin nests block comments and the file will not compile).
- Do **not** commit secrets; never touch `.env*` in git.
