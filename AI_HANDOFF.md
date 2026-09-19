# AI_HANDOFF.md

This handoff reflects reality. Nothing below claims a build that has not been
observed. Update it after every meaningful work session.

## Project

QR Payment Queue — native Android application for organizing QR payment tasks.

## Current version

0.1.0 (V0.1 foundation)

## Current status

Source complete for V0.1. The repository has been converted from the old
web scaffold into a clean native Android Gradle project. No APK has been
produced yet: this authoring environment has no JDK and no Android SDK, so
Gradle cannot run here. The APK build authority is **GitHub Actions**
(`.github/workflows/android.yml`), which runs `./gradlew assembleDebug` and
fails if the APK is missing or empty.

## Completed work (V0.1)

- Removed the entire legacy Vite/React/Convex web scaffold (see Files removed).
- Created a native Android Gradle project (Kotlin DSL): `settings.gradle.kts`,
  root `build.gradle.kts`, `gradle.properties`, version catalog, wrapper files.
- Created the `:app` module: AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01,
  compileSdk/targetSdk 35, minSdk 26, applicationId `com.enjirad.qrqueue`.
- Created `AndroidManifest.xml` with one launcher activity and **no runtime
  permissions** (folder access will use the Storage Access Framework in V0.2).
- Created the V0.1 Compose UI: app header, hero card with version chip, queue
  stats (in queue / total amount), Import QR Images button (honest
  "not implemented in V0.2" notice), payment queue section with empty state
  ("ยังไม่มี QR ในคิว"), and a payment-safety card.
- Created the domain model: `PaymentStatus` (full status list from the brief,
  error/terminal flags, `completedStates`), `QueueItem`, `formatSatang`.
- Created `QueueViewModel` (ViewModel + StateFlow) and `QueueRoute`/`QueueScreen`.
- Created an adaptive launcher icon (vector QR glyph, no binary assets).
- Added unit tests: `MoneyTest`, `PaymentStatusTest`.
- Created the GitHub Actions workflow that tests, lints, builds and verifies
  the APK and uploads artifact `qr-payment-queue-debug-apk`.
- Created `README.md`, `AI_RULES.md`, this file.

## Architecture

- Single activity (`MainActivity`) hosting Jetpack Compose.
- `ui/` → `QueueRoute` (composition with ViewModel) → `QueueScreen` (stateless UI).
- `ui/QueueViewModel` exposes `StateFlow<QueueUiState>`; UI collects it with
  `collectAsStateWithLifecycle`.
- `domain/` → pure Kotlin models and helpers (`PaymentStatus`, `QueueItem`,
  `formatSatang`), unit-testable without Android.
- `ui/theme/` → colors, typography, `QrQueueTheme` (no dynamic color on purpose).
- No persistence, no importer, no decoding yet — deliberately V0.1 scope.

## Important files

| File | Role |
| --- | --- |
| `app/build.gradle.kts` | app module config (SDK levels, Compose, dependencies) |
| `gradle/libs.versions.toml` | pinned, mutually compatible tool versions |
| `app/src/main/AndroidManifest.xml` | single launcher activity, no permissions |
| `app/src/main/java/com/enjirad/qrqueue/MainActivity.kt` | entry point |
| `app/src/main/java/com/enjirad/qrqueue/ui/QueueScreen.kt` | V0.1 UI |
| `app/src/main/java/com/enjirad/qrqueue/ui/QueueViewModel.kt` | screen state |
| `app/src/main/java/com/enjirad/qrqueue/domain/PaymentStatus.kt` | status model |
| `.github/workflows/android.yml` | APK build + verification + artifact |

## Files created

All Android/Gradle sources listed above, plus:
`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`,
`.gitignore`, `README.md`, `AI_RULES.md`, `AI_HANDOFF.md`,
`app/proguard-rules.pro`,
`app/src/main/res/values/{strings,colors,themes}.xml`,
`app/src/main/res/values-night/themes.xml`,
`app/src/main/res/drawable/ic_launcher_foreground.xml`,
`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`,
`app/src/test/java/com/enjirad/qrqueue/domain/{MoneyTest,PaymentStatusTest}.kt`.

## Files modified

