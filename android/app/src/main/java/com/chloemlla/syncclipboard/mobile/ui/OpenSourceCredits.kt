package com.chloemlla.syncclipboard.mobile.ui

import androidx.annotation.StringRes
import com.chloemlla.syncclipboard.mobile.R

/**
 * Static first-party + third-party credits for the Android client.
 * Only direct runtime dependencies are listed (not test-only or transitive trees).
 */
data class DependencyCredit(
    val name: String,
    val author: String,
    @StringRes val descriptionRes: Int,
    val license: String,
    val url: String? = null,
)

object OpenSourceCredits {
    const val FORK_REPO_URL = "https://github.com/Chloemlla/SyncClipboard"
    const val UPSTREAM_REPO_URL = "https://github.com/Jeric-X/SyncClipboard"
    const val PROJECT_LICENSE = "MIT License"
    const val PROJECT_COPYRIGHT = "Copyright (c) 2022 JericX"

    val dependencies: List<DependencyCredit> = listOf(
        DependencyCredit(
            name = "Jetpack Compose / Compose BOM",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.oss_dep_compose,
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/compose",
        ),
        DependencyCredit(
            name = "AndroidX Activity Compose",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.oss_dep_activity_compose,
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/activity",
        ),
        DependencyCredit(
            name = "AndroidX Core KTX",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.oss_dep_core_ktx,
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/core",
        ),
        DependencyCredit(
            name = "AndroidX Lifecycle",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.oss_dep_lifecycle,
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
        ),
        DependencyCredit(
            name = "AndroidX Security Crypto",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.oss_dep_security_crypto,
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/security",
        ),
        DependencyCredit(
            name = "Material Icons Extended",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.oss_dep_material_icons,
            license = "Apache-2.0",
            url = "https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary",
        ),
        DependencyCredit(
            name = "OkHttp",
            author = "Square",
            descriptionRes = R.string.oss_dep_okhttp,
            license = "Apache-2.0",
            url = "https://square.github.io/okhttp/",
        ),
        DependencyCredit(
            name = "Kotlin Coroutines",
            author = "JetBrains",
            descriptionRes = R.string.oss_dep_coroutines,
            license = "Apache-2.0",
            url = "https://github.com/Kotlin/kotlinx.coroutines",
        ),
        DependencyCredit(
            name = "ASP.NET Core SignalR Client",
            author = "Microsoft",
            descriptionRes = R.string.oss_dep_signalr,
            license = "MIT",
            url = "https://github.com/dotnet/aspnetcore",
        ),
        DependencyCredit(
            name = "Shizuku API / Provider",
            author = "RikkaApps",
            descriptionRes = R.string.oss_dep_shizuku,
            license = "Apache-2.0",
            url = "https://github.com/RikkaApps/Shizuku",
        ),
        DependencyCredit(
            name = "Lumen Crash SDK",
            author = "Chloemlla",
            descriptionRes = R.string.oss_dep_lumen_crash,
            license = "Repository license",
            url = "https://github.com/Chloemlla/Project-Lumen",
        ),
    )
}
