package com.chloemlla.syncclipboard.mobile.shizuku

import android.content.ClipData
import android.os.Build
import android.os.IBinder
import android.util.Log
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
        return runCatching { readPrimaryClipAsShell() }
            .onFailure { Log.w(TAG, "readClipboardText failed (sdk=${Build.VERSION.SDK_INT})", it) }
            .getOrNull()
    }

    /**
     * Reflectively call IClipboard.getPrimaryClip with the running version's signature.
     *
     * Signature drift across API levels (package, attribution, userId, deviceId):
     *  - Pre-Q: often (String pkg, int userId)
     *  - Q–T:   (String pkg, String attributionTag, int userId)
     *  - U/V+:  may add deviceId / extra ints; we fill remaining ints with 0 and
     *           later String slots with null after the first package name.
     */
    private fun readPrimaryClipAsShell(): String? {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager
            .getMethod("getService", String::class.java)
            .invoke(null, "clipboard") as? IBinder
        if (binder == null) {
            Log.w(TAG, "clipboard service binder is null")
            return null
        }

        val stub = Class.forName("android.content.IClipboard\$Stub")
        val clipboard = stub
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
        if (clipboard == null) {
            Log.w(TAG, "IClipboard.asInterface returned null")
            return null
        }

        val candidates = clipboard.javaClass.methods
            .filter { it.name == "getPrimaryClip" }
            .sortedByDescending { it.parameterTypes.size }

        if (candidates.isEmpty()) {
            Log.w(TAG, "no getPrimaryClip method on ${clipboard.javaClass.name}")
            return null
        }

        var lastError: Throwable? = null
        for (method in candidates) {
            val clip = runCatching {
                val args = buildGetPrimaryClipArgs(method.parameterTypes)
                method.isAccessible = true
                method.invoke(clipboard, *args) as? ClipData
            }.onFailure {
                lastError = it
                Log.d(
                    TAG,
                    "getPrimaryClip try failed sig=${method.parameterTypes.joinToString { it.simpleName }}: ${it.message}",
                )
            }.getOrNull()

            if (clip != null) {
                if (clip.itemCount == 0) {
                    Log.d(TAG, "primary clip empty")
                    return null
                }
                // Prefer CharSequence text; fall back to coerce via toString of first item text.
                val text = clip.getItemAt(0)?.text?.toString()?.takeIf { it.isNotEmpty() }
                if (text == null) {
                    Log.d(TAG, "primary clip has no text item (count=${clip.itemCount})")
                }
                return text
            }
        }

        Log.w(
            TAG,
            "all getPrimaryClip signatures failed (sdk=${Build.VERSION.SDK_INT}, tried=${candidates.size})",
            lastError,
        )
        return null
    }

    /**
     * Build reflective args for getPrimaryClip:
     * first String = calling package (shell), later String (attributionTag) = null,
     * all int/long primitives = 0, other reference types = null.
     */
    private fun buildGetPrimaryClipArgs(parameterTypes: Array<Class<*>>): Array<Any?> {
        var stringSeen = 0
        return parameterTypes.map { type ->
            when {
                type == String::class.java -> if (stringSeen++ == 0) SHELL_PKG else null
                type == Int::class.javaPrimitiveType || type == Integer::class.java -> 0
                type == Long::class.javaPrimitiveType || type == java.lang.Long::class.java -> 0L
                type == Boolean::class.javaPrimitiveType || type == java.lang.Boolean::class.java -> false
                else -> null
            }
        }.toTypedArray()
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
        private const val TAG = "ShizukuUserService"
        private const val SHELL_PKG = "com.android.shell"
    }
}