- `.gitignore` — replaced web ignores with Android ignores + secret exclusions.
- `README.md` — rewritten to describe the Android project.

## Files removed

The complete legacy web application (it must not be restored):

- `src/` (all React/Convex/UI code), `public/`, `index.html`
- `package.json`, `package-lock.json`, `bun.lock`, `node_modules` metadata usage
- `vite.config.ts`, `tsconfig.json`, `tsconfig.app.json`, `tsconfig.node.json`
- `components.json`, `eslint.config.js`, `postcss.config.cjs`, `convex.json`
- `main.ts`, `sst-env.d.ts`, `vly-toolbar-readonly.tsx`,
  `.prettierignore`, `.prettierrc`, `integrations.md`

`.env*` files were left untouched (managed by the user / platform, never committed).

## Tests performed

- Unit tests authored: `MoneyTest` (5 cases), `PaymentStatusTest` (4 cases).
- **Not executed in the authoring environment**: no JDK / Android SDK here.
- CI runs `./gradlew testDebugUnitTest` on every push; treat that output as truth.

## Build command

```bash
./gradlew assembleDebug
```

## Build result

- LOCAL BUILD: **not performed** (authoring environment has no JDK/Android SDK;
  do not claim otherwise).
- GITHUB ACTIONS BUILD: **pending first push** — workflow is committed and will
  run automatically on push (also on PR and manual dispatch).

## APK path

Expected: `app/build/outputs/apk/debug/app-debug.apk`

## APK verification result

**Pending.** The workflow verifies `test -f` and `test -s` (exists, size > 0),
inspects the APK contents with `unzip -l`, and uploads artifact
`qr-payment-queue-debug-apk` with `if-no-files-found: error`. Until a green
Actions run is observed, the APK does not exist anywhere and must not be
reported as built.

## Git commit / push result

Version control in this environment is managed by the platform's Vly
integration; the agent cannot run `git` commands here. Files are synced and
committed by the platform. Record the real commit hash here after the next push.

## Known issues

1. `gradle/wrapper/gradle-wrapper.jar` is a binary and may be absent from a
   checkout. The CI workflow regenerates it (`gradle wrapper --gradle-version
   8.11.1`) before `./gradlew assembleDebug`, and `gradlew` prints a clear
   message if the jar is missing locally. Local CLI users run that same command
   once (Android Studio does not need it).
2. `local.properties` is intentionally not committed; local builds need an SDK
   path (Android Studio writes it automatically).
3. The V0.1 UI has no importer yet — the Import button shows an honest
   "planned for V0.2" notice. This is by design, not a bug.

## Blockers

None in the source. The only blocker was environmental: no JDK/Android SDK in
the authoring sandbox, which is why GitHub Actions is the build authority.

## Next task

V0.2 — folder selection via the Android Storage Access Framework
(`ACTION_OPEN_DOCUMENT_TREE` / `OpenDocumentTree`), no broad storage
permissions, feeding `QueueViewModel` with discovered document URIs.

## Important decisions

- Kotlin + Compose + Gradle Kotlin DSL; single-activity architecture.
- Version set pinned as a compatible group (AGP 8.7.3 / Kotlin 2.1.0 /
  Compose BOM 2024.12.01 / Gradle 8.11.1 / JDK 17).
- minSdk 26 so the adaptive launcher icon needs no binary PNG fallbacks.
- `android:allowBackup="false"` — a payment utility should not back up app data.
- No dynamic color: the brand palette is part of the product.
- Money is a `Long` count of satang end-to-end; formatting is a single tested
  helper (`formatSatang`).
- Status model encodes two hard rules: never `PAID` from sharing alone, never
  auto-retry unknown results.

## Things future agents must NOT repeat

- Do **not** reintroduce any web scaffold (Vite/React/Convex/tsconfig/package.json).
  The previous attempt failed by building a web app in an Android project.
- Do **not** claim a build/APK exists without CI evidence.
- Do **not** create fake queue items, sample payments or placeholder "success"
  states — the empty state is the honest V0.1 state.
- Do **not** add storage permissions; use SAF when V0.2 lands.
- Do **not** add bank automation, Accessibility clicking, OTP/PIN handling, or
  any "confirm payment" automation. Ever.
