package com.chloemlla.syncclipboard.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncClientUrlTest {

    @Test
    fun normalize_addsHttpSchemeWhenMissing() {
        assertEquals("http://192.168.1.10:5033/", SyncClient.normalizeBaseUrl("192.168.1.10:5033").toString())
    }

    @Test
    fun normalize_keepsHttpsScheme() {
        assertEquals("https://host:5033/", SyncClient.normalizeBaseUrl("https://host:5033").toString())
    }

    @Test
    fun normalize_trimsTrailingSlash() {
        assertEquals("http://host:5033/", SyncClient.normalizeBaseUrl("http://host:5033/").toString())
    }

    @Test
    fun normalize_rejectsBlank() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncClient.normalizeBaseUrl("   ")
        }
    }
}
