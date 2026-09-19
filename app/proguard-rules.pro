# ProGuard / R8 rules for QR Payment Queue.
#
# V0.1 ships with minification disabled (see app/build.gradle.kts). When a
# release build starts using R8, add explicit keep rules here instead of
# changing build defaults silently.

# Domain models are plain Kotlin; nothing special is required yet.
