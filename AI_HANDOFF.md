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

**0.9.0 (versionCode 11)** — the hand-off now enters AWAITING_USER_CONFIRMATION
*before* the bank app is launched (the four actions appear immediately and no
launch result can remove them), Home keeps one active QR area with the rest of
the queue listed below it, and Home has an Edit mode (drag & drop placement,
per-element show/hide, QR-image offset inside its frame, reset layout — all
persisted). Plus everything from V0.8: fixed-position control panel, one-tap
problem flow, screen lock, hand selector, lazy daily reset.

## CI status right now (read this first)

**`f0d6daa` GREEN (run `35483100157`, observed 2026-09-20): 230 unit tests,
`lintDebug`, `assembleDebug`, APK 9.4M. Everything pushed to `main` is compiled,
unit-tested, linted and packaged; the V0.9 section below is what that run
contains (on top of the V0.8 base the repository owner commits itself).**

| Commit | Run | Result |
| --- | --- | --- |
| `18c25ae` (owner's V0.8 base + its own four-action work) | `35481283802` | GREEN — the base this change was built on |
| `f186024` feat: show the four payment actions before the bank is launched (V0.9) | `35482887473` | RED — **1 unit test failure**: my new test expected `retryShare` to jump straight to the awaiting state instead of the real SHARING → awaiting pair |
| `f0d6daa` test: assert the real two-step retry in the launch-failure test | `35483100157` | **GREEN** — 230 unit tests, `lintDebug`, `assembleDebug`, APK 9.4M, artifact `qr-payment-queue-v0.9.0-debug` |

Earlier runs (kept as history — the rows below are the V0.6→V0.8 line):

| Commit | Run | Result |
| --- | --- | --- |
| `8a87a16` feat: one-handed UX, settings, problem reasons, daily reset | `35455075539` | RED (compile errors) |
| `a65948d` fix: OptIn for the experimental Material3 bottom sheet | `35455290188` | RED (test compile errors) |
| `ba3005f` fix: InMemoryAppSettingsStore import path | `35455677419` | RED — **3 unit test failures** |
| `6f4a032` fix: keep reported QRs unusable, restore Home priority, honour hand mode | `35466912022` | **GREEN** — `testDebugUnitTest`, `lintDebug`, `assembleDebug`, APK 9.3M |
| `2f86875` docs: record the observed V0.6 CI result | `35467134539` | GREEN |
| `766caef` feat: fixed-position control panel with icon-only ActionRail (V0.7) | `35469780618` | RED — `Icons.Outlined.Help` unresolved, `markUnknown` argument mismatch |
| `681076a` fix: replace `Icons.Outlined.Help` with `Icons.Outlined.Info` | `35470087863` | RED — still the `markUnknown` parameter mismatch |
| `7692662` fix: pass null detail to `markUnknown` to match parameter order | `35470329500` | GREEN |
| `3e16238` feat: 4-action one-handed payment flow with retry re-share (V0.8) | `35472604276` | RED — 7× unresolved `TestFixtures` in `PaymentQueueTest` |
| `786bdde` fix: use the shared `testItem` fixture in the retryShare tests | `35476118557` | **GREEN** — unit tests, lint, `assembleDebug`, APK artifact `qr-payment-queue-v0.8.0-debug` |

Run `35455677419` failed on:

- `PaymentQueueTest.homeOffersUnresolvedResultsBeforeAnythingElse` (ComparisonFailure, expected `unknown-1`, got `ready-1`)
- `ProblemFlowTest.reportProblem marks current QR as UNUSABLE` (NullPointerException)
- `ProblemFlowTest.reportProblem is no-op for already completed item` (AssertionError)

All three were fixed at their cause by `6f4a032` (below) and CI has confirmed it.
That run proves **compile + unit test + lint + APK packaging only** — no screen of
this app has still ever been rendered, so nothing here is a device pass.

## Latest change: V0.9 — QR hand-off flow + home layout Edit mode

### 1. The four actions no longer depend on the launch (§1 of the request)

`QueueViewModel.onShareItemRequested` now does exactly two steps, in this order:

1. `beginHandOff` → `enterAwaiting(itemId)` runs both transitions inside the
   ViewModel and saves **once, already in the four-button state**:
   `PaymentQueue.startSharing` (READY/FAILED → **SHARING**, records the `STARTED`
   attempt) then `PaymentQueue.shareLaunched` (SHARING →
   **AWAITING_USER_CONFIRMATION**, records `LAUNCHED`). That single `persist` call
   is what puts the rail on screen, and it happens **before any `Intent` exists**.
2. Only then is the selected bank re-probed (`BankTarget.query` + `preflight`) and
   the one-shot `imageIntent` published for the Compose effect to launch.

Every failure path is now `PaymentQueue.recordLaunchFailed` (new in V0.9), which
records a `FAILED` attempt, keeps the reason in `failureDetail` (rendered under the
QR) and **leaves the item in AWAITING_USER_CONFIRMATION** while showing a notice:
no bank selected, bank not installed, hand-off intent unresolvable, or the stored
image is gone. A launch callback with `launched == false` takes the same path.
**A failed launch can no longer turn the item into `FAILED`**, so the rail
(✓ ⚠ ? ↻) and the ↻ retry action always survive it, and no item is ever removed
from the pay flow because an external app would not open.

`shareLaunched` only accepts an item that is SHARING, so a duplicated launch
callback is refused instead of inflating the history
(`DoublePaymentTest.theLaunchIsRecordedOnlyOnce`). `retryShare` is AWAITING →
SHARING → AWAITING (one `persist` in the ViewModel) with a new `STARTED` attempt:
same QR, same Payment Item, same position, four actions still on screen.

One deliberate asymmetry, for whoever reads the attempt history next: the
`LAUNCHED` attempt is written **before** Android actually launches, because the
item must already be awaiting when anything is launched. When Android refuses, a
`FAILED` attempt is appended after it, so the last record is always the truth.
`failItem` still exists for a failure *before* any hand-off (the preview file of
`onViewItemRequested` is gone); that path does not own the rail and still moves a
READY item to `FAILED`.

There was **no lifecycle dependency to remove**: a grep of `app/src` shows the
ViewModel never used `ON_RESUME`, `LifecycleEventObserver` or
`onBankAppReturnedToForeground`; the launch result is reported by the Compose
effect that calls `onImageIntentLaunched`. `PaymentStatus.SHARING` is kept (it is
what a queue written mid-hand-off by an older version holds, and `resolveInterrupted`
still maps it to `UNKNOWN`), but V0.9 never *persists* it (the queue is saved after
both transitions, already awaiting), and the rail renders the four actions for it
as well (`isAwaiting` covers SHARING) so no hand-off state can show an empty panel.

### 2. Home had no panel at all for an item that owns the hand-off (real bug)

`PaymentQueue.nextActionItem` deliberately skips in-flight items —
`PaymentQueueTest.anItemAwaitingConfirmationIsNotOfferedAsTheNextNewAction`
asserts it — and `HomeTab` rendered its QR panel *only* from `nextActionItem`.
So once the user tapped the scan button, a queue whose only item was awaiting an
answer rendered **no control panel at all**: the four actions the brief asks for
could never appear. `QueueUiState.activeItem` (= `awaitingAnswerItem ?:
nextActionItem`) now feeds the top QR area, and `queuedItems` lists every other
open item below it.

### 3. Home layout (parts 2 and 3 of the request)

- **One active QR area.** The top shows the active QR (see above); every other
  open item is listed below it with the existing `CompactItemCard`, so a newly
  imported QR joins the list and the QR the user is working on is never replaced
  or duplicated. `QueueUiState.queuedItems` is pure and unit-tested; the domain is
  untouched here (`nextActionItem` / `awaitingAnswerItem` already existed).
- **Edit mode.** An Edit control sits next to Lock in the header
  (`[Lock] [Edit]`). In edit mode: elements are draggable one-handed
  (`HomeElementBox` + `detectDragGestures`, drag consumed so it can never reach
  the action underneath), each draggable element is outlined, and the
  `EditLayoutPanel` lists every hideable element with a show/hide control so a
  hidden element can always be brought back. Scrolling is disabled in edit mode,
  exactly like when Home is locked, so a drag is never read as a scroll.
- **Movable elements** (`HomeElement`): QR image, scan action, confirm, warning,
  unknown, retry and add-QR are draggable and **non-hideable** (the model says
  so); guidance, the import hint and the progress caption are draggable *and*
  hideable. A stored value can never hide a protected element —
  `HomeLayoutConfig.setVisible` refuses it, and a test asserts it.
- **QR image inside its frame.** The image can be dragged inside the frame
  (clipped by the frame's shape, `ContentScale.Fit` keeps the aspect ratio) and
  the offset is clamped to ±96 dp, so the code can never be dragged out of view.
- **Persistence.** `HomeLayoutCodec` encodes the whole layout as one string into
  the existing `AppSettingsStore` SharedPreferences file (no database, no new
  store). Anything unreadable decodes to `HomeLayoutConfig.DEFAULT`, so an
  existing user opens the app exactly as before.
- **Reset.** `EditLayoutPanel → รีเซ็ตผัง` asks for confirmation and then restores
  every placement, visibility and the QR-image offset. Lock blocks editing and
  reset (with a notice), and locking Home leaves edit mode.

### 4. Tests changed for the new contract (no assertion weakened)

- `PaymentQueueTest` (+2, now 34):
  `aLaunchThatNeverReachedTheBankKeepsTheItemAndItsFourActions` (the rail, the
  retry action and the queue position all survive a launch that never reached the
  bank, which is recorded as a `FAILED` attempt and nothing else) and
  `aLaunchFailureIsOnlyRecordedWhileTheItemWaitsForAnAnswer` (a stale callback is
  refused for a READY item and cannot reopen a completed one). No existing
  assertion was weakened or deleted.
- `HomeLayoutTest` (new, 18) and `HomeEditModeTest` (new, 7) cover the layout
  model/codec and the home-screen state (which QR the top area shows, the list
  below it, edit-mode defaults, element visibility).
- `DoublePaymentTest` (8) and `PaymentConfirmationTest` (10) were **left exactly as
  they were** on purpose: they exercise `failItem`, which still exists for a
  failure that happens before any hand-off. The launch path got its own new test
  instead of rewriting theirs.

## Previous change: the V0.8 retry tests could not compile (fixed)

`3e16238` added seven `retryShare` tests that call `TestFixtures.readyItem(...)`,
a helper that exists nowhere in `app/src/test`: `TestFixtures.kt` defines the
top-level fixtures `testVersion`, `testItem` and `testQueue`, and no object named
`TestFixtures` was ever added. `:app:compileDebugUnitTestKotlin` therefore failed
on all seven references, so `testDebugUnitTest` never ran at all — that is why
the V0.8 run was red (and why V0.7/V0.8 had no test evidence).

The fix uses the fixture that already exists instead of adding a second,
duplicate one: `testItem(id, position)` builds exactly the item those tests
describe — READY, with one CURRENT QR version — and `startSharing`,
`shareLaunched`, `confirmCompleted` and `retryShare` all reach the states the
tests assert from there (the attempts count reaches 6 in the multiple-retry
test, so its `>= 3` assertion holds honestly). No assertion was changed or
weakened.

Evidence: run `35476118557` (`786bdde`) is GREEN — `testDebugUnitTest`,
`lintDebug`, `assembleDebug`, APK existence/size checks, artifact
`qr-payment-queue-v0.8.0-debug`.

Still not evidenced anywhere: a rendered screen. CI compiles, unit-tests, lints
and packages; it never launches the app.

## Previous change: finish V0.6 (one-handed UX, settings, problem reasons, daily reset)

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

## Files changed (V0.6 change)
## V0.7/V0.8 changes (fixed-position control panel, 4-action one-handed payment flow)

The V0.7 redesign makes the Home tab a fixed control panel: QR preview on one
side, icon-only action buttons on the other, with the layout mirroring for
left/right hand preference.

### Key changes:

1. **Fixed-position ActionRail** (§3–§6): Home shows a split layout — QR preview
   on the left for right-hand mode, mirrored for left-hand. Action buttons sit
   in a fixed 80dp-wide rail that never shifts position.
2. **Icon-only buttons** (§2): Text is replaced with icons — Share/Scan (◉) for
   READY, CheckCircle (✓) for confirm, Warning (⚠) for problem, Help (?) for
   unknown. Muscle-memory ordering: top=done, middle=problem, bottom=unknown.
3. **Screen lock** (§11–§14): A lock toggle in Settings and a lock icon in the
   header. When locked, vertical scroll is disabled on Home so the QR and action
   positions never change. Lock state persists via SharedPreferences.
4. **Simplified problem flow** (§24–§25): The ProblemReasonsSheet is removed.
   Tapping ⚠ or ? is a one-tap action that immediately moves the item to
   REQUIRES_QR_REPLACEMENT or UNKNOWN and advances to the next QR. No reason
   selection, no confirmation dialog.
5. **Settings screen** (§39–§41): Settings text replaced with ⚙ icon in header.
   Lock toggle added. Hand preference stays as segmented buttons (left/right)
   with spatial positioning.
6. **No bottom nav bar on Home**: Home is a fixed control panel; the bottom nav
   was removed to prevent layout competition with the ActionRail.
7. **Import summary/banner hidden on Home**: Simplified to show only the
   ActionRail and QR area when an item is active.
8. **Version bump**: 0.6.0/8 → 0.7.0/9, artifact `qr-payment-queue-v0.7.0-debug`.

### Files changed:

- `data/AppSettingsStore.kt` — `saveHomeLocked`/`loadHomeLocked` for lock
  persistence.
- `ui/QueueViewModel.kt` — `homeLocked` state, `onLockToggled`, simplified
  `onReportProblem` (one-tap, no reason sheet), new `onMarkUnknown`. Removed
  `onReportProblemRequested`, `onProblemReasonDismissed`, `onProblemReasonSelected`.
- `ui/QueueScreen.kt` — New `HomeTab` with `ControlPanel`/`ActionRail` layout.
  `AppHeader` with ⚙ and 🔒 icons. `SettingsDialog` with lock toggle and
  segmented hand selector. Removed `ProblemReasonsSheet`, `ReadyItemCard`,
  `ConfirmPaymentPanel` (replaced by `ControlPanel`). Removed `QueueBottomBar`.
- `app/build.gradle.kts`, `.github/workflows/android.yml` — version 0.7.0.
- `data/AppSettingsStoreTest.kt` — 3 new lock persistence tests.
- `res/values/strings.xml` — new strings for lock, scan, unknown, progress.

- `domain/PaymentQueue.kt` — `nextActionItem` offers problem items again.
- `domain/QueueItem.kt` — `withNormalizedVersions` never revives a reported-unusable
  (or superseded) version.
- `data/QueueRepository.kt` — `problemReason` persisted and restored.
- `ui/QueueScreen.kt` — `OneHandActionBar` + hand preference threaded into
  `ReadyItemCard` and `ConfirmPaymentPanel`.
- `app/build.gradle.kts`, `.github/workflows/android.yml` — version 0.6.0
  (versionCode 8) and artifact `qr-payment-queue-v0.6.0-debug`.
- `domain/ProblemFlowTest.kt` — the two corrected expectations.
- `domain/QrReplacementTest.kt` — two new regression tests (reload keeps a
  reported-unusable QR unusable; a superseded QR is not revived).
- `README.md`, `docs/REAL_DEVICE_TEST.md`, this file — documentation.

## Tests

Pure JVM JUnit 4, no device, no emulator, no new dependency, no `@Ignore`.
**230 test methods across 20 classes** (`./gradlew testDebugUnitTest`): the count
is `grep -c "@Test"` over `app/src/test`, so the two new V0.9 classes
(`HomeLayoutTest`, `HomeEditModeTest`) are included.

| Suite | Methods | Covers |
| --- | --- | --- |
| `PaymentQueueTest` | 34 | every valid/invalid transition, ordering, counts, home priority, process death, the V0.9 launch-failure contract |
| `HomeLayoutTest` | 18 | the layout model: placement, clamping, visibility rules, the QR-image offset, the stored form and every damaged-input path |
| `HomeEditModeTest` | 7 | what the home screen renders: the active QR area, the queue list below it, edit-mode defaults, element visibility |
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
- GITHUB ACTIONS: **GREEN** on `6f4a032` (run `35466912022`) — `testDebugUnitTest`,
  `lintDebug`, `assembleDebug` and the APK checks all passed.

## CI result

- Last green run: `35466912022` (commit `6f4a032`, V0.6.0, APK 9.3M) —
  `BUILD SUCCESSFUL` for unit tests, lint, the APK build and the APK checks.
- Previous green run: `35451443398` (commit `ec84fdd`, V0.5.0, APK 9.3M).
- `35455677419` (commit `ba3005f`) — **FAILED** in `testDebugUnitTest` with the
  three failures listed above. `assembleDebug`, `lintDebug` and the APK steps
  never ran in that workflow because the test step failed first.

## APK artifact

- Workflow artifact name: `qr-payment-queue-v0.6.0-debug`
- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Last observed size: `9.3M` (run `35466912022`, the V0.6.0 build).
- This is the artifact a device test should install. It has never been installed.

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

1. Run the device checklist (now including the hand-mode, problem-reason, clear-item
   and daily-reset steps) and fill in `docs/REAL_DEVICE_TEST.md`. Everything above
   is compile- and unit-verified only.
2. Add Compose UI tests (androidTest + emulator) for the tabs, badge, one-handed
   band, replace-QR flow and confirm panel.
3. Persist the selected tab (e.g. via `SavedStateHandle`).
4. Re-verify the remaining Thai banks on Google Play and add the ones that pass.

## Commits so far (V0.5 → V0.6)

- `4647636` — feat: add payment tabs, QR replacement, problem badge and payment confirmation
- `ec84fdd` — test: assert the full attempt history after a retried hand-off
- `92b47ef` — docs: record the V0.5 tabs, state machine and replace-QR behaviour
- `8a87a16` — feat: one-handed UX, settings, problem reasons, daily reset (V0.6) — **CI red**
- `a65948d` — fix: add OptIn for experimental Material3 ModalBottomSheet API — **CI red**
- `ba3005f` — fix: correct InMemoryAppSettingsStore import path in DailyResetTest — **CI red**
- `6f4a032` — fix: keep reported QRs unusable, restore Home priority and honour hand mode — **CI green** (`35466912022`)

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
- **Do not make the four actions depend on anything the app cannot control.** The
  item enters AWAITING_USER_CONFIRMATION first and the launch is attempted second;
  a failed launch is recorded as an attempt (`recordLaunchFailed`), never as a
  `FAILED` item, because a `FAILED` item loses the rail and the retry action.
- **Do not re-point Home at `nextActionItem` alone.** It skips in-flight items by
  design, so rendering the panel only from it makes the four actions disappear the
  moment the user needs them. Use `QueueUiState.activeItem`.
- **Never add a second definition of Home's elements.** Placement, visibility and
  the QR-image offset all live in `HomeLayoutConfig` / `HomeLayoutCodec`; the UI
  reads them and must not keep its own copy of any of it.
- Do not claim a build, APK, CI or device pass that was not observed.
- **Watch the file-tool size limit.** `str_replace` silently stops matching past
  roughly the first 64 KB of `ui/QueueScreen.kt` (it reports "old string not
  found" for text that is plainly there). Split new UI into its own file
  (`ui/HomeLayoutUi.kt`) or edit the tail with an asserted, verified script
  instead of assuming the text is wrong.
