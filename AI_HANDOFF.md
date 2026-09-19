# AI_HANDOFF.md

This handoff reflects reality. Nothing below claims a build, an APK or a device
result that has not been observed. Update it after every meaningful work session.

## Project

QR Payment Queue — native Android application that lets the user select a
banking app, queue QR payment screenshots, and hand each image straight to that
bank, one image at a time.

Package `com.enjirad.qrqueue`. Native Android only: Kotlin + Jetpack Compose +
AndroidX + Material 3 + Gradle Kotlin DSL. No web technology anywhere.

## Current version

0.4.2 (versionCode 6) — V0.4.2 **Direct Bank Share + Persistent Bank Selection +
Upload Gate**.

## What V0.4.2 is

```
V0.4.1:  import → queue → share to K PLUS (hardcoded) → confirm
V0.4.2:  SELECT BANK → persist → import gate → queue → share to the selected
         bank's package only → confirm
```

1. **Bank selection on the home screen.** A `BankSelectionCard` at the top of
   every screen shows the current bank's status (ready / installed-not-advertised
   / not-found / not-selected) with a button to choose or change.
2. **Upload gate.** `QueueUiState.canImport` is the single source of truth:
   `selectedBank != null && bankStatus?.canHandOff == true`. The import button is
   disabled when this is false — the gate is structural, never a toast after the
   fact.
3. **Bank selection dialog.** Probes every known bank on the device at dialog
   open time (`BankTarget.query`). Installed + advertising banks are selectable,
   not-installed banks are greyed out.
4. **Persistent selection.** `BankSelectionStore` (production:
   `SharedPreferencesBankSelectionStore`) stores `selectedBankPackage` +
   `selectedBankId`. On init the ViewModel loads the bank and **re-probes it on
   the current device**. If the bank was uninstalled since the last session the
   upload gate is disabled immediately and the user is told (V0.4.2 §12/§13). The
   old selection is kept so the card can still show "K PLUS — ไม่พบแอป".
5. **Direct bank share, no Sharesheet.** `QrShare.bankShareIntent` takes
   `targetPackage` and calls `setPackage(...)`. `Intent.createChooser` is not used
   anywhere in the app. The `ShareIntentSpec.targetPackage` field is the unit-
   tested contract.
6. **Target re-verification at share time.** `QueueViewModel.beginHandOff`
   re-queries the selected bank immediately before the hand-off
   (`BankTarget.query` → installed?) and rejects the launch through
   `BankTarget.preflight` + `BankShareFlow.noticeFor` when there is no bank or the
   package is gone. `QueueScreen.launchImageIntent` additionally requires
   `intent.resolveActivity(...)` to resolve before `startActivity`, and any
   `ActivityNotFoundException` / `SecurityException` becomes a FAILED item with an
   error — never a chooser and never another app (V0.4.2 §9/§11/§17).
7. **`isInstalled` and `canReceiveQrImage` are separate.** `BankTarget` exposes
   both. "Package installed" and "advertises this share activity" answer different
   questions on Android 11+, and an installed-but-not-advertising bank is still
   attempted and its result reported honestly (V0.4.2 §11C/§14).
8. **Generalized copy.** The user-visible strings no longer hardcode K PLUS:
   the confirm panel, re-share dialog and waiting card use the selected bank's
   display name, and `PaymentStatus.SHARING` reads "กำลังเปิดแอปธนาคาร".
9. **Queue and state machine unchanged.** `QUEUED → SHARING → WAITING_USER →
   COMPLETED`, `FAILED`, `UNKNOWN` — the V0.4.1 state machine, per-item
   transitions, single-hand-off rule and manual confirmation are untouched.
   Changing the selected bank does not affect queued items, their status, or
   their share targets; the next share uses the bank selected at that moment
   (V0.4.2 §8/§18/§19/§20).

QR decoding is **not** part of V0.4.2 and must not come back (V0.4.2 §21).

## Version history

| Version | Scope |
| --- | --- |
| 0.1.0 | Native Android foundation, APK build, CI |
| 0.2.0 | Multi-image photo-picker import, app-private image storage |
| 0.3.0 | QR decoding (ZXing) + EMVCo PromptPay parsing + queue audit |
| 0.4.0 | Image queue + K PLUS hand-off + manual user confirmation; QR decoding removed |
| 0.4.1 | Home-screen queue, self-closing import, one-by-one direct K PLUS hand-off |
| **0.4.2** | **Bank selection, persistent selected bank, upload gate, direct bank share** |

## Files added (this session)

- `app/src/main/java/com/enjirad/qrqueue/data/BankSelectionStore.kt` —
  `BankSelectionStore` interface, `SharedPreferencesBankSelectionStore`, and the
  pure `BankSelectionCodec` (id-first, package-fallback, mismatch-rejecting).
- `app/src/test/java/com/enjirad/qrqueue/data/InMemoryBankSelectionStore.kt` —
  test-only store backed by a map so a restart can be simulated.
- `app/src/test/java/com/enjirad/qrqueue/data/DirectBankIntentTest.kt` (7 methods).
- `app/src/test/java/com/enjirad/qrqueue/data/SelectedBankPersistenceTest.kt` (7 methods).
- `app/src/test/java/com/enjirad/qrqueue/ui/UninstalledBankTest.kt` (5 methods).
- `app/src/test/java/com/enjirad/qrqueue/ui/ShareFallbackTest.kt` (6 methods).

`BankInfo.kt` (from the earlier V0.4.2 work) still holds `BankInfo`,
`BankAvailability`, `BankTargetStatus`, `BankRegistry`, and now the new
`BankShareReadiness` enum.

