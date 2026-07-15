package com.syncclipboard.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebImageAssistTest {

    @Test
    fun extractImageUrl_fromDesktopStyleHtml() {
        val html =
            """<html><body><img src="https://cdn.example.com/a/b/photo.png" /></body></html>"""
        assertEquals("https://cdn.example.com/a/b/photo.png", WebImageAssist.extractImageUrl(html))
    }

    @Test
    fun extractImageUrl_fromSingleQuotedLooseTag() {
        val html = """<div><img alt="x" src='http://img.test/x.jpg' width="10"></div>"""
        assertEquals("http://img.test/x.jpg", WebImageAssist.extractImageUrl(html))
    }

    @Test
    fun extractImageUrl_directImageUrl() {
        assertEquals(
            "https://example.com/pic.WEBP?size=1",
            WebImageAssist.extractImageUrl("https://example.com/pic.WEBP?size=1"),
        )
    }

    @Test
    fun extractImageUrl_returnsNullForPlainText() {
        assertNull(WebImageAssist.extractImageUrl("hello world"))
        assertNull(WebImageAssist.extractImageUrl(null))
        assertNull(WebImageAssist.extractImageUrl(""))
    }

    @Test
    fun normalize_keepsPngBytes() {
        // Minimal PNG header + pad
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0, 0, 0, 0, 0,
        )
        val result = WebImageAssist.normalizeToDesktopImage(png, "image/png", "x.png")
        assertNotNull(result)
        assertEquals("png", result!!.extension)
        assertTrue(result.bytes.contentEquals(png))
    }

    @Test
    fun normalize_keepsJpegBytes() {
        val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0)
        val result = WebImageAssist.normalizeToDesktopImage(jpg, "image/jpeg", "x.jpg")
        assertNotNull(result)
        assertEquals("jpg", result!!.extension)
    }
}
