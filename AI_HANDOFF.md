# AI_HANDOFF.md

This handoff reflects reality. Nothing below claims a build that has not been
observed. Update it after every meaningful work session.

## Project

QR Payment Queue — native Android application for organizing QR payment tasks.

## Current version

0.1.0 (V0.1 foundation)

## Current status

**CI/CD repair session.** The native Android project itself was untouched
(rule: fix only what blocks CI). The GitHub Actions workflow failed twice at
the same step before Gradle ever ran; the workflow has been rewritten to fix
the root cause. The new run's result is **pending** — see "GitHub Actions
result" below. Do not report the CI as green until run #3 (or later) is
observed passing.

## Completed work (this session — CI/CD only)

- Diagnosed both failed runs from the GitHub API (see below) — not from guesses.
- Rewrote `.github/workflows/android.yml` to fix the root cause.
- Removed web-scaffold leftovers that had been committed to the repository.
- Extended `.gitignore` so JS toolchain files cannot be committed again.
- No changes to: Kotlin sources, Gradle build files, manifest, resources,
  version catalog, wrapper. The app feature set (V0.1) is unchanged.

## Diagnosis (with evidence)

Repository: `EnJirad/qr-pay-queue` (public), branch `main`.

| Run | Commit | Step failed | Duration |
| --- | --- | --- | --- |
| #1 id `35420781950`, job `105837947672` | `aaa66a19c6c0e72c536e84ff04d625ef1740ab49` | Step 4 "Set up Android environment" (`android-actions/setup-android@v3`) — failure after ~15 s | 23 s total |
| #2 id `35424162034`, job `105847109924` | `f21a9f0e03bf517d105e89b6610f133f541b9d58` | Step 4 "Set up Android environment" (`android-actions/setup-android@v3`) — failure after ~6 s | 12 s total |

All later steps (SDK packages, Gradle, tests, lint, `assembleDebug`, APK
verification, artifact upload) were **skipped** — the build never reached
Gradle. Full step logs require sign-in (API returned 403), so the exact
stderr text could not be captured; the diagnosis rests on the step-level
conclusions above plus public evidence of the same class of failure.

Root cause (from evidence):

- Both failures occurred inside `android-actions/setup-android@v3`, not in
  project code. Runner annotations on both jobs say the `ubuntu-latest` image
  is migrating (Ubuntu 26, actions/runner-images#14748) and v3 (Node 20) runs
  force-pinned on Node 24.
- `android-actions/setup-android` issue #546 (opened 2026-09-18, one day
  before these runs) reports the identical breakage pattern on fresh
  images — preinstalled Android SDK pieces (build-tools) are no longer
  guaranteed — and the collaborator's recommended fix is to use
  `setup-android@v4` with an explicit `packages` input instead of trusting
  image preinstalls.

## Fix applied

`.github/workflows/android.yml` was rewritten:

1. `runs-on: ubuntu-24.04` — pin the image; stop chasing the rolling
   `ubuntu-latest` migration.
2. `actions/checkout@v5` and `actions/setup-java@v5` (v4 was deprecated —
   the runner annotated this explicitly).
3. `android-actions/setup-android@v4` **with** `packages: "platform-tools
   platforms;android-35 build-tools;35.0.0"` — install exactly what AGP 8.7.3
   / compileSdk 35 needs, not what the image happens to contain.
4. Kept: JDK 17 Temurin, Gradle 8.11.1 via `gradle/actions/setup-gradle@v4`,
   wrapper regeneration fallback, `chmod +x gradlew`.
5. Gate order unchanged: `testDebugUnitTest` → `lintDebug` →
   `./gradlew assembleDebug --stacktrace` → `test -f` → `test -s` →
   `unzip -l` inspection → upload artifact `qr-payment-queue-debug-apk`
   with `if-no-files-found: error`.

## Files modified (this session)

- `.github/workflows/android.yml` — rewritten as described above.
- `.gitignore` — additionally ignores `package.json`, `package-lock.json`,
  `bun.lock`, `bun.lockb`, `integrations.md`, `src/` (web leftovers guard).

## Files removed (this session)

Web-scaffold files that had been committed to git in error:

- `package.json` (`@vly-ai/integrations` only)
- `bun.lock`
- `integrations.md` (web/integration docs)
- `src/lib/vly-integrations.ts` (the only remaining `src/` file)

