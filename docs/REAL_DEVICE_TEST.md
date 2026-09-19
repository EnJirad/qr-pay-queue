# Real-device test report — V0.6.0 tabs, one-handed mode, problem reasons, daily reset

## Status

> **REAL DEVICE VERIFICATION: NOT VERIFIED**
>
> No Android device and no emulator were available in the environment where this
> change was written. CI only compiles, unit-tests, lints and packages the APK, so
> **no screen of this app has ever been rendered outside a build**, and no bank
> hand-off has been observed. Nothing in this repository claims otherwise.
>
> The last **green** CI run was the V0.5.0 build (`ec84fdd`, run `35451443398`).
> The V0.6 commits are **red**: run `35455677419` (`ba3005f`) failed three unit
> tests. The fixes for them are in the working tree and have not been built yet,
> so there is no APK for V0.6 to test at the time of writing. Do not test against
> a stale 0.5.0 artifact and record the result as a V0.6 pass.

## Required checklist (Xiaomi 15T Pro / Android 16) — 20 steps

None of these has been run.

| # | Step | Expected | Status |
| --- | --- | --- | --- |
| 1 | Open the app | Bank card shows "ยังไม่ได้เลือกธนาคาร"; **+ เพิ่ม QR** disabled; tab หน้าแรก selected; no problem badge | NOT RUN |
| 2 | Select a bank (e.g. K PLUS) | Card shows the bank + publisher; **+ เพิ่ม QR** enabled | NOT RUN |
| 3 | Add multiple QR images | Photo picker; progress 1/5 … 5/5; auto-returns to หน้าแรก; items numbered QR #01… | NOT RUN |
| 4 | Verify queue ordering | Items keep the selection order and their numbers; the next action is the first item | NOT RUN |
| 5 | Verify หน้าแรก | Shows the next action (pay / check / replace QR), the remaining count and **+ เพิ่ม QR** | NOT RUN |
| 6 | Verify the ปัญหา badge | No badge while nothing needs attention; a count appears as soon as one item does | NOT RUN |
| 7 | Pay one item | Tap **ชำระเงิน** → item shows SHARING briefly, then awaits confirmation | NOT RUN |
| 8 | Verify the direct bank launch | The selected bank opens **directly** — **no Android Sharesheet** | NOT RUN |
| 9 | Return without confirming | Item is **not** completed; หน้าแรก asks you to check and confirm | NOT RUN |
| 10 | Verify it is not auto-completed | Item still in หน้าแรก/ปัญหา, not in ชำระแล้ว | NOT RUN |
| 11 | Mark it completed manually | **ทำรายการเสร็จแล้ว** → item appears under ชำระแล้ว with its time | NOT RUN |
| 12 | Verify ชำระแล้ว | Only user-confirmed items; active items do not appear | NOT RUN |
| 13 | Trigger the unusable-QR workflow | **QR ใช้งานไม่ได้** → item moves to ปัญหา as ต้องเปลี่ยน QR; item is **not** deleted; badge +1 | NOT RUN |
| 14 | Replace the QR | **เปลี่ยน QR** → picker opens for one image → same Payment Item, now QR v2 | NOT RUN |
| 15 | Verify the same Payment Item remains | Same number (`QR #08`) and position; no new item was created | NOT RUN |
| 16 | Verify the old QR is history | Card shows "QR เวอร์ชันที่ 2"; v1 is no longer current; ใช้ไม่ได้ | NOT RUN |
| 17 | Verify the new QR is READY | Item is back in หน้าแรก as รอชำระ and can be paid again | NOT RUN |
| 18 | Restart the app | Queue, statuses, QR versions, completed items and the selected bank all survive | NOT RUN |
| 19 | Verify UNKNOWN recovery | Kill the app mid hand-off → item returns as ยังไม่ทราบผล, queue pauses, **nothing is re-sent** | NOT RUN |
| 20 | Rapid payment taps | Hammering **ชำระเงิน** starts exactly one attempt and opens the bank once | NOT RUN |

### V0.6 additions (one-handed mode, problem reasons, clear item, daily reset)

| # | Step | Expected | Status |
| --- | --- | --- | --- |
| 21 | Open **ตั้งค่า** | Hand mode shows ถนัดขวา selected; auto-reset shows its current state; bank section shows the selected bank; manual reset is offered | NOT RUN |
| 22 | Switch to **ถนัดซ้าย** | The primary actions of the current item (ชำระเงิน, ดูรูป / มีปัญหา and the confirmation buttons) move to the **left** side of the card; nothing is stretched across the screen | NOT RUN |
| 23 | Switch back to **ถนัดขวา** | The same actions sit on the **right** side; no second layout appears | NOT RUN |
| 24 | Restart after choosing ถนัดซ้าย | The setting is still ถนัดซ้าย and the actions are still on the left | NOT RUN |
| 25 | Tap **มีปัญหา** on the current QR | A short reason list appears (QR ใช้งานไม่ได้ / ธนาคารแจ้งว่า QR ไม่ถูกต้อง / QR หมดอายุ / จ่ายไม่ได้ / รูปภาพมีปัญหา / อื่น ๆ) | NOT RUN |
| 26 | Pick a reason | The item leaves หน้าแรก, appears in ปัญหา with that reason, the badge increases, and the item keeps its number | NOT RUN |
| 27 | Restart after reporting a problem | The same item is still in ปัญหา with the same reason, and its reported QR is **not** listed as the current one | NOT RUN |
| 28 | **ล้างรายการ** on a problem item | A confirmation dialog appears; confirming deletes only that item and its images; the other items keep their numbers and statuses | NOT RUN |
| 29 | Turn on auto-reset, then move the device date forward one day and reopen the app | The previous day's queue and images are gone, a notice says so, and both the selected bank and ถนัดมือ are unchanged | NOT RUN |
| 30 | Manual **ล้างข้อมูลของวันนี้** | After confirming, the queue is empty; settings and the bank selection survive; gallery images are untouched | NOT RUN |

