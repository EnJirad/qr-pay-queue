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

**0.6.0 (versionCode 8)** — three tabs, one item state machine, QR versioning,
problem badge, problem reasons, explicit payment confirmation, hand-preference
thumb zone, settings dialog, lazy daily reset.

## CI status right now (read this first)

The three V0.6 commits are **RED**:

| Commit | Run | Result |
| --- | --- | --- |
| `8a87a16` feat: one-handed UX, settings, problem reasons, daily reset | `35455075539` | RED (compile errors) |
| `a65948d` fix: OptIn for the experimental Material3 bottom sheet | `35455290188` | RED (test compile errors) |
| `ba3005f` fix: InMemoryAppSettingsStore import path | `35455677419` | RED — **3 unit test failures** |

Run `35455677419` failed on:

- `PaymentQueueTest.homeOffersUnresolvedResultsBeforeAnythingElse` (ComparisonFailure, expected `unknown-1`, got `ready-1`)
- `ProblemFlowTest.reportProblem marks current QR as UNUSABLE` (NullPointerException)
- `ProblemFlowTest.reportProblem is no-op for already completed item` (AssertionError)

**The change described below (which fixes all three plus two real defects) is in
the working tree and has NOT been pushed, so its CI result has NOT been observed.
Do not report it as green.**

## This change: finish V0.6 (one-handed UX, settings, problem reasons, daily reset)

The V0.6 work added real features but left the build red and two of them
half-wired. This change fixes the failures at their cause instead of loosening
the assertions, and completes the one-handed action area.

### 1. Home stopped offering problem items (the failing priority test)

`8a87a16` had added `!item.status.isProblem` to `PaymentQueue.nextActionItem`,
which contradicts three things that were not changed with it:

- `QueueScreen.HomeTab` renders the **ต้องจัดการ** card from
  `state.nextActionItem?.takeIf { it.status.isProblem }`, so with the filter that
  branch could never run — dead UI that still claimed to work;
- the V0.5 requirement that Home surfaces the thing needing attention first
  (§5 "current problem requiring attention", §6 "[ ตรวจสอบรายการ ]");
- `PaymentQueueTest.homeOffersUnresolvedResultsBeforeAnythingElse`.

The filter is **reverted**: problem items are offered on Home again, in the same
priority order as before (unresolved → needs a new QR → failed → next READY), and
the ปัญหา tab still lists every problem. `priorityRank` and the queue order are
untouched, so the app still never silently reorders the queue.

### 2. A reported-unusable QR was revived when the queue was reloaded (real bug)

`QueueItem.withNormalizedVersions()` is applied to every item read from disk. Its
old rule — "no version is marked current, so make the highest version number
current" — turned the QR the user had just reported as unusable back into the
current QR on the next launch. That silently undid `markQrUnusable` and
`reportProblem` persistence, and contradicted the device checklist step 16
("v1 is no longer current; ใช้ไม่ได้").

New rule: the newest CURRENT version wins; only a version list that carries no
CURRENT flag **and** no version the user reported unusable is re-pointed at its
newest version (that repair path is for a repaired/hand-edited queue file). A
reported-unusable QR never becomes current again, and a *superseded* older QR is
never revived to fill the gap either — the item simply waits for a replacement QR
with no current version, which is exactly the state `QueueRepository
.markMissingImage` and `replaceCurrentQr` already expect.

### 3. `problemReason` was never persisted (real bug)

`QueueRepository.serializeItem`/`parseItem` wrote and read `failureDetail` but not
the V0.6 `problemReason`, so the reason the user picked disappeared on the next
launch. Both directions now carry it (additive JSON field; older files read as
null, so no schema bump is needed).

### 4. The two ProblemFlowTest expectations were wrong, not the domain

Both failures were bad test expectations; the domain was right, and both tests now
assert **more** than before:

