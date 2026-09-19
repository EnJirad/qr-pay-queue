# Real-device test report — V0.4.2 bank selection and upload gate

## Status

> **REAL DEVICE VERIFICATION: NOT YET DONE**
>
> No Android device and no emulator were available in the environment where this
> change was written. CI only compiles, unit-tests, lints and packages the APK.
> Therefore **no bank selection, share flow or K PLUS behaviour is claimed anywhere
> in this repository**.

## Verified so far (no device required)

| Claim | Evidence |
| --- | --- |
| APK builds | GitHub Actions run `35443754450` (commit `4713f68`): `assembleDebug` PASS |
| Unit tests pass | same run: `testDebugUnitTest` PASS, 76 test methods |
| Lint passes | same run: `lintDebug` PASS (`abortOnError = true`) |
| Upload gate works without device | `UploadGateTest` (6 methods): disabled when no bank, enabled when installed, disabled when uninstalled |
| Bank classification is correct | `BankTargetTest` (9 methods): READY / INSTALLED_NOT_ADVERTISED / NOT_INSTALLED, registry lookups |
| Share contract has targetPackage | `ShareIntentSpecTest` (8 methods): action SEND, content URI, image MIME, read grant, target package for each bank, no chooser |
| Queue state machine rules | `PaymentQueueTest`, `PaymentStatusTest` |
| Import, persistence, completion | `QueueImportTest`, `ImportCompletionTest` |
| Bank registry has real package names | `BankTargetTest.eachKnownBankHasAValidPackageName` |
| Direct bank intent contract | `DirectBankIntentTest` (7 methods): ACTION_SEND, image MIME, EXTRA_STREAM content URI, read grant, setPackage target per bank, no chooser |
| Bank selection survives restart | `SelectedBankPersistenceTest` (7 methods) |
| Uninstalled bank disables upload | `UninstalledBankTest` (5 methods): isInstalled=false, canUpload=false, requiresBankSelection=true |
| No Sharesheet fallback | `ShareFallbackTest` (6 methods): every non-ready outcome is an error notice; the action is never a chooser |

## Test environment (fill in when a device is available)

| Field | Value |
| --- | --- |
| Device model | Xiaomi 15T Pro (target) |
| Android version | Android 16 (target) |
| K PLUS version | _to be filled in_ |
| App version | 0.4.2 (versionCode 6) |
| Build under test | commit hash of the tested build |
| APK source | GitHub Actions artifact `qr-payment-queue-v0.4.2-debug` |
| Test date | _to be filled in_ |
| Tester | _to be filled in_ |

Install:
```bash
adb install -r app-debug.apk
```

## Test cases

### TEST 1 — first install: no bank, upload disabled

1. Install fresh (clear app data if needed).
2. Open the app.

Expected:
- The bank section shows "ยังไม่ได้เลือกธนาคาร" with "กรุณาเลือกธนาคารก่อนเพิ่มรูป QR".
- The [เลือกธนาคาร] button is enabled.
- The [+ เพิ่มรูป QR] button is **disabled**.
- The hint below the import button says "กรุณาเลือกธนาคารก่อน จึงจะเพิ่มรูป QR ได้".

Status: `NOT RUN`

### TEST 2 — bank selection dialog shows real device state

1. Tap [เลือกธนาคาร].

Expected: the dialog lists K PLUS, SCB EASY, Krungthai NEXT, and Bualuang
mBanking. For each:
- Installed + advertises image sharing → "พร้อมใช้งาน" (enabled radio).
- Installed but no image share activity → "ไม่รองรับการส่งรูป" (enabled radio, red note).
- Not installed → "ไม่ได้ติดตั้ง" (greyed out, disabled radio).

Status: `NOT RUN`

### TEST 3 — select a bank, upload becomes enabled

1. Tap [เลือกธนาคาร].
2. Select K PLUS (if installed).
3. Tap [ยืนยัน].

Expected:
- The bank card shows "K PLUS" with a green checkmark and "พร้อมใช้งาน".
- The [+ เพิ่มรูป QR] button is now **enabled**.
- The import hint reverts to the normal text.

Status: `NOT RUN`

### TEST 4 — persist across app restart

1. Select K PLUS (Test 3).
2. Force-stop the app.
3. Re-open the app.

Expected: K PLUS is still shown as the selected bank, upload still enabled.

Status: `NOT RUN`

### TEST 5 — change bank persists the new one

1. Select K PLUS.
2. Tap [เปลี่ยนธนาคาร].
3. Select SCB EASY (if installed).
4. Confirm.
5. Force-stop and reopen.

Expected: SCB EASY is shown, not K PLUS.

Status: `NOT RUN`

### TEST 6 — queue survives bank change

1. Select K PLUS, import 3 images.
2. Change bank to SCB EASY.
3. Check the queue.

Expected: all 3 images are still there with their original statuses. Changing
the bank does not delete, reset or re-queue any item.

Status: `NOT RUN`

### TEST 7 — bank uninstalled while app is closed

1. Select K PLUS.
2. Force-stop the app.
3. Uninstall K PLUS.
4. Re-open QR Payment Queue.

Expected:
- The bank card shows "K PLUS" with "ไม่พบแอปธนาคารในเครื่อง" (red note).
- A snackbar says "ธนาคารที่เลือกไว้ไม่พบในเครื่องอีกแล้ว กรุณาเลือกธนาคารใหม่".
- The [+ เพิ่มรูป QR] button is **disabled**.
- The [เปลี่ยนธนาคาร] button is enabled.

Status: `NOT RUN`

### TEST 8 — share opens the selected bank directly

1. Select K PLUS, import images.
2. Tap [แชร์ไปธนาคาร] on a QUEUED item.

Expected:
- K PLUS opens directly (no Android chooser).
- If K PLUS is not installed → the share fails and the item becomes FAILED
  with a clear reason.

Status: `NOT RUN`

### TEST 9 — bank installed but not advertised

1. If a bank is installed but does not appear as a share target, select it.
2. Try to share a QR image.

Expected: the share is still attempted (the app doesn't block it). If it fails,
the item is FAILED with a clear reason. The bank card note explains the
situation.

Status: `NOT RUN`

### TEST 10 — process death during payment

1. Share an image to the bank.
2. Kill the app.
3. Reopen.

Expected: the item is UNKNOWN, the queue is paused, the selected bank is still
remembered. The user resolves the UNKNOWN item manually.

Status: `NOT RUN`

### TEST 11 — the entire V0.4.1 flow still works

1. Select a bank.
2. Import 3 images → progress 1/3..3/3 → auto-return to home.
3. Pick image 1 → share to bank → bank opens.
4. Return → confirm "ทำรายการเสร็จแล้ว" → COMPLETED.
5. Pick image 2 → share → confirm → COMPLETED.
6. Pick image 3 → share → confirm → COMPLETED.
7. Finished banner: "ทำรายการครบแล้ว 3/3".

Status: `NOT RUN`

## Evidence to attach

For each executed test, attach a screenshot named `<test-number>-<step>.png` under
`docs/evidence/` and reference it from the test above. Do not add screenshots you
did not take.

## What this app can never verify

- Whether a bank transaction actually completed.
- Whether the user verified the recipient and amount inside the banking app.
- Whether the banking app reads the QR from the shared image.
