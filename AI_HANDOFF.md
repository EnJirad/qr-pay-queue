# AI_HANDOFF.md

This handoff reflects reality. Nothing below claims a build, an APK or a device
result that has not been observed. Update it after every meaningful work session.

## Project

QR Payment Queue — native Android application that lets the user select one
financial app, queue QR payment screenshots as **Payment Items**, and hand each
item's QR straight to that app, one item at a time.

Package `com.enjirad.qrqueue`. Native Android only: Kotlin + Jetpack Compose +
AndroidX + Material 3 + Gradle Kotlin DSL. No web technology, no WebView, no
Accessibility automation, no root, no PIN/OTP automation, no screen scraping, no
private bank API, no QR decoder, no automatic payment confirmation.

## Current version

**0.5.0 (versionCode 7)** — three-tab navigation, one item state machine, QR
versioning, problem badge, explicit payment confirmation.

## This change: 3-tab navigation + payment state + badge + replace QR

Goal: make the queue fast to work through, keep finished and unfinished items
impossible to confuse, and let a QR that the bank rejects be replaced **without
creating a new Payment Item**.

### Navigation (exactly three tabs, fixed order)

```
หน้าแรก   |   ปัญหา   |   ชำระแล้ว
```

- **หน้าแรก (Home)** — the action centre. Shows, in the required priority order:
  unresolved result → QR to replace → failed → the next READY item, then the rest
  of the queue, then `+ เพิ่ม QR`. Primary action follows the queue state
  (`ตรวจสอบ` / `ชำระเงิน` / `+ เพิ่ม QR`); the "วันนี้ชำระครบแล้ว" banner appears
  when every item is confirmed.
- **ปัญหา (Problems)** — every item whose status is `UNKNOWN`,
  `REQUIRES_QR_REPLACEMENT` or `FAILED`, in queue order, each with its own
  recovery actions.
- **ชำระแล้ว (Completed)** — only items the user confirmed, in the order they
  finished them, with their completion time.

### Problem badge

`QueueUiState.problemBadge` = `queue.problemCount` = number of items with
`status.isProblem`. READY items, in-flight items and completed items are never
counted; a count of 0 renders **no badge** (not "0"). The count is derived from
the queue state, so it updates the moment a problem is resolved.

### State machine (`PaymentStatus`, one authoritative machine)

```
READY → SHARING → AWAITING_USER_CONFIRMATION → COMPLETED
          |                    |
          v                    v
        FAILED               UNKNOWN
                               |
AWAITING / UNKNOWN / FAILED → REQUIRES_QR_REPLACEMENT → READY (new QR)
                 FAILED / UNKNOWN → READY (explicit user retry)
```

- `COMPLETED` is only reachable through the user's own tap
  (`confirmCompleted` from `AWAITING_USER_CONFIRMATION`,
  `resolveUnknownCompleted` from `UNKNOWN`). Opening the bank app, the share
  Intent succeeding, the Activity resuming and returning from the bank app are
  **not** transitions at all.
- `UNKNOWN` never auto-retries, never auto-completes and never auto-shares.
- `FAILED` means a failure in **this** app (missing file, unbuildable intent,
  bank could not be opened). It is not a generic replacement for a bank
  rejection.
- Only one item may `holdsHandoff` (`SHARING` / `AWAITING_USER_CONFIRMATION` /
  `UNKNOWN`) at a time, and `startSharing` re-checks that inside the domain, so
  rapid taps cannot start two attempts.

### Model

- `QueueItem` **is the Payment Item** (stable `id`, stable `position`, stable
  `itemLabel` such as `QR #08`). The class name was kept to avoid a
  repo-wide rename (the brief allows keeping the existing architecture); it
  carries `status`, `createdAt`, `updatedAt`, `completedAt`, `lastAttemptAt`,
  `failureDetail`, `versions`, `attempts`.
- `QrVersion` = one QR image version (`id`, `paymentItemId`, `filePath`,
  `createdAt`, `versionNumber`, `status` = CURRENT / UNUSABLE / SUPERSEDED,
  `mimeType`, `displayName`, `sourceUri`, `fingerprint`). Exactly one version per
  item is CURRENT; `withNormalizedVersions()` enforces it on load.
- `PaymentAttempt` = this app's own workflow record (`id`, `paymentItemId`,
  `qrVersionId`, `startedAt`, `result`, `reason`). Results: `STARTED`,
  `LAUNCHED`, `FAILED`, `QR_UNUSABLE`, `COMPLETED`, `UNKNOWN`. These are **not**
  bank transaction records and are never used to decide the payment result.

### Replace QR

`markQrUnusable` (from `AWAITING_USER_CONFIRMATION` / `UNKNOWN` / `FAILED`) keeps
the item in the queue, marks its current version `UNUSABLE` and sets
`REQUIRES_QR_REPLACEMENT`. `replaceCurrentQr` appends a new version (the previous
one becomes `UNUSABLE` if it had been reported unusable, otherwise `SUPERSEDED`),
sets it as the only current version and returns the item to `READY` — same item
id, same position, same `QR #08`. A replacement that cannot be used (blank path,
completed item, wrong state) is refused and leaves the previous QR untouched; the
ViewModel also deletes the orphan copy and reports "เปลี่ยน QR ไม่สำเร็จ".