## Verified so far (no device required)

| Claim | Evidence |
| --- | --- |
| APK builds | **V0.5.0 only.** GitHub Actions run `35451443398` (commit `ec84fdd`): `assembleDebug` PASS, APK 9.3M. The V0.6 tree has **not** been built: run `35455677419` failed at `testDebugUnitTest`, so no APK was produced |
| Unit tests pass | **V0.5.0 only** (163 test methods, same run). The V0.6 tree is at 190 test methods and the three failures of run `35455677419` have been fixed in the working tree, **unverified by CI so far** |
| Lint passes | **V0.5.0 only** (same run, `abortOnError = true`). The V0.6 lint step has never run |
| State machine (all valid/invalid transitions) | `PaymentQueueTest` (25 methods) |
| Only user confirmation completes an item | `PaymentConfirmationTest` (10 methods) |
| Double-payment protection | `DoublePaymentTest` (8 methods): one attempt per tap burst, one hand-off at a time |
| Replace QR keeps the Payment Item | `QrReplacementTest` (11 methods): same id/position, v2 current, v1 history, READY, failed replacement leaves the original intact |
| Badge / tab contents | `NavigationBadgeTest` (14 methods): counts, no "0" badge, problem and completed tab contents, tab order |
| Import de-duplication and summary | `QueueImportTest`, `ImportCompletionTest` (duplicates counted separately from failures) |
| Persistence incl. legacy queues | `QueueRepository` schema 3 with migration; selected bank: `SelectedBankPersistenceTest` (7 methods) |
| Direct bank intent contract | `DirectBankIntentTest` (8), `ShareIntentSpecTest` (8): ACTION_SEND, image MIME, EXTRA_STREAM content URI, read grant, `setPackage` per bank, **no chooser** |
| Upload gate / uninstalled bank | `UploadGateTest` (7), `UninstalledBankTest` (5) |
| No Sharesheet fallback | `ShareFallbackTest` (6): every non-ready outcome is an error notice |
| Bank registry hygiene | `BankTargetTest` (14): unique ids/packages, valid format, verified ⇒ Play URL + ISO date, 13 expected banks, K PLUS is `com.kasikorn.retail.mbanking.wap` |
| UI layer is only compile-verified | The three tabs, badge, replace-QR picker and confirm panel are Compose code that CI compiles; **no screen has been observed** |

## Test environment (fill in when a device is available)

| Field | Value |
| --- | --- |
| Device model | Xiaomi 15T Pro (target) |
| Android version | Android 16 (target) |
| Bank app versions | _to be filled in_ |
| App version | 0.6.0 (versionCode 8) |
| Build under test | commit hash of the tested build |
| APK source | GitHub Actions artifact `qr-payment-queue-v0.6.0-debug` |
| Test date | _to be filled in_ |
| Tester | _to be filled in_ |

Install:
```bash
adb install -r app-debug.apk
```

## Test cases

### TEST 1 — first launch: no bank, upload disabled

Expected: bank card shows "ยังไม่ได้เลือกธนาคาร" + "กรุณาเลือกธนาคารก่อนเพิ่มรูป QR";
**[เลือกธนาคาร]** enabled; **[+ เพิ่ม QR]** disabled with the disabled hint.
Status: `NOT RUN`

### TEST 2 — bank dialog shows real device state

Expected: each known bank shows ติดตั้งแล้ว · ส่งรูป QR ได้ / ติดตั้งแล้ว แต่ไม่พบกิจกรรมที่รับรูป QR /
ไม่ได้ติดตั้ง / ไม่สามารถตรวจสอบได้. A not-installed bank is greyed out and not selectable.
Status: `NOT RUN`

### TEST 3 — select a bank, upload becomes enabled

Expected: card shows the bank + publisher; **[+ เพิ่ม QR]** enabled; the normal hint returns.
Status: `NOT RUN`

### TEST 4 — bank persists across restart

Force-stop and reopen → the same bank is still selected and import is still enabled.
Status: `NOT RUN`

### TEST 5 — change bank persists the new one

**เปลี่ยนธนาคาร** → pick another → force-stop and reopen → the new bank is shown.
Status: `NOT RUN`

