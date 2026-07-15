package com.chloemlla.syncclipboard.mobile.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncNotificationCopyTest {

    @Test
    fun truncatePreviewCollapsesWhitespaceAndEllipsizes() {
        assertEquals("", SyncNotificationCopy.truncatePreview("   "))
        assertEquals("hello", SyncNotificationCopy.truncatePreview("  hello  "))
        assertEquals(
            "line one line two",
            SyncNotificationCopy.truncatePreview("line one\nline two"),
        )
        val long = "a".repeat(60)
        val out = SyncNotificationCopy.truncatePreview(long, maxChars = 10)
        assertEquals(10, out.length)
        assertEquals("aaaaaaaaa…", out)
    }

    @Test
    fun previewKindDetectsMarkers() {
        assertEquals(SyncNotificationCopy.PreviewKind.EMPTY, SyncNotificationCopy.previewKind(""))
        assertEquals(SyncNotificationCopy.PreviewKind.TEXT, SyncNotificationCopy.previewKind("plain"))
        assertEquals(SyncNotificationCopy.PreviewKind.IMAGE, SyncNotificationCopy.previewKind("[Image] a.png"))
        assertEquals(SyncNotificationCopy.PreviewKind.FILE, SyncNotificationCopy.previewKind("[File] a.zip"))
        assertEquals(SyncNotificationCopy.PreviewKind.GROUP, SyncNotificationCopy.previewKind("[Group] 3 files"))
    }
}
