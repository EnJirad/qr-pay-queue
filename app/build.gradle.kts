import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.enjirad.qrqueue"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.enjirad.qrqueue"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.8.0"
    }

    buildTypes {
        release {
            // V0.1 does not ship a release build; keep minification off until
            // there is a signing config and real keep rules.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Lint runs in CI (`./gradlew lintDebug`) and must fail the build on a
        // real problem, so a lint error can never be mistaken for a pass.
        abortOnError = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)

    // No QR decoder dependency: the app does not read QR codes. The image is
    // handed straight to the selected bank with a package-targeted ACTION_SEND
    // intent, and the bank app reads it.

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