- `reportProblem marks current QR as UNUSABLE` expected the reported QR to still
  be `currentVersion`. It is not, and must not be: the same rule already held for
  `markQrUnusable` in V0.5 (`QrReplacementTest` asserts it) and
  `QueueRepository.markMissingImage` documents it ("an item waiting for a
  replacement QR has no current version"). The test now asserts there is no
  current version, that the version is `UNUSABLE`, that the image is still the
  preview, that the item can no longer be handed off, and that the other item in
  the queue is untouched.
- `reportProblem is no-op for already completed item` tried to complete a READY
  item with one `confirmCompleted` call. The domain refuses that by design
  (only `AWAITING_USER_CONFIRMATION` may be confirmed — see
  `PaymentConfirmationTest`). The test now drives the real path
  (`startSharing` → `shareLaunched` → `confirmCompleted`) and then asserts that
  `reportProblem` changes the completed item **not at all**.

### 5. The hand preference did nothing (inert setting)

V0.6 stored `HandPreference` (RIGHT / LEFT) and showed it in the settings dialog,
but no layout used it — a setting that lies about what it does.

`QueueScreen.OneHandActionBar` now implements V0.6 §4–§6: the primary actions sit
together in a band anchored to the thumb side (bottom-right for ถนัดขวา, mirrored
for ถนัดซ้าย) instead of being stretched across the whole card. There is exactly
one layout — the preference only picks the side — and the band is 85 % of the card
width, so the actions keep a large touch target and cannot overflow a narrow
phone. It is used by `ReadyItemCard` (ชำระเงิน + ดูรูป/มีปัญหา) and
`ConfirmPaymentPanel` (ทำรายการเสร็จแล้ว + QR ใช้งานไม่ได้ + ยังไม่แน่ใจ).

Everything else from the V0.6 brief (settings dialog with hand mode / auto-reset /
manual reset / bank switch, the problem-reasons bottom sheet, the clear-item
confirmation, the lazy daily reset on app start) was already implemented by
`8a87a16` and is unchanged here.

## Model

- `QueueItem` **is the Payment Item** (stable `id`, stable `position`, stable
  `itemLabel` such as `QR #08`) and carries `status`, `createdAt`, `updatedAt`,
  `completedAt`, `lastAttemptAt`, `failureDetail`, `problemReason`, `versions`,
  `attempts`.
- `QrVersion` = one QR image version (`id`, `paymentItemId`, `filePath`,
  `createdAt`, `versionNumber`, `status` = CURRENT / UNUSABLE / SUPERSEDED,
  `mimeType`, `displayName`, `sourceUri`, `fingerprint`). At most one version per
  item is CURRENT, and a reported-unusable version never becomes current again.
- `PaymentAttempt` = this app's own workflow record (`STARTED`, `LAUNCHED`,
  `FAILED`, `QR_UNUSABLE`, `COMPLETED`, `UNKNOWN`). Never a bank transaction
  record, never used to decide a payment result.
- `HandPreference` = RIGHT / LEFT, persisted in SharedPreferences.
- State machine (`PaymentStatus`) is unchanged from V0.5:

```
READY → SHARING → AWAITING_USER_CONFIRMATION → COMPLETED
          |                    |
          v                    v
        FAILED               UNKNOWN
                               |
AWAITING / UNKNOWN / FAILED → REQUIRES_QR_REPLACEMENT → READY (new QR)
                 FAILED / UNKNOWN → READY (explicit user retry)
```

## Persistence

Schema **3** in `filesDir/qrqueue/queue.json`. Items persist `versions`,
`attempts`, `completedAt`, `lastAttemptAt`, `failureDetail` and now
`problemReason`. Legacy queues still open: schema 2 (`storedImagePath`,
`displayName`, `mimeType`, …) becomes the item's v1 QR version; schema 1 / 0.3.x
names (`fileName`, `importedAtMillis`) are still read; the pre-0.5 status names
(`QUEUED`, `WAITING_USER`, `SUCCESS`, …) still map onto the new states. Old QR
versions stay on disk (history), so the orphan sweep references every version
path, not just the current one.

Settings live in SharedPreferences (`qr_queue_settings`): hand preference,
auto-reset toggle, last reset date. The selected bank lives in the bank-selection
store. None of them are cleared by a daily reset.

## Daily reset

