package com.chloemlla.syncclipboard.mobile.shizuku

import android.content.ClipData
import android.os.IBinder
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Runs inside a process spawned by Shizuku at the shell (adb) UID. That identity holds
 * privileges the app can't get directly — notably READ_CLIPBOARD_IN_BACKGROUND and the
 * ability to run `dumpsys` / `cmd` / `appops` — so we expose a tiny, purpose-specific
 * surface (keep-alive + clipboard read) rather than arbitrary shell execution.
 *
 * Must stay self-contained: this class is loaded in a separate process and must not pull
 * in app UI / Application classes.
 */
class ShizukuUserService() : IShizukuUserService.Stub() {

    // Shizuku also probes for a (Context) constructor; keep a no-arg one as the primary.
    constructor(context: android.content.Context) : this()

    override fun destroy() {
        // Nothing persistent to release; process exit is handled by Shizuku.
    }

    override fun applyKeepAlive(packageName: String): String {
        val steps = listOf(
            "dumpsys deviceidle whitelist +$packageName",
            "cmd deviceidle whitelist +$packageName",
            "am set-standby-bucket $packageName active",
            "appops set $packageName RUN_ANY_IN_BACKGROUND allow",
            "appops set $packageName RUN_IN_BACKGROUND allow",
        )
        val log = StringBuilder()
        for (cmd in steps) {
            val (code, out) = runShell(cmd)
            log.append(if (code == 0) "OK  " else "ERR ").append(cmd)
            if (out.isNotBlank()) log.append(" -> ").append(out.trim().take(120))
            log.append('\n')
        }
        return log.toString().trimEnd()
    }

    override fun readClipboardText(): String? {
        return runCatching { readPrimaryClipAsShell() }.getOrNull()
    }

    /** Reflectively call IClipboard.getPrimaryClip with the running version's signature. */
    private fun readPrimaryClipAsShell(): String? {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager
            .getMethod("getService", String::class.java)
            .invoke(null, "clipboard") as? IBinder ?: return null

        val stub = Class.forName("android.content.IClipboard\$Stub")
        val clipboard = stub
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder) ?: return null

        val method = clipboard.javaClass.methods.firstOrNull { it.name == "getPrimaryClip" }
            ?: return null

        // Build args positionally by type: first String = calling pkg, later String
        // (attributionTag) = null, int = userId 0. Covers P..U signature drift.
        var stringSeen = 0
        val args = method.parameterTypes.map { type ->
            when {
                type == String::class.java -> if (stringSeen++ == 0) SHELL_PKG else null
                type == Int::class.javaPrimitiveType -> 0
                else -> null
            }
        }.toTypedArray()

        val clip = method.invoke(clipboard, *args) as? ClipData ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0)?.text?.toString()
        return text?.takeIf { it.isNotEmpty() }
    }

    private fun runShell(command: String): Pair<Int, String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val err = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val code = process.waitFor()
            code to (if (err.isNotBlank()) err else output)
        } catch (t: Throwable) {
            -1 to (t.message ?: "exec failed")
        }
    }

    companion object {
        private const val SHELL_PKG = "com.android.shell"
    }
}