## Files changed (this session)

- `data/BankTarget.kt` — added the pure `preflight(selectedBank, installed)`,
  public `isInstalled(context, bank)` and `canReceiveQrImage(context, bank)`;
  `query` now composes those two instead of duplicating the probe.
- `data/QueueRepository.kt` — the bank selection now delegates to a
  `BankSelectionStore` (constructor-injectable); the old SharedPreferences keys
  moved into `BankSelectionCodec`.
- `ui/QueueViewModel.kt` — new pure `BankShareFlow.noticeFor(...)`; the
  `QueueNotice.SHARE_FAILED` became `SHARE_TARGET_UNAVAILABLE`; added
  `QueueUiState.requiresBankSelection`; share and re-share now go through
  `beginHandOff` which re-verifies the target; a failed launch reports the
  cannot-open error instead of silently failing.
- `ui/QueueScreen.kt` — `launchImageIntent` requires the share intent to resolve
  before launching (no chooser fallback); the confirm panel, re-share dialog and
  waiting card now take the selected bank name; added a `bank_generic` fallback.
- `domain/BankInfo.kt` — added the `BankShareReadiness` enum.
- `domain/PaymentStatus.kt` — the `SHARING` label is now "กำลังเปิดแอปธนาคาร".
- `res/values/strings.xml` — `notice_share_failed` → `notice_share_target_unavailable`
  (the V0.4.2 §17 wording), `confirm_body`/`reshare_dialog_title`/`waiting_body`
  now take the bank name, added `bank_generic`.
- `data/QrShare.kt`, `app/build.gradle.kts` — comments generalized from K PLUS to
  the selected bank (no behavior change; the package was never in the comments).

## Dependency changes

**None.** SharedPreferences is part of the Android platform. No new library was
added or removed.

## Tests

Pure JVM JUnit 4 (no device, no emulator, no `@Ignore`, no new dependency):

- Existing 8 classes (per the last observed CI run: 76 methods) —
  `PaymentStatusTest`, `PaymentQueueTest`, `QueueImportTest`, `ImportCompletionTest`,
  `ShareIntentSpecTest`, `BankTargetTest`, `UploadGateTest`.
- New this session (25 methods): `DirectBankIntentTest` (ACTION_SEND, image MIME,
  EXTRA_STREAM content URI, read grant, setPackage target per bank, no chooser),
  `SelectedBankPersistenceTest` (no bank → null; select K PLUS → restore after
  restart; change bank → new one restored; clear; unknown/mismatched decode),
  `UninstalledBankTest` (isInstalled=false, canUpload=false,
  requiresBankSelection=true, old selection kept; pure `preflight`),
  `ShareFallbackTest` (every non-ready outcome is an error notice; the action is
  never a chooser).

## Build result

- LOCAL BUILD: **not performed / not possible** — this environment has no
  JDK/SDK (`java` is not installed). Nothing below claims a local pass.
- GITHUB ACTIONS: the workflow runs on every push; the result of the push that
  carries this session's changes is the authority. Read the run before reporting
  a pass.

## CI result

- Previous observed run (before this session): **SUCCESS** on commit `4713f68`
  (`35443754450`, 2m3s) — `testDebugUnitTest`, `lintDebug`, `assembleDebug`,
  APK verification and artifact upload `qr-payment-queue-v0.4.2-debug`, all PASS.
- This session's commit: **result not yet observed here.** Do not report CI as
  green until the corresponding run is read.

## APK artifact

- Workflow artifact name: `qr-payment-queue-v0.4.2-debug`
- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Last observed size (run `35443754450`): `9.2M`.

## Real-device verification

**NOT DONE.** No device or emulator in this environment. The bank selection,
upload gate and direct share are unit-tested as pure logic; the real-device
questions are: does the dialog probe correctly, does the persisted selection
survive process death, and does the share actually open the selected bank without
the Android Sharesheet? Plan in `docs/REAL_DEVICE_TEST.md` (Xiaomi 15T Pro,
Android 16). Until those runs exist the app is **REAL DEVICE VERIFICATION: NOT
VERIFIED**.

## Known limitations

1. **Bank confirmation stays manual.** Without an official supported bank
   integration the app cannot know whether a transaction completed.
2. **Package names come from Google Play listings.** If a bank changes its
   package name the app reports it as "not found" and the user selects another.
3. **Not all banking apps support image sharing.** `INSTALLED_NOT_ADVERTISED`
   means the bank is installed but its share activity was not found; the hand-off
   is still attempted and the result is reported honestly.
4. **Changing banks does not affect queued items.** Items in WAITING_USER or
   UNKNOWN still reference the bank selected when they were shared; the next
   share uses the current bank.
5. **K PLUS cannot be verified from here** — needs a real device.
6. No instrumented UI tests; no emulator in CI.
7. The `.env.example` / `.env.keys` web leftovers remain tracked but unused.

## Things future agents must NOT repeat

- Do not build a chooser fallback: `Intent.createChooser` must not exist in the
  payment path (asserted by tests).
- Do not treat "`queryIntentActivities` found nothing" as "the bank is not
  installed" — installation and share-advertisement are separate states.
- Do not clear the saved bank just because its package is missing; keep it to
  display it, and gate use on `requiresBankSelection`.
- Do not re-introduce a QR decoder, Accessibility automation, PIN/OTP handling,
  or any automatic payment confirmation/retry.
- Do not claim a build, APK or CI pass that was not observed.