### TEST 6 — changing the bank never touches the queue

Select bank A, import 3 items, one in `AWAITING_USER_CONFIRMATION`; change to bank B.
Expected: all 3 items keep their statuses and QR versions; nothing is deleted or reset;
the next share uses bank B.
Status: `NOT RUN`

### TEST 7 — import duplicates and partial failures

Select the same screenshot twice plus a second one → summary "✓ เพิ่ม 2 รายการ /
↷ ข้ามรายการซ้ำ 1 รายการ". Then (if reproducible) make one image fail → the other
items stay in the queue and the failure is reported.
Status: `NOT RUN`

### TEST 8 — direct bank launch, no chooser

Tap **ชำระเงิน** on a READY item.
Expected: the selected bank opens directly; **no Android Sharesheet**;
if the bank cannot be opened, the item becomes FAILED with the clear
"ไม่สามารถเปิดธนาคารที่เลือกได้…" message and **no other app opens**.
Status: `NOT RUN`

### TEST 9 — returning is not success

Share an item, return to the app without confirming.
Expected: the item is still open, หน้าแรก shows the confirm panel, and the item is
**not** in ชำระแล้ว.
Status: `NOT RUN`

### TEST 10 — confirm and move to ชำระแล้ว

Tap **ทำรายการเสร็จแล้ว** → item moves to ชำระแล้ว with its completion time; the next
item is offered on หน้าแรก but **nothing is opened automatically**.
Status: `NOT RUN`

### TEST 11 — unusable QR → เปลี่ยน QR → same Payment Item

1. On a shared item tap **QR ใช้งานไม่ได้**.
2. Check ปัญหา: the item shows ต้องเปลี่ยน QR and the badge increased.
3. Tap **เปลี่ยน QR** and pick one image.

Expected: the same item number (`QR #08`) is still there, now on QR v2 and back in
รอชำระ; v1 is kept as history; no new item was created.
Status: `NOT RUN`

### TEST 12 — process death during payment

Share an item, kill the app mid hand-off, reopen.
Expected: the item is ยังไม่ทราบผล, the queue pauses, the selected bank is still
remembered, and **the QR is not sent again automatically**.
Status: `NOT RUN`

### TEST 13 — uninstalled bank while the app is closed

Select K PLUS, force-stop, uninstall K PLUS, reopen.
Expected: the card shows K PLUS with "ไม่พบแอปธนาคารในเครื่อง", a snackbar asks to
select a new bank, and **[+ เพิ่ม QR]** is disabled until a usable bank is chosen.
Status: `NOT RUN`

### TEST 14 — installed bank that does not advertise image sharing

Expected: reported as "ติดตั้งแล้ว แต่ไม่พบกิจกรรมที่รับรูป QR" (**not** "not installed");
the hand-off is still attempted and its result is reported honestly.
Status: `NOT RUN`

### TEST 15 — rapid payment taps

Hammer **ชำระเงิน** on one item: exactly one attempt, one bank launch, and only one
`STARTED` record for that item.
Status: `NOT RUN`

### TEST 16 — the whole batch still works end to end

Import 3 images → pay each one → confirm each one → all 3 appear under ชำระแล้ว →
หน้าแรก shows "วันนี้ชำระครบแล้ว 3/3".
Status: `NOT RUN`

### TEST 17 — hand mode moves the actions (V0.6)

ตั้งค่า → ถนัดซ้าย → หน้าแรก.
Expected: the current item's primary actions are anchored to the left; switching back
to ถนัดขวา mirrors them to the right. Same layout, no clipping on a narrow screen,
and the buttons keep their full touch height.
Status: `NOT RUN`

### TEST 18 — problem reason (V0.6)

On the current QR tap **มีปัญหา** and pick "QR หมดอายุ".
Expected: the item moves to ปัญหา with that reason visible, keeps its number and
position, and its previous QR is no longer the current one. After a restart the
reason is still shown and the reported QR is still not current. Selecting
**เปลี่ยน QR** gives the same item a v2 and returns it to หน้าแรก.
Status: `NOT RUN`

### TEST 19 — clear one item (V0.6)

In ปัญหา, **ล้างรายการ** → confirm.
Expected: only that item and its images are deleted; the remaining items keep their
numbers, statuses and QR versions; no gallery image is touched.
Status: `NOT RUN`

### TEST 20 — daily reset (V0.6)

Turn the automatic reset on, leave the queue with items, then move the device date
forward one day and open the app.
Expected: the queue and its copied images are deleted, a notice says the daily data
was cleared, and the settings (hand mode, auto-reset, bank) are untouched. The
manual **ล้างข้อมูลของวันนี้** does the same on demand, after a confirmation.
Status: `NOT RUN`

## Evidence to attach

For each executed test, attach a screenshot named `<test-number>-<step>.png` under
`docs/evidence/` and reference it from the test above. Do not add screenshots you
did not take.

## What this app can never verify

- Whether a bank transaction actually completed.
- Whether the user verified the recipient and amount inside the banking app.
- Whether the banking app reads the QR from the shared image.
