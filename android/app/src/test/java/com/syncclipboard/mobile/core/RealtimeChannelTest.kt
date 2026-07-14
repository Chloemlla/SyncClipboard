package com.syncclipboard.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeChannelTest {

    @Test
    fun buildHubUrl_appendsHubPathAndDefaultsScheme() {
        assertEquals(
            "http://192.168.1.10:5033/SyncClipboardHub",
            RealtimeChannel.buildHubUrl("192.168.1.10:5033"),
        )
    }

    @Test
    fun buildHubUrl_keepsHttpsAndTrimsTrailingSlash() {
        assertEquals(
            "https://host:5033/SyncClipboardHub",
            RealtimeChannel.buildHubUrl("https://host:5033/"),
        )
    }
}