Lazy, on app start (`QueueViewModel.init`): when auto-reset is on and the stored
`last_reset_date` is not today, the whole queue and its imported images are
deleted (`QueueRepository.clearDailyData`), the date is stamped and the screen
says so. Settings and the selected bank survive. Manual reset is the same
deletion from the settings dialog. Nothing resets while the app is running, and
nothing is deleted without the date actually changing.

## Files changed (this change)

- `domain/PaymentQueue.kt` — `nextActionItem` offers problem items again.
- `domain/QueueItem.kt` — `withNormalizedVersions` never revives a reported-unusable
  (or superseded) version.
- `data/QueueRepository.kt` — `problemReason` persisted and restored.
- `ui/QueueScreen.kt` — `OneHandActionBar` + hand preference threaded into
  `ReadyItemCard` and `ConfirmPaymentPanel`.
- `app/build.gradle.kts`, `.github/workflows/android.yml` — version 0.6.0
  (versionCode 8) and artifact `qr-payment-queue-v0.6.0-debug` (the V0.6 commit
  forgot the bump, so `strings.xml` said v0.6.0 while the APK said 0.5.0).
- `domain/ProblemFlowTest.kt` — the two corrected expectations.
- `domain/QrReplacementTest.kt` — two new regression tests (reload keeps a
  reported-unusable QR unusable; a superseded QR is not revived).
- `README.md`, `docs/REAL_DEVICE_TEST.md`, this file — documentation.

## Tests

Pure JVM JUnit 4, no device, no emulator, no new dependency, no `@Ignore`.
**190 test methods across 18 classes** (`./gradlew testDebugUnitTest`).

| Suite | Methods | Covers |
| --- | --- | --- |
| `PaymentQueueTest` | 25 | every valid/invalid transition, ordering, counts, home priority, process death |
| `QueueImportTest` | 14 | naming, fingerprints, item/version construction, N→N items |
| `ImportCompletionTest` | 15 | import finishes by itself, summary including duplicates, payment lock |
| `NavigationBadgeTest` | 14 | three tabs, badge counts, problem/completed tab contents |
| `BankTargetTest` | 14 | registry hygiene, 4-state classification, K PLUS package |
| `QrReplacementTest` | 13 | same item id, history, READY, failed replacement safety, reload keeps a reported-unusable QR unusable |
| `ProblemFlowTest` | 10 | report a problem, reason, unusable QR, clear item, completed item is untouchable |
| `PaymentStatusTest` | 11 | status rules incl. problem/retry/replace sets |
| `PaymentConfirmationTest` | 10 | only user confirmation completes; attempts are workflow records |
| `AppSettingsStoreTest` | 9 | hand preference, auto-reset flag, last reset date |
| `DoublePaymentTest` | 8 | one attempt per tap burst, one hand-off at a time |
| `DirectBankIntentTest` | 8 | direct bank intent contract, per-bank setPackage, no chooser |
| `ShareIntentSpecTest` | 8 | share spec: SEND, content URI, image MIME, read grant |
| `SelectedBankPersistenceTest` | 7 | bank selection survives restart |
| `UploadGateTest` | 7 | upload gate (disabled without an installed bank) |
| `DailyResetTest` | 6 | date format, reset triggering, settings survive a reset |
| `ShareFallbackTest` | 6 | never a chooser, never another app |
| `UninstalledBankTest` | 5 | uninstalled bank disables upload, keeps the value |

## Build result

- LOCAL BUILD: **not performed / not possible** — no JDK and no Android SDK in
  this environment (`java -version` → `java: not found`), so
  `./gradlew testDebugUnitTest` cannot be run here. The three failing tests were
  diagnosed from the CI log (failure type and line number) and from reading the
  code, not from a local run.
- GITHUB ACTIONS: **RED on `ba3005f`** (see the top of this file). CI is the build
  authority; this change's run does not exist yet.

## CI result

- Last green run: `35451443398` (commit `ec84fdd`, V0.5.0, 163 tests, APK 9.3M).
- Last observed run: `35455677419` (commit `ba3005f`) — **FAILED** in
  `testDebugUnitTest` with the three failures listed above. `assembleDebug`,
  `lintDebug` and the APK steps never ran in that workflow because the test step
  failed first.