### Persistence

Schema **3** in `filesDir/qrqueue/queue.json`: items now persist `versions` and
`attempts` (plus `completedAt` / `lastAttemptAt`). Legacy queues still open:

- schema 2 (`storedImagePath`, `displayName`, `mimeType`, …) becomes the item's
  v1 QR version;
- schema 1 / 0.3.x names (`fileName`, `importedAtMillis`) are still read, and the
  pre-0.5 status names (`QUEUED`, `WAITING_USER`, `SUCCESS`, …) still map onto
  the new states.

Old QR versions are **kept on disk** (history), so the orphan sweep now
references every version path, not just the current one.

### Import

Multi-select import is unchanged in behaviour and still ends by itself, but now:
per-image outcomes are `imported` / `duplicate` / `failed`; a partial import
keeps everything that succeeded; an exact duplicate (SHA-256 of the copied bytes,
computed while copying) is skipped and reported as `↷ ข้ามรายการซ้ำ N รายการ`.
No perceptual or QR-content comparison is used anywhere.

### Double-payment protection

1. UI: the payment button is disabled while `paymentInFlight` (an `imageIntent`
   of kind SHARE is pending).
2. ViewModel: `onShareItemRequested` refuses while a hand-off is in flight and
   refuses when `queue.canStartHandoff` is false.
3. Domain: `startSharing` requires `status.canShare` **and** a free hand-off
   **and** a current QR version; a second call is a no-op that records nothing.

## Files changed (this change)

- `domain/PaymentStatus.kt` — rewritten state set + rules.
- `domain/QrVersion.kt` — **new** `QrVersion` / `QrVersionStatus` /
  `PaymentAttempt` / `PaymentAttemptResult`.
- `domain/QueueItem.kt` — Payment Item with versions + attempts, `itemLabel`,
  `previewFilePath`, `withNormalizedVersions`.
- `domain/PaymentQueue.kt` — transitions incl. `markQrUnusable`,
  `replaceCurrentQr`, `problemItems`, `completedItems`, `problemCount`,
  `nextActionItem` (priority + queue order), `hasFingerprint`.
- `domain/QueueImport.kt` — fingerprinting (`fingerprintOf` / `fingerprintHex`),
  `buildVersion`, `ImportSummary(imported, failed, duplicates)`.
- `data/QueueRepository.kt` — schema 3, version/attempt (de)serialisation, legacy
  migration, fingerprint while copying, missing-image check on the current
  version.
- `ui/QueueViewModel.kt` — `QueueTab`, badge, duplicate skip, replace-QR flow,
  payment lock, notices.
- `ui/QueueScreen.kt` — three tabs, bottom navigation with badge, home action
  centre, problem cards, completed list, replace-QR picker, confirm panel.
- `res/values/strings.xml` — new tab / status / action / empty-state copy.
- `app/build.gradle.kts`, `.github/workflows/android.yml` — version 0.5.0
  (versionCode 7) and artifact `qr-payment-queue-v0.5.0-debug`.
- Tests: `PaymentQueueTest`, `PaymentStatusTest`, `QueueImportTest`,
  `ImportCompletionTest` rewritten; **new** `TestFixtures.kt`,
  `QrReplacementTest.kt`, `PaymentConfirmationTest.kt`, `DoublePaymentTest.kt`,
  `NavigationBadgeTest.kt`.

## Tests

Pure JVM JUnit 4, no device, no emulator, no new dependency, no `@Ignore`.
**163 test methods** across 15 classes (`./gradlew testDebugUnitTest`).

| Suite | Methods | Covers |
| --- | --- | --- |
| `PaymentQueueTest` | 25 | every valid/invalid transition, ordering, counts, home priority, process death |
| `ImportCompletionTest` | 15 | import finishes by itself, summary including duplicates, payment lock |
| `QueueImportTest` | 14 | naming, fingerprints, item/version construction, N→N items |
| `NavigationBadgeTest` | 14 | three tabs, badge counts, problem/completed tab contents |
| `BankTargetTest` | 14 | registry hygiene, 4-state classification, K PLUS package |
| `PaymentStatusTest` | 11 | status rules incl. problem/retry/replace sets |
| `QrReplacementTest` | 11 | same item id, history, READY, failed replacement safety |
| `PaymentConfirmationTest` | 10 | only user confirmation completes; attempts are workflow records |
| `DoublePaymentTest` | 8 | one attempt per tap burst, one hand-off at a time |
| `DirectBankIntentTest` | 8 | direct bank intent contract, per-bank setPackage, no chooser |
| `ShareIntentSpecTest` | 8 | share spec: SEND, content URI, image MIME, read grant |
| `SelectedBankPersistenceTest` | 7 | bank selection survives restart |
| `UploadGateTest` | 7 | upload gate (disabled without an installed bank) |
| `ShareFallbackTest` | 6 | never a chooser, never another app |
| `UninstalledBankTest` | 5 | uninstalled bank disables upload, keeps the value |

