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

0.4.2 (versionCode 6) — V0.4.2 **Bank Selection + Persistent Selected Bank +
Upload Gate**.

## What V0.4.2 changed

The user must select a banking app before importing any QR images. The selection
is persisted via SharedPreferences so it survives every form of process death.
The import button is disabled until a bank is selected and installed.

```
V0.4.1:  import → queue → share to K PLUS (hardcoded) → confirm
V0.4.2:  SELECT BANK → persist → queue → import → share to selected bank → confirm
```

1. **Bank selection on the home screen.** A `BankSelectionCard` at the top of
   every screen shows the current bank's status (ready / installed-not-advertised
   / not-found / not-selected) with a button to choose or change. The card is
   always visible so the user never loses sight of where QR images will be sent.
2. **Upload gate.** `QueueUiState.canImport` is the single source of truth:
   `selectedBank != null && bankStatus?.canHandOff == true`. The import button is
   disabled when this is false, and no toast or dialog is needed — the gate is
   structural.
3. **Bank selection dialog.** Probes every known bank on the device at dialog
   open time (pure `BankTarget.query`). Shows radio buttons for each bank with
   real-time status: ready (green), installed-not-advertised (red note), or
   not-installed (greyed out).
4. **Persistent selection.** `QueueRepository` stores `selectedBankPackage` and
   `selectedBankId` in SharedPreferences. On init the ViewModel loads the bank
   and re-probes it on the current device. If the bank was uninstalled since the
   last session, the upload gate is disabled immediately and the user is told.
5. **Generalized share flow.** `QrShare.bankShareIntent` takes `targetPackage`
   as a parameter (no longer hardcoded to K PLUS). `ShareIntentSpec.targetPackage`
   is part of the unit-tested contract. `BankRegistry` holds the verified package
   names for K PLUS, SCB EASY, Krungthai NEXT, and Bualuang mBanking.
6. **`KPlusTarget` → `BankTarget`.** The old K PLUS-only target resolution is
   replaced by a generalized `BankTarget.classify` / `BankTarget.query` that
   works with any `BankInfo`. The manifest `<queries>` block lists all four
   known banking packages.
7. **Queue and state machine unchanged.** `QUEUED → SHARING → WAITING_USER →
   COMPLETED`, `FAILED`, `UNKNOWN` — the V0.4.1 state machine, per-item
   transitions, single-hand-off rule, and confirmation flow are untouched.
   Changing the selected bank does not affect queued items, their status, or
   their share targets. The next share uses whatever bank is selected at that
   moment.

## Version history

| Version | Scope |
| --- | --- |
| 0.1.0 | Native Android foundation, APK build, CI |
| 0.2.0 | Multi-image photo-picker import, app-private image storage |
| 0.3.0 | QR decoding (ZXing) + EMVCo PromptPay parsing + queue audit |
| 0.4.0 | Image queue + K PLUS hand-off + manual user confirmation; QR decoding removed |
| 0.4.1 | Home-screen queue, self-closing import, one-by-one direct K PLUS hand-off |
| **0.4.2** | **Bank selection, persistent selected bank, upload gate, generalized share** |

## Files added

- `app/src/main/java/com/enjirad/qrqueue/domain/BankInfo.kt` — `BankInfo`,
  `BankAvailability`, `BankTargetStatus`, and `BankRegistry` (the verified
  candidate list of Thai banking apps).
- `app/src/main/java/com/enjirad/qrqueue/data/BankTarget.kt` — generalized
  `classify` (pure, unit-tested) and `query` (Android layer) for any banking
  package.
- `app/src/test/java/com/enjirad/qrqueue/data/BankTargetTest.kt` — bank-target
  classification, registry lookups.
- `app/src/test/java/com/enjirad/qrqueue/ui/UploadGateTest.kt` — the upload gate
  and bank-selection derived state.

## Files removed

- `app/src/main/java/com/enjirad/qrqueue/data/KPlusTarget.kt` — replaced by
  `BankTarget.kt`.
- `app/src/test/java/com/enjirad/qrqueue/data/KPlusTargetTest.kt` — replaced by
  `BankTargetTest.kt`.

## Files changed

- `data/QrShare.kt` — `shareSpec` takes `targetPackage`; `kPlusShareIntent`
  replaced by `bankShareIntent(context, file, mimeType, targetPackage)`.
- `data/QueueRepository.kt` — added `saveSelectedBank`, `loadSelectedBank`,
  `clearSelectedBank` using SharedPreferences.
- `ui/QueueViewModel.kt` — bank state (`selectedBank`, `bankStatus`),
  `canImport` upload gate, `isBankReady`, bank-selection handlers, bank-aware
  `requestShare`.
- `ui/QueueScreen.kt` — `BankSelectionCard`, `BankSelectionDialog`, disabled
  import button, bank-aware share labels.
