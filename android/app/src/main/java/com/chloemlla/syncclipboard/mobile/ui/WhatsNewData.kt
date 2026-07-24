package com.chloemlla.syncclipboard.mobile.ui

import androidx.annotation.StringRes
import com.chloemlla.syncclipboard.mobile.BuildConfig
import com.chloemlla.syncclipboard.mobile.R

/**
 * Build-scoped “本次更新说明” content.
 *
 * Identity is always [BuildConfig.SHORT_HASH] + [BuildConfig.BUILD_TIME] — never hard-code
 * commit hashes or build times in the page copy. Replace [topics] each user-facing release
 * so the guide stays scannable (welcome + a few bullets + finish).
 */
object WhatsNewData {
    data class Topic(
        @StringRes val titleRes: Int,
        @StringRes val bodyRes: Int,
    )

    /** Current build identity used for ack / auto-show. */
    val commitHash: String get() = BuildConfig.SHORT_HASH
    val buildTime: String get() = BuildConfig.BUILD_TIME
    val versionName: String get() = BuildConfig.VERSION_NAME

    /**
     * Topic slides for this build. Prefer replace over append so upgrades stay short.
     * Keep ~3–6 items covering what a returning user will notice.
     */
    val topics: List<Topic> = listOf(
        Topic(
            titleRes = R.string.whats_new_topic_shizuku_title,
            bodyRes = R.string.whats_new_topic_shizuku_body,
        ),
        Topic(
            titleRes = R.string.whats_new_topic_rearm_title,
            bodyRes = R.string.whats_new_topic_rearm_body,
        ),
        Topic(
            titleRes = R.string.whats_new_topic_scope_title,
            bodyRes = R.string.whats_new_topic_scope_body,
        ),
    )
}
