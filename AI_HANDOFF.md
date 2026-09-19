# AI_HANDOFF.md

This handoff reflects reality. Nothing below claims a build, an APK or a device
result that has not been observed. Update it after every meaningful work session.

## Project

QR Payment Queue — native Android application that lets the user select one
financial app, queue QR payment screenshots, and hand each image straight to that
app, one image at a time.

Package `com.enjirad.qrqueue`. Native Android only: Kotlin + Jetpack Compose +
AndroidX + Material 3 + Gradle Kotlin DSL. No web technology, no WebView, no
Accessibility automation, no root, no PIN/OTP automation, no screen scraping, no
private bank API, no automatic payment confirmation.

## Current version

0.4.2 (versionCode 6) — plus the **Thai bank registry expansion** described below.
The version was deliberately not bumped so the existing CI workflow and artifact
name (`qr-payment-queue-v0.4.2-debug`) stay stable.

## This change: expand validated Thai bank support

Goal: support as many major Thai banking / payment apps as can be **verified**,
treating the package id as the single source of truth and never guessing.

Method (rules that were followed):

1. Search Google Play; open the app's **live listing**.
2. Read the package id **from the listing URL** (`.../details?id=<package>`).
3. Confirm the publisher is the real bank/company.
4. Any package id that could not be confirmed on Google Play was **not added**.

Old packages in this repo were wrong and are gone from the registry:

- `com.kasikornbank.kplus` → **404 on Google Play**; the live K PLUS listing is
  `com.kasikorn.retail.mbanking.wap`.
- `com.scb.BankApp` → 404; live is `com.scb.phone`.
- `com.krungthai.nextbanking` → 404; live is `ktbcs.netbank`.
- `com.bblmobilebanking` → 404; live is `com.bbl.mobilebanking`.

### Verified banks (single source of truth: `BankRegistry.allBanks`)

Package ids read from Google Play on **2026-09-19**.

| App | Package | Publisher | Category | Share status |
| --- | --- | --- | --- | --- |
| K PLUS | `com.kasikorn.retail.mbanking.wap` | Kasikornbank (KBank) | commercial | `UNKNOWN` (runtime) |
| SCB EASY | `com.scb.phone` | Siam Commercial Bank | commercial | `UNKNOWN` (runtime) |
| Krungthai NEXT | `ktbcs.netbank` | Krungthai Bank | commercial | `UNKNOWN` (runtime) |
| Bangkok Bank Mobile Banking | `com.bbl.mobilebanking` | Bangkok Bank | commercial | `UNKNOWN` (runtime) |
| krungsri | `com.krungsri.kma` | Bank of Ayudhya | commercial | `UNKNOWN` (runtime) |
| ttb touch | `com.TMBTOUCH.PRODUCTION` | TMBThanachart | commercial | `UNKNOWN` (runtime) |
| MyMo by GSB | `com.mobilife.gsb.mymo` | Government Savings Bank | specialised | `UNKNOWN` (runtime) |
| CIMB THAI | `com.cimbthai.digital.mycimb` | CIMB Thai Bank | commercial | `UNKNOWN` (runtime) |
| UOB TMRW Thailand | `com.uob.mightyth2` | United Overseas Bank (Thai) | commercial | `UNKNOWN` (runtime) |
| Dime! | `com.dimekkp.dimeapp` | KKP Dime | digital | `UNKNOWN` (runtime) |
| MAKE by KBank | `com.kasikornbank.makebykbank` | Kasikornbank (KBank) | digital | `UNKNOWN` (runtime) |
| Kept | `com.krungsri.kept` | Bank of Ayudhya | digital | `UNKNOWN` (runtime) |
| TrueMoney | `th.co.truemoney.wallet` | True Money Co. Ltd. | wallet | `UNKNOWN` (runtime) |

**"Google Play package verified" means the id exists on Play and belongs to that
publisher. It does NOT mean the app accepts a shared QR image.** `QrShareSupport`
is `UNKNOWN` for every bank until a real-device test (or official documentation)
proves otherwise; the runtime probe decides at selection time.

### Checked but NOT added (package id could not be confirmed on Play here)

LINE BK, ShopeePay (the live `com.shopee.th` is the Shopee shopping app, not a
standalone QR payer), KKP Mobile, TISCO, LH Bank, GHB ALL, BAAC A-Mobile, Islamic
Bank of Thailand, Bank of China (Thai), ICBC Thailand, Standard Chartered
Thailand, Citibank, EXIM. Adding these requires the same Play verification first.

## Model and states

- `BankInfo` now carries `id`, `displayName`, `packageName`, `company`,
  `category`, `googlePlayUrl`, `verificationDate`, `qrShareSupport`, `verified`.
