package com.syncclipboard.mobile.sync

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils

/**
 * Helpers for the two OS-level grants this app needs to run reliably in the
 * background:
 *  - battery-optimization exemption (keeps the service alive under Doze), and
 *  - the accessibility service (lifts the API 29+ background clipboard-read block
 *    so phone -> server push works from any app).
 */
object PermissionHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Intent that opens the system dialog to request battery-optimization exemption. */
    fun requestIgnoreBatteryOptimizationsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun batterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ClipboardAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (entry in splitter) {
            if (entry.equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}
