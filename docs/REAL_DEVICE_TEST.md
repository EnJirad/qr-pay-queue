# Real-device test report — V0.9.1 hand-off rail, active QR area, Edit mode

## Status

> **REAL DEVICE VERIFICATION: NOT VERIFIED**
>
> No Android device and no emulator were available in the environment where this
> change was written. CI only compiles, unit-tests, lints and packages the APK, so
> **no screen of this app has ever been rendered outside a build**, and no bank
> hand-off has been observed. Nothing in this repository claims otherwise.
>
> The last **green** CI run is the V0.9.1 build (`57426ea`, run `35484704958`),
> whose artifact is `qr-payment-queue-v0.9.1-debug` (APK 9.4M) — that is the one
> to install for this checklist. It runs 236 unit tests, `lintDebug` and
> `assembleDebug`, and nothing more.
> Do not test against a stale artifact and record the result as a V0.9 pass: the
> V0.9 hand-off behaviour and the Edit mode only exist in `f0d6daa` or later, and
> placing the actions of both action states only exists in `57426ea` or later.

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
| APK builds | **V0.9.0**: run `35483100157` (commit `f0d6daa`) `assembleDebug` PASS, APK 9.4M, artifact `qr-payment-queue-v0.9.0-debug` |
| Unit tests pass | **V0.9.0**: same run, 230 test methods across 20 classes, 0 failures |
| Lint passes | **V0.9.0**: same run, `lintDebug` PASS (`abortOnError = true`) |
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
| App version | 0.9.1 (versionCode 12) |
| Build under test | commit hash of the tested build |
| APK source | GitHub Actions artifact `qr-payment-queue-v0.9.1-debug` |
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

Tap the QR/scan action on a READY item.
Expected: the selected bank opens directly; **no Android Sharesheet**;
if the bank cannot be opened, the item **keeps its four actions** with the clear
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

## V0.9 checklist — hand-off rail, active QR area, Edit mode

All of these are **NOT RUN**: the behaviour they check was written without a device.

### A. The four actions never depend on the bank launch

| # | Step | Expected | Status |
| --- | --- | --- | --- |
| 31 | Tap the QR/scan action on a READY item | The four actions (✓ ⚠ ? ↻) are on screen **immediately** — before the bank app appears, and whether or not it appears | NOT RUN |
| 32 | Same tap, with the selected bank **uninstalled** (or the bank set to one that is not installed) | The four actions **still** appear, with the "ไม่พบแอปธนาคารในเครื่อง" notice; nothing is marked FAILED; the item is not removed from หน้าแรก | NOT RUN |
| 33 | Same tap with the stored image deleted (advanced; e.g. clear app data of one image via a file manager) | The four actions stay, with the "ไม่พบไฟล์รูป…" notice; the item is not failed | NOT RUN |
| 34 | After a failed launch, read under the QR | The reason is shown as a caption **and** as a message; the queue did not move on | NOT RUN |
| 35 | Tap ↻ with the bank still unavailable | The same Payment Item and the same QR are retried, a new attempt is recorded, the four actions stay, nothing becomes COMPLETED/FAILED/PROBLEM | NOT RUN |
| 36 | Tap ↻ repeatedly on a payable item, each time looking at ชำระแล้ว / ปัญหา | No item is ever created, completed or moved by a retry; the item number and QR version never change | NOT RUN |
| 37 | Kill the app right after the tap and reopen | The item comes back as ยังไม่ทราบผล and **nothing is re-sent automatically** | NOT RUN |
| 38 | Tap the QR action with the bank app open already (edge case) | Only one attempt starts; no second bank window | NOT RUN |
| 39 | Never tap anything after a successful bank launch | The item **never** completes by itself, no matter how long you wait or how often you return to the app | NOT RUN |

### B. One active QR area, the rest listed below

| # | Step | Expected | Status |
| --- | --- | --- | --- |
| 40 | Import 3 images | The top area shows **one** QR (the next action); the other two are listed below under คิวที่เหลือ with their own status | NOT RUN |
| 41 | Import one more image while an item is awaiting your answer | The awaiting item keeps the top area (its four actions stay visible); the new QR joins the list below; nothing is duplicated or replaced | NOT RUN |
| 42 | Complete the top item | It leaves the top area, the next open item takes the top spot, and the completed one is only in ชำระแล้ว | NOT RUN |

### C. Edit mode

