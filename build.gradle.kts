// Top-level build file for QR Payment Queue.
//
// Plugin versions live in gradle/libs.versions.toml so every module shares the
// same AGP / Kotlin versions. Nothing here is a web toolchain: this repository
// builds a native Android APK only (see AI_RULES.md).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