## Build result

- LOCAL BUILD: **not performed / not possible** — no JDK/SDK in this environment.
- GITHUB ACTIONS: **SUCCESS** — CI is the build authority and it produced and
  verified the APK.

## CI result

**GREEN** on commit `ec84fdd` (run `35451443398`, 2m17s):

- `testDebugUnitTest`: **PASS** (163 test methods)
- `lintDebug`: **PASS** (`abortOnError = true`)
- `assembleDebug`: **PASS**
- APK existence / non-empty / inspect: **PASS**
- Artifact upload `qr-payment-queue-v0.5.0-debug`: **PASS**
- Run URL: https://github.com/EnJirad/qr-pay-queue/actions/runs/35451443398

The first run of this change (`35451341900`, commit `4647636`) failed one unit
test, `DoublePaymentTest.aFailedHandOffDoesNotConsumeTheOnlyAttempt`, because the
test expected 2 attempt records where the domain keeps all three
(`STARTED, FAILED, STARTED`). The code was right; the expectation was fixed in
`ec84fdd` and CI went green.

## Commits

- `4647636` — feat: add payment tabs, QR replacement, problem badge and payment confirmation
- `ec84fdd` — test: assert the full attempt history after a retried hand-off

## APK artifact

- Workflow artifact name: `qr-payment-queue-v0.5.0-debug`
- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Last observed size (run `35451443398`): `9.3M`

## Real-device verification

**NOT DONE — REAL DEVICE VERIFICATION: NOT VERIFIED.** No device or emulator
here. The 20-step checklist (Xiaomi 15T Pro, Android 16) is in
`docs/REAL_DEVICE_TEST.md`, all steps `NOT RUN`. Do not claim any of it.

## Bank registry (unchanged by this release, still current)

`BankRegistry.allBanks` is the single source of truth. 13 package ids read from
each app's **live Google Play listing on 2026-09-19**: K PLUS
`com.kasikorn.retail.mbanking.wap`, SCB EASY `com.scb.phone`, Krungthai NEXT
`ktbcs.netbank`, Bangkok Bank `com.bbl.mobilebanking`, krungsri
`com.krungsri.kma`, ttb touch `com.TMBTOUCH.PRODUCTION`, MyMo by GSB
`com.mobilife.gsb.mymo`, CIMB THAI `com.cimbthai.digital.mycimb`, UOB TMRW
`com.uob.mightyth2`, Dime! `com.dimekkp.dimeapp`, MAKE by KBank
`com.kasikornbank.makebykbank`, Kept `com.krungsri.kept`, TrueMoney
`th.co.truemoney.wallet`. The old (dead) ids `com.kasikornbank.kplus`,
`com.scb.BankApp`, `com.krungthai.nextbanking` and `com.bblmobilebanking` must
stay absent; a test asserts it.

"Google Play package verified" is **not** "share verified": `QrShareSupport` is
`UNKNOWN` for every bank until a device test proves otherwise.

## Known limitations

1. Share capability is unverified for every bank (needs a device).
2. **The three tabs are not covered by instrumented UI tests** and have never
   been rendered on a device or an emulator: the badge, the `NavigationBar`, the
   replace-QR picker and the confirm panel compile in CI (so the Compose code
   type-checks and links) but no screen has been observed visually.
3. Compose cannot be exercised by JVM unit tests here, so the UI layer is
   verified only through its pure state holder (`QueueUiState`) and the domain.
4. No QR history screen: version history is persisted and shown as "QR เวอร์ชันที่
   N" on the card, but there is no dedicated timeline UI.
5. Task/process-death recovery restores the queue, not the selected tab (the tab
   lives in the ViewModel, so it survives rotation, not process death).
6. Bank package ids can change (K PLUS did); re-verify on Play before trusting a
   stale id.
7. No instrumented UI tests; no emulator in CI.

## Next recommended work

1. Run the 20-step device checklist and fill in `docs/REAL_DEVICE_TEST.md`.
2. Add Compose UI tests (androidTest + emulator) for the tabs, badge, replace-QR
   flow and confirm panel.
3. Persist the selected tab (e.g. via `SavedStateHandle`) and add a QR version
   history view for an item.
4. Re-verify the remaining Thai banks on Google Play and add the ones that pass.

## Things future agents must NOT repeat

- Do not guess a bank package id. Verify on the live Google Play listing and
  record the date.
- Do not treat "package verified" as "share verified".
- Do not report "not installed" from a failed `queryIntentActivities`; installed
  and share-capable are separate states (`UNKNOWN` ≠ `NOT_INSTALLED`).
- Do not complete an item, retry an `UNKNOWN` item or start the next payment
  automatically.
- Do not delete an item because its QR is unusable — replace the QR instead.
- Do not delete an old QR image on replacement: it is the item's history.
- Never write the literal `image/*` inside a Kotlin block comment (KDoc): Kotlin
  nests block comments, so it silently breaks the file. This cost one CI run in
  the previous change.
- Do not claim a build, APK, CI or device pass that was not observed.
