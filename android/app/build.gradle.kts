plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun providerString(name: String, defaultValue: String): String =
    providers.environmentVariable(name)
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: defaultValue

val releaseKeystoreFile = providerString("KEYSTORE_FILE", "")
val releaseKeystorePassword = providerString("KEYSTORE_PASSWORD", "")
val releaseKeyAlias = providerString("KEY_ALIAS", "")
val releaseKeyPassword = providerString("KEY_PASSWORD", "")
val hasReleaseSigningConfig = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it.isNotBlank() }

// Prefer CI-injected latest main auto-release. Do not hardcode a permanent pin.
val lumenCrashVersion: String =
    providers.gradleProperty("lumenCrashVersion")
        .orElse(providers.environmentVariable("LUMEN_CRASH_VERSION"))
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: error(
            "Missing lumen-crash version. Resolve the latest lumen-crash-v* release and set " +
                "LUMEN_CRASH_VERSION or gradle property lumenCrashVersion " +
                "(see android/README.md / scripts/resolve-lumen-crash-latest.sh).",
        )

val syncClipboardShortHash: String =
    providers.environmentVariable("SYNCCLIPBOARD_SHORT_HASH")
        .orElse(providers.gradleProperty("syncClipboardShortHash"))
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: "unknown"

// UTC build stamp for in-app “本次更新说明” identity (commit hash + build time).
// CI should inject SYNCCLIPBOARD_BUILD_TIME (ISO-8601). Local default is configuration-time UTC.
val syncClipboardBuildTime: String =
    providers.environmentVariable("SYNCCLIPBOARD_BUILD_TIME")
        .orElse(providers.gradleProperty("syncClipboardBuildTime"))
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: java.time.Instant.now().toString()

android {
    namespace = "com.chloemlla.syncclipboard.mobile"
    // androidx.core 1.17.0+ requires compileSdk 36+ (AAR metadata check).
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        // Align with compileSdk and Android 17 adaptation path.
        targetSdk = 37
        versionCode = providers.environmentVariable("SYNCCLIPBOARD_ANDROID_VERSION_CODE")
            .orNull
            ?.toIntOrNull()
            ?: 1
        versionName = providers.environmentVariable("SYNCCLIPBOARD_ANDROID_VERSION_NAME")
            .orNull
            ?.takeIf { it.isNotBlank() }
            ?: "1.0.0"

        buildConfigField("String", "SHORT_HASH", "\"$syncClipboardShortHash\"")
        buildConfigField("String", "BUILD_TIME", "\"$syncClipboardBuildTime\"")
        buildConfigField("String", "LUMEN_CRASH_VERSION", "\"$lumenCrashVersion\"")
    }

    // dual applicationId strategy for settings migration:
    //  - production: com.chloemlla.syncclipboard.mobile (new id)
    //  - legacyMigrate: com.syncclipboard.mobile so existing installs can update,
    //    export encrypted settings, and hand off to the production package.
    flavorDimensions += "distribution"
    productFlavors {
        create("production") {
            dimension = "distribution"
            isDefault = true
            applicationId = "com.chloemlla.syncclipboard.mobile"
        }
        create("legacyMigrate") {
            dimension = "distribution"
            applicationId = "com.syncclipboard.mobile"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    val releaseSigningConfig = if (hasReleaseSigningConfig) {
        signingConfigs.create("release") {
            storeFile = file(releaseKeystoreFile)
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    } else {
        null
    }

    buildTypes {
        release {
            releaseSigningConfig?.let {
                signingConfig = it
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        disable += "GradleDependency"
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // 1.17.0+ provides Live Update compat APIs (setRequestPromotedOngoing / setShortCriticalText).
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Real-time push over the same /SyncClipboardHub SignalR feed the desktop client uses.
    implementation("com.microsoft.signalr:signalr:8.0.11")
    // Shizuku: run privileged shell operations (keep-alive whitelisting + background
    // clipboard reads) via a UserService bound at the shell (adb) UID.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Crash capture + adaptive Compose crash report UI from Project Lumen.
    implementation("com.chloemlla.lumen:lumen-crash:$lumenCrashVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