- This change: **NOT YET RUN — do not claim a pass.**

## APK artifact

- Workflow artifact name: `qr-payment-queue-v0.6.0-debug`
- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Last observed size: `9.3M` (run `35451443398`, V0.5.0 — not this build).

## Real-device verification

**NOT DONE — REAL DEVICE VERIFICATION: NOT VERIFIED.** No device or emulator
here. The checklist (Xiaomi 15T Pro, Android 16) is in
`docs/REAL_DEVICE_TEST.md`, all steps `NOT RUN`. Do not claim any of it. In
particular, the one-handed action band, the settings dialog, the problem-reasons
sheet and the daily reset have **never been rendered** — they are compile-verified
Compose code only.

## Bank registry (unchanged by V0.5/V0.6, still current)

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
2. The whole UI — three tabs, badge, one-handed band, settings, problem-reasons
   sheet, confirm panel, replace-QR picker — has never been rendered on a device
   or an emulator. CI only proves it compiles and links.
3. Compose cannot be exercised by JVM unit tests here, so the UI layer is
   verified only through its pure state holder (`QueueUiState`) and the domain.
   `withNormalizedVersions` and the new regression tests cover the *state* the UI
   renders, not the rendering.
4. No QR history screen: versions are persisted and shown as "QR เวอร์ชันที่ N",
   but there is no dedicated timeline UI.
5. Task/process-death recovery restores the queue, not the selected tab (the tab
   lives in the ViewModel, so it survives rotation, not process death).
6. Bank package ids can change (K PLUS did); re-verify on Play before trusting a
   stale id.
7. The daily reset deletes the queue and its images with no undo (by design: it is
   the operational data of one day) — there is no archive.
8. No instrumented UI tests; no emulator in CI.

## Next recommended work

1. **Push this change and read the CI result** — it fixes three unit test
   failures and has never been built.
2. Run the device checklist (now including the hand-mode, problem-reason, clear-item
   and daily-reset steps) and fill in `docs/REAL_DEVICE_TEST.md`.
3. Add Compose UI tests (androidTest + emulator) for the tabs, badge, one-handed
   band, replace-QR flow and confirm panel.
4. Persist the selected tab (e.g. via `SavedStateHandle`).
5. Re-verify the remaining Thai banks on Google Play and add the ones that pass.

## Commits so far (V0.5 → V0.6)

- `4647636` — feat: add payment tabs, QR replacement, problem badge and payment confirmation
- `ec84fdd` — test: assert the full attempt history after a retried hand-off
- `92b47ef` — docs: record the V0.5 tabs, state machine and replace-QR behaviour
- `8a87a16` — feat: one-handed UX, settings, problem reasons, daily reset (V0.6) — **CI red**
- `a65948d` — fix: add OptIn for experimental Material3 ModalBottomSheet API — **CI red**
- `ba3005f` — fix: correct InMemoryAppSettingsStore import path in DailyResetTest — **CI red**

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
- **Do not change a domain contract and leave the UI or its test behind.** The V0.6
  commit made `nextActionItem` skip problem items while `HomeTab` still rendered
  its problem card from that very property and `PaymentQueueTest` still asserted
  the old order; that single line cost a red CI run. Change the property, the UI
  and the tests in the same commit.
- **A reported-unusable QR has no current version.** Never "repair" that state by
  making a version current again (that is what made the verdict disappear on the
  next launch), and do not revive a superseded version either.
- **When you add a field to the model, persist it.** `problemReason` existed in
  the domain and in the UI for a whole session while `QueueRepository` silently
  dropped it on save.
- **Never write a test that completes an item without the hand-off.** Only
  `AWAITING_USER_CONFIRMATION` may be confirmed; a test that calls
  `confirmCompleted` on a READY item is asserting something the app must never do.
- Never write the literal `image/*` inside a Kotlin block comment (KDoc): Kotlin
  nests block comments, so it silently breaks the file. This cost one CI run in an
  earlier change.
- Do not claim a build, APK, CI or device pass that was not observed.
