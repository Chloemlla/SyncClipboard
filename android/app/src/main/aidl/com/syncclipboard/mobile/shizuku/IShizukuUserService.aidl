package com.syncclipboard.mobile.shizuku;

/**
 * Privileged operations executed inside a UserService running at the shell (adb) UID
 * via Shizuku. Only whitelisted, purpose-specific calls are exposed — no arbitrary
 * shell execution surface is handed to the app process.
 */
interface IShizukuUserService {

    /** Lifecycle hook invoked by Shizuku when the service is torn down. */
    void destroy() = 16777114;

    /**
     * Apply best-effort keep-alive tweaks with shell privileges:
     * Doze whitelist, standby-bucket = active, and RUN_ANY_IN_BACKGROUND appop allow.
     * Returns a short human-readable status log (one line per applied step).
     */
    String applyKeepAlive(String packageName) = 1;

    /**
     * Read the current primary-clip text using the shell identity, which holds
     * READ_CLIPBOARD_IN_BACKGROUND — bypassing the Android 10+ background read block.
     * Returns null when the clip is empty / not text / unavailable.
     */
    String readClipboardText() = 2;
}