| # | Step | Expected | Status |
| --- | --- | --- | --- |
| 43 | Tap **✎** next to the lock | Edit mode opens with the panel (element list, รีเซ็ตผัง, เสร็จสิ้น); every movable element gets a thin outline | NOT RUN |
| 44 | In Edit mode, drag each element (QR frame, scan, ✓, ⚠, ?, ↻, add-QR, guidance, import hint, progress) | It follows the finger smoothly, stays inside the screen, and **no action fires** while dragging or after a drag | NOT RUN |
| 45 | Tap (without dragging) an action inside Edit mode | The action must **not** run (a hand-off must not start from Edit mode) | NOT RUN |
| 46 | In Edit mode, scroll the screen | Scrolling is disabled, so a drag is never read as a scroll and a scroll never moves an element | NOT RUN |
| 47 | Hide the guidance text, the import hint and the progress caption with the eye control | They disappear; the panel can bring each one back; the actions cannot be hidden at all (no control for them) | NOT RUN |
| 48 | Nudge the QR image inside its frame | The image moves inside the clipped frame and the code stays visible (the offset is bounded, the aspect ratio is kept) | NOT RUN |
| 49 | Force-stop and reopen the app | Every position, every visibility choice and the QR-image offset are exactly as you left them | NOT RUN |
| 50 | Rotate / recreate the screen (or leave and return from the bank) | The layout is unchanged | NOT RUN |
| 51 | **รีเซ็ตผัง** → confirm | Every element returns to the app's own place, everything is shown again, and the QR image is centred | NOT RUN |
| 52 | Tap **🔒** while in Edit mode | Edit mode closes and nothing can be dragged; tapping **✎** while locked reports that Home is locked instead of opening Edit mode | NOT RUN |
| 53 | In Edit mode, drag the add-QR button, then leave Edit mode and tap it | It is still the normal **+ เพิ่มรูป QR** action (the picker opens) | NOT RUN |
| 54 | Switch ถนัดมือ to ถนัดซ้าย and repeat one drag | The rest of Home mirrors for the left hand, and your own placement is kept (a saved layout wins over the default hand placement) | NOT RUN |

### D. Both action states are placeable (V0.9.1)

| # | Step | Expected | Status |
| --- | --- | --- | --- |
| 55 | With a **ready** item, enter Edit mode and drag the scan action **without** paying first | It moves straight away; no hand-off starts, no bank app opens | NOT RUN |
| 56 | With the same ready item, look at the rail in Edit mode | The four answers (✓ ⚠ ? ↻) are on the rail as dimmed placeholders and can be dragged — no need to hand the QR to the bank first | NOT RUN |
| 57 | With an **awaiting** item, enter Edit mode | The scan action is now the dimmed placeholder and can be dragged; the four real answers still work outside Edit mode | NOT RUN |
| 58 | Drag the answers into place while the item is ready, then hand the QR over and come back | The four answers sit exactly where you left them (one layout, not one per state) | NOT RUN |
| 59 | In Edit mode, tap a dimmed placeholder | Nothing happens: no payment, no problem report, no retry, no photo picker | NOT RUN |

### E. The rail order and the ปัญหา tab (V0.9.3)

| # | Step | Expected | Status |
| --- | --- | --- | --- |
| 60 | On Home, with an item the bank already holds, read the rail from top to bottom | **⚠ รายงานปัญหา** first, then **✓ ยืนยันสำเร็จ**, then **? ไม่ทราบผล**, then **↻ ลองสแกนอีกครั้ง** | NOT RUN |
| 61 | Enter Edit mode with the same item | The same four answers appear in the same order (plus the scan action as a dimmed placeholder), each can be dragged, and **no action fires** while or after dragging | NOT RUN |
| 62 | Tap **⚠ มีปัญหา** on the active QR | The item leaves Home **completely** — it is neither the top QR nor in คิวที่เหลือ — the next payable QR takes the top area by itself, the ปัญหา badge goes up by 1, the item is listed under ปัญหา, and **nothing is deleted** | NOT RUN |
| 63 | Open ปัญหา and read the actions of that item (ต้องเปลี่ยน QR) | Only **เปลี่ยน QR** and **ล้างรายการ** — no re-send is offered, because that QR was reported unusable | NOT RUN |
| 64 | Make an `UNKNOWN` item (hand a QR over, then tap **? ไม่ทราบผล**) and open ปัญหา | Its actions, top to bottom: **ลองส่ง QR ใหม่ · เปลี่ยน QR · ทำรายการเสร็จแล้ว · ล้างรายการ**. **ลองส่ง QR ใหม่** returns it to หน้าแรก as รอชำระ and sends **nothing** by itself | NOT RUN |
| 65 | (Advanced) Delete the stored image of a READY item, then tap it, and open ปัญหา | The item is `FAILED` and offers **ลองส่ง QR ใหม่ · เปลี่ยน QR · QR ใช้งานไม่ได้ · ล้างรายการ** — with no "ทำรายการเสร็จแล้ว", because nothing reached the bank | NOT RUN |
| 66 | Tap **ล้างรายการ** on any problem item | The confirmation dialog appears **first**; **ยกเลิก** keeps the item exactly as it was; **ล้างรายการ** deletes only that item and its images | NOT RUN |
| 67 | Turn every remaining item into a problem (report or ไม่ทราบผล) | Home shows "ยังไม่มี QR ที่พร้อมส่งไปธนาคาร" with the count and **no** QR panel; the ปัญหา badge still counts every one of them | NOT RUN |

## Evidence to attach

For each executed test, attach a screenshot named `<test-number>-<step>.png` under
`docs/evidence/` and reference it from the test above. Do not add screenshots you
did not take.

## What this app can never verify

- Whether a bank transaction actually completed.
- Whether the user verified the recipient and amount inside the banking app.
- Whether the banking app reads the QR from the shared image.
