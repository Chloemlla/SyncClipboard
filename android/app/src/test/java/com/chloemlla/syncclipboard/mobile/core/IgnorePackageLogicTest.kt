package com.chloemlla.syncclipboard.mobile.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure logic for ignore-package matching (mirrors [SettingsStore.isPackageIgnored]).
 */
class IgnorePackageLogicTest {

    @Test
    fun match_is_case_insensitive() {
        val list = listOf("com.Example.App", "com.other")
        assertTrue(isIgnored("com.example.app", list))
        assertTrue(isIgnored("COM.OTHER", list))
        assertFalse(isIgnored("com.missing", list))
        assertFalse(isIgnored(null, list))
        assertFalse(isIgnored("  ", list))
    }

    private fun isIgnored(packageName: String?, packages: List<String>): Boolean {
        if (packageName.isNullOrBlank()) return false
        val target = packageName.trim()
        return packages.any { it.equals(target, ignoreCase = true) }
    }
}
