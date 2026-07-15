package com.chloemlla.syncclipboard.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMigratorTest {

    @Test
    fun snapshot_roundTripsJson() {
        val original = SettingsMigrator.Snapshot(
            config = ServerConfig(
                baseUrl = "192.168.1.10:5033",
                username = "admin",
                password = "s3cret",
                pollSeconds = 5,
                pullEnabled = true,
                pushEnabled = false,
                easyCopyImage = true,
                downloadWebImage = false,
            ),
            serviceEnabled = true,
        )
        val parsed = SettingsMigrator.Snapshot.fromJson(original.toJson())
        assertNotNull(parsed)
        assertEquals(original.config.baseUrl, parsed!!.config.baseUrl)
        assertEquals(original.config.username, parsed.config.username)
        assertEquals(original.config.password, parsed.config.password)
        assertEquals(original.config.pollSeconds, parsed.config.pollSeconds)
        assertEquals(original.config.pullEnabled, parsed.config.pullEnabled)
        assertEquals(original.config.pushEnabled, parsed.config.pushEnabled)
        assertEquals(original.config.easyCopyImage, parsed.config.easyCopyImage)
        assertEquals(original.config.downloadWebImage, parsed.config.downloadWebImage)
        assertEquals(original.serviceEnabled, parsed.serviceEnabled)
    }

    @Test
    fun constants_matchPackageRename() {
        assertEquals("com.syncclipboard.mobile", SettingsMigrator.LEGACY_PACKAGE)
        assertEquals("com.chloemlla.syncclipboard.mobile", SettingsMigrator.MODERN_PACKAGE)
        assertTrue(SettingsMigrator.LEGACY_AUTHORITY.startsWith(SettingsMigrator.LEGACY_PACKAGE))
        assertTrue(SettingsMigrator.MODERN_AUTHORITY.startsWith(SettingsMigrator.MODERN_PACKAGE))
    }
}