- `BankCategory`: commercial / specialised / digital bank / payment wallet.
- `QrShareSupport`: `SUPPORTED` / `UNKNOWN` / `NOT_SUPPORTED`.
- `BankAvailability` (runtime, §5): `SHARE_CAPABLE`,
  `INSTALLED_BUT_NOT_SHARE_CAPABLE`, `NOT_INSTALLED`, `UNKNOWN`.
  `classify(...)` takes `installed: Boolean?`; `null` (probe could not complete)
  becomes `UNKNOWN` and is **never** treated as installed.
- `BankTargetStatus`: `isInstalled`, `canHandOff`, `shareCapable`.

## Files changed

- `domain/BankInfo.kt` — new `BankInfo`/`BankCategory`/`QrShareSupport`, 4-state
  `BankAvailability`, `BankTargetStatus`, and the 13-entry `BankRegistry`
  (`allBanks`, `findById`, `findByPackage`, `VERIFIED_ON`).
- `data/BankTarget.kt` — `classify` takes tri-state `installed`; `query` reports
  `UNKNOWN` on a non-visibility failure; package lookups use `packageName`.
- `data/BankSelectionStore.kt`, `ui/QueueViewModel.kt` — use `packageName`.
- `ui/QueueScreen.kt` — bank card/dialog render the 4 runtime states and show the
  publisher; no "ready" claim unless the app advertises image sharing.
- `AndroidManifest.xml` — `<queries>` lists exactly the 13 verified packages.
- `res/values/strings.xml` — status wording (`ติดตั้งแล้ว · ส่งรูป QR ได้`,
  `ไม่พบกิจกรรมที่รับรูป QR`, `ไม่สามารถตรวจสอบได้`).
- `README.md` — verified bank table and the package-vs-share distinction.

## Tests

Pure JVM JUnit 4 (no device, no emulator, no new dependency, no `@Ignore`):

- Registry: unique ids, unique packages, non-empty/valid package format,
  verified ⇒ Google Play URL matching the package and an ISO verification date,
  the 13 expected ids present.
- K PLUS: expects `com.kasikorn.retail.mbanking.wap` and asserts the old
  `com.kasikornbank.kplus` is absent, and that all four obsolete packages are
  gone from the registry.
- Intent: ACTION_SEND, `image/*`, EXTRA_STREAM `content://`, read grant,
  `setPackage` target per registry bank, no chooser.
- Persistence: selected bank survives restart; change bank updates it; unknown /
  mismatched stored value decodes to nothing.
- Uninstall: selected-but-missing package ⇒ `isInstalled=false`,
  `canUpload=false`, `requiresBankSelection=true`, old value kept.
- `UNKNOWN` probe ⇒ not installed, cannot hand off.

## Build result

- LOCAL BUILD: **not performed / not possible** — no JDK/SDK in this environment.
- GITHUB ACTIONS: **the authority**; read the run for this commit.

## CI result

To be filled from the GitHub Actions run for this commit (see git log for SHA).
Earlier runs `35448758349` and `35448891122` were green on the previous work.

## APK artifact

- Workflow artifact name: `qr-payment-queue-v0.4.2-debug`
- Path: `app/build/outputs/apk/debug/app-debug.apk`

## Real-device verification

**NOT DONE — REAL DEVICE VERIFICATION: NOT VERIFIED.** No device or emulator
here. Share capability for every bank is `UNKNOWN`. The checklist (Xiaomi 15T
Pro, Android 16) is in `docs/REAL_DEVICE_TEST.md`. Do not claim any bank is
"share verified" until those runs exist.

## Known limitations

1. Share capability is unverified for every bank (needs a device).
2. Some major banks (KKP Mobile, TISCO, LH Bank, GHB, BAAC, IBank, BOC, ICBC,
   SCB, Citi, StanChart, LINE BK, ShopeePay) are not registered because their
   package ids could not be confirmed on Google Play here.
3. Digital apps (Dime!, Kept, MAKE) may not offer QR payment at all; the runtime
   probe hides them when they cannot receive an image.
4. Bank package ids can change (as K PLUS did); re-verify on Play before trusting
   a stale id.
5. No instrumented UI tests; no emulator in CI.

## Things future agents must NOT repeat

- Do not guess a package id or copy one from memory/blog. Verify on the live
  Google Play listing and record the date.
- Do not treat "package verified" as "share verified".
- Do not report "not installed" from a failed `queryIntentActivities`; installed
  and share-capable are separate states (`UNKNOWN` ≠ `NOT_INSTALLED`).
- Do not scatter package strings outside `BankRegistry`.
- Do not add a QR decoder, Accessibility automation, PIN/OTP handling, or any
  automatic payment confirmation/retry.
- Do not claim a build, APK, CI or device pass that was not observed.