- `AndroidManifest.xml` — expanded `<queries>` to cover all four known banking
  packages.
- `res/values/strings.xml` — V0.4.2 copy; bank-selection strings, upload-gate
  hint, generalized bank labels (no longer K PLUS–only).
- `app/build.gradle.kts` — `versionName = "0.4.2"`, `versionCode = 6`.
- `.github/workflows/android.yml` — artifact `qr-payment-queue-v0.4.2-debug`.
- `app/src/test/java/com/enjirad/qrqueue/data/ShareIntentSpecTest.kt` — updated
  for the `targetPackage` parameter.

## Dependency changes

**None.** SharedPreferences is part of the Android platform. No new library
was added or removed.

## Tests

76 JUnit 4 test methods across 8 classes, all pure JVM (no device, no emulator,
no `@Ignore`, no new dependency):

- `PaymentStatusTest` (10) — status classification, hand-off ownership.
- `PaymentQueueTest` (20) — per-item transitions, single-hand-off rule, failure
  and unknown paths, retry, process death, counts.
- `QueueImportTest` (12) — URI de-duplication, storage helpers, 1/3/10 imports.
- `ImportCompletionTest` (11) — progress arithmetic, self-closing import screen,
  import summary.
- `ShareIntentSpecTest` (8) — the hand-off contract with `targetPackage` for
  multiple banks; no chooser.
- `BankTargetTest` (9) — bank classification, registry lookups, known-bank
  validation.
- `UploadGateTest` (6) — the upload gate: disabled without a bank, enabled when
  installed, disabled when uninstalled; dialog state independent of import;
  progress and queue don't affect the gate.

## Build result

- LOCAL BUILD: **not performed / not possible** — no JDK/SDK in this sandbox.
- GITHUB ACTIONS: **SUCCESS** on commit `4713f68` (run `35443754450`, 2m3s).

## CI result

**GREEN.** Every step of `Android CI` succeeded on commit
`4713f68ce5ff40d7baed147835fedd48edb83db1`:

- Run URL: https://github.com/EnJirad/qr-pay-queue/actions/runs/35443754450
- `testDebugUnitTest`: **PASS** (76 test methods)
- `lintDebug`: **PASS** (`abortOnError = true`)
- `assembleDebug`: **PASS**
- APK existence / non-empty / inspect: **PASS**
- Artifact upload `qr-payment-queue-v0.4.2-debug`: **PASS**
- Build time: 2m3s

### V0.4.2 runs that failed first

1. `35442759243` — **failed** `:app:compileDebugKotlin`: `BankTarget.kt` had
   `image/*` inside a KDoc block comment, which Kotlin nests. Same class of bug
   as V0.4.0. Fixed by rephrasing.
2. `35443007046` — **failed** `:app:compileDebugUnitTestKotlin`: test referenced
   `isAdvertisedShareTarget` which was removed from `BankTargetStatus` in favor
   of the `advertised` boolean. Fixed in the test.
3. `35443319600` — **failed** 1 unit test: `isBankReady` used `canHandOff`
   (which is true for any installed bank), but the test expected it to be false
   for an installed-but-not-advertised bank. Fixed `isBankReady` to require
   `advertised == true`.

## APK artifact

- Workflow artifact name: `qr-payment-queue-v0.4.2-debug`
- Path inside the workflow: `app/build/outputs/apk/debug/app-debug.apk`
- Last observed V0.4.2 build (run `35443754450`): `9.2M`, `classes.dex`
  18,137,284 bytes, `AndroidManifest.xml` 6,896 bytes (larger manifest due to
  additional `<queries>` entries for four banking packages).

## Real-device verification

**NOT YET DONE.** No device or emulator in this environment. The bank selection
and upload gate are unit-tested as pure logic; the real-device questions are:
does the dialog probe correctly, does the persisted selection survive process
death, and does the share actually open the selected bank? Plan in
`docs/REAL_DEVICE_TEST.md`.

## Known limitations

1. **Bank confirmation stays manual.** Without an official supported bank
   integration the app cannot know whether a transaction completed.
2. **Bank package names come from Google Play listings.** If a bank changes its
   package name, the app reports it as "not found" and the user can select
   another bank. The registry can be extended in a future version.
3. **Not all banking apps support image sharing.** `INSTALLED_NOT_ADVERTISED`
   means the bank is installed but its share-activity was not found; the hand-off
   is still attempted and the result is reported honestly.
4. **Changing banks does not affect queued items.** Items in WAITING_USER or
   UNKNOWN status still reference the bank that was selected when they were
   shared. The next share uses the current bank.
5. **K PLUS cannot be verified from here** — needs a real device.
6. No instrumented UI tests; no emulator in CI.
7. The `.env.example` / `.env.keys` web leftovers remain tracked but unused.
