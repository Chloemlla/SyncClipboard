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

android {
    namespace = "com.syncclipboard.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.syncclipboard.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.environmentVariable("SYNCCLIPBOARD_ANDROID_VERSION_CODE")
            .orNull
            ?.toIntOrNull()
            ?: 1
        versionName = providers.environmentVariable("SYNCCLIPBOARD_ANDROID_VERSION_NAME")
            .orNull
            ?.takeIf { it.isNotBlank() }
            ?: "1.0.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Real-time push over the same /SyncClipboardHub SignalR feed the desktop client uses.
    implementation("com.microsoft.signalr:signalr:8.0.11")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
