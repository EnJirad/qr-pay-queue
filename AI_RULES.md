# AI_RULES.md — Permanent Rules for AI Agents

These rules apply to every future AI agent working on QR Payment Queue.
They override convenience, speed and "looks done" instincts.

## 1. Inspect before modify

Always read the actual files before editing them. Never assume the project
structure, package name, Gradle versions or APIs. If something is unknown,
inspect it or ask — do not guess.

## 2. Native Android only

This project is a native Android application (Kotlin + Jetpack Compose + Gradle
Kotlin DSL, package `com.enjirad.qrqueue`).

- Never convert it into a web application.
- Never add Vite, React, Next.js, Vue, Angular, Svelte, Capacitor, Cordova,
  PWA, TWA, WebView or HTML-based app scaffolding.
- Never wrap a website in an APK.
- The primary artifact is a real APK built by Gradle / GitHub Actions.

## 3. No guessing, no fake data

- Never invent project structure, APIs, schemas, build results or test results.
- Never create fake transactions, fake QR results or fake bank responses.
- If a feature is not implemented, label it as planned/not implemented in the UI.
- Sample/demo data may only exist if clearly labeled "Demo"/"Sample" and must
  never be mixed with real payment state.

## 4. Fix root causes

Fix the underlying cause of a failure rather than hiding symptoms. Do not
disable checks to make a red build green, and do not hand-edit generated files
(`_generated`, `R`, build outputs).

## 5. Preserve working features

Do not remove working functionality without a documented reason. Keep
single-activity Compose architecture, ViewModel + StateFlow state handling, and
the domain/UI separation unless a real requirement demands otherwise.

## 6. No duplicate systems

One application, one package, one Gradle build. Do not create parallel
`app-new/`, `android-new/`, `mobile/`, `web/` or prototype projects. Do not
duplicate responsibilities (e.g. two QR parsers or two queue stores).

## 7. Toolchain discipline

- Plugin/library versions are pinned in `gradle/libs.versions.toml`. Do not
  bump one version without checking compatibility of the others.
- Keep `applicationId = "com.enjirad.qrqueue"` stable.
- Do not add dependencies for things the platform already provides unless
  there is a clear reason.

## 8. Payment and banking security is untouchable

Never implement or weaken: automatic PIN/OTP entry, storing PINs, passwords,
OTPs or biometric secrets, blind Accessibility clicking of payment screens,
hidden bank API calls, or any automation that confirms a payment.

- Never mark a payment `PAID` just because the app opened or shared a QR.
- Never auto-retry a transaction whose result is unknown.
- The user confirms every real payment in their own banking app.

## 9. APK is the result

A change is done when `./gradlew assembleDebug` produces a real APK.

- `app/build/outputs/apk/debug/app-debug.apk` must exist.
- It must have a size greater than 0 bytes.
- GitHub Actions must build and upload it as `qr-payment-queue-debug-apk`.

## 10. No fake success

Never report BUILD: PASS / APK: FOUND without evidence from a real build. If a
build could not be run, say so explicitly and state where the build will run
(GitHub Actions). Read the actual error output before changing code.

## 11. Build failure rule

If a build fails: read the real error → determine the root cause → fix it →
re-run until it succeeds. Do not skip the failing step, do not delete the
check, and do not claim success.

## 12. Tests

Run `./gradlew testDebugUnitTest` after changing domain logic. Add tests for
new pure logic (parsers, validators, money math). Do not weaken assertions.

## 13. Git hygiene

Review `git status` / `git diff` before committing. Never commit secrets,
keystores, API keys, tokens or credentials. Never force push. Use the
repository's main branch.

## 14. Documentation duty

After meaningful work, update `AI_HANDOFF.md` with the real state: files
changed, tests run, build result, APK path/size, known issues, next task.
Update `README.md` when user-facing behavior changes.

## 15. Handoff

Future agents rely on the handoff section "Things future agents must NOT
repeat". Read it before starting.
