package com.chloemlla.syncclipboard.mobile.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NetworkToolClientsTest {

    private val clients = NetworkToolClients()

    @Test
    fun createShortUrl_rejectsNonHttp() {
        try {
            clients.createShortUrl("ftp://example.com/a")
            fail("expected exception")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("http", ignoreCase = true))
        }
    }

    @Test
    fun createShortUrl_rejectsBlankHost() {
        try {
            clients.createShortUrl("not-a-url")
            fail("expected exception")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("http", ignoreCase = true))
        }
    }

    @Test
    fun createArtifact_requiresToken() {
        try {
            clients.createArtifact(
                backendBaseUrl = "https://example.com",
                accessToken = "",
                title = "t",
                content = "c",
            )
            fail("expected exception")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Sign in", ignoreCase = true) || e.message!!.isNotBlank())
        }
    }

    @Test
    fun generateImage_requiresPrompt() {
        try {
            clients.generateImage(
                baseUrl = "https://example.com/v1",
                apiKey = "k",
                model = "m",
                prompt = "  ",
            )
            fail("expected exception")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Prompt", ignoreCase = true))
        }
    }

    @Test
    fun ffmpeg_unavailable_when_no_path() {
        assertFalse(FfmpegMediaSupport.isAvailable(""))
        assertFalse(FfmpegMediaSupport.isAvailable(null))
        assertEquals(null, FfmpegMediaSupport.resolveFfmpegPath(""))
    }
}