**Security note:** `.env.example`, `.env.keys` and `.env.local` also existed
in the working tree and in git history (HEAD commit `f21a9f0`). The sandbox
blocks reading/writing `.env*` files, so they could not be deleted here.
`.gitignore` already excludes `.env` / `.env.*`, so **future** commits will
not include them, but `.env.keys` may contain a secret and is already in the
public git history. **The user must rotate any value that was in `.env.keys`
and, if desired, purge it from history (e.g. `git filter-repo`) — this agent
could not perform either action from the sandbox.**

## Files created

None this session.

## Tests performed

- YAML sanity check on the new workflow (no tab characters; structure
  reviewed line by line). No YAML parser was available in the sandbox.
- **No local Gradle build was performed** — the sandbox has no JDK/Android
  SDK. The first real compile/test run happens in Actions.

## Build command

```bash
./gradlew assembleDebug
```

## Build result

- LOCAL BUILD: **not performed / not possible** (no JDK, no Android SDK).
- GITHUB ACTIONS BUILD (history): **FAIL** — runs #1 and #2, both at
  `setup-android@v3` (see Diagnosis).
- GITHUB ACTIONS BUILD (current): **PENDING** — the rewritten workflow runs
  on the next push; observe run #3 before reporting anything.

## APK path

Expected: `app/build/outputs/apk/debug/app-debug.apk`

## APK verification result

**Not verified — no APK exists yet.** Verification is the CI steps
`test -f`, `test -s`, `ls -lh`, `unzip -l` followed by artifact upload
`qr-payment-queue-debug-apk`.

## Git commit / push result

Version control is managed by the platform's Vly integration; the agent
cannot run `git` here. Changes are synced/committed by the platform. Record
the real commit hash of this fix here once observed, then update the
"GitHub Actions result" section with run #3's URL, conclusion and APK size.

## GitHub Actions result

- Run #1 (`35420781950`): **FAIL** — step 4, `android-actions/setup-android@v3`.
- Run #2 (`35424162034`): **FAIL** — step 4, `android-actions/setup-android@v3`.
- Run #3 (workflow rewrite): **PENDING**.

## Known issues

1. The exact stderr of the `setup-android@v3` failures is behind sign-in;
   root cause was established from step-level evidence + public issue #546.
2. `.env.keys` (possible secret) is present in public git history — rotation
   is the user's action item; see Security note above.
3. `gradle/wrapper/gradle-wrapper.jar` is committed (43,583 bytes, verified
   in the git tree), and CI additionally regenerates it defensively.
4. V0.1 UI has no importer yet — the Import button shows an honest
   "planned for V0.2" notice. By design, not a bug.

## Blockers

None in the source. The previous blocker (CI dying in `setup-android@v3`) is
addressed by the rewritten workflow; confirmation is the pending run #3.

## Next task

1. Observe run #3; if it fails, open its log (signed in), read the real
   error, fix the root cause, push again — repeat until green.
2. Once green: record run URL, commit hash and APK size in this file.
3. Then continue feature work: V0.2 — folder selection via the Android
   Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE` /
   `OpenDocumentTree`), no broad storage permissions, feeding
   `QueueViewModel` with discovered document URIs.

## Important decisions

- CI fix only: no Kotlin/Gradle/manifest changes in this session (task scope).
- Runner image pinned to `ubuntu-24.04` for determinism; can be un-pinned
  deliberately later once the Ubuntu 26 migration settles.
- SDK packages installed explicitly rather than trusted from the image.
- Pinned-version set unchanged (AGP 8.7.3 / Kotlin 2.1.0 / Compose BOM
  2024.12.01 / Gradle 8.11.1 / JDK 17).
- Status model still encodes the hard rules: never `PAID` from sharing alone,
  never auto-retry unknown results.

## Things future agents must NOT repeat

- Do **not** reintroduce any web scaffold (Vite/React/Convex/tsconfig/
  package.json/bun.lock/integrations.md/`src/`).
- Do **not** claim a build/APK exists without CI evidence.
- Do **not** trust `ubuntu-latest` + whatever Android SDK pieces it
  preinstalls — declare the packages you need.
- Do **not** create fake queue items, sample payments or placeholder
  "success" states.
- Do **not** add storage permissions; use SAF when V0.2 lands.
- Do **not** add bank automation, Accessibility clicking, OTP/PIN handling,
  or any "confirm payment" automation. Ever.
- Do **not** commit secrets; never touch `.env*` in git.
