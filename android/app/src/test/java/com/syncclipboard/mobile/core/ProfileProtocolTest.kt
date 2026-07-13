package com.syncclipboard.mobile.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileProtocolTest {

    @Test
    fun sha256_matchesUppercaseHexOfUtf8() {
        // SHA-256("hello world") == b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9
        assertEquals(
            "B94D27B9934D3E08A52E52D7DA7DABFAC484EFE37A5380EE9088F7ACE2EFCDE9",
            HashUtil.sha256UpperHex("hello world"),
        )
    }

    @Test
    fun sha256_isUppercaseAndCoversCjk() {
        val hash = HashUtil.sha256UpperHex("你好，世界")
        assertEquals(64, hash.length)
        assertEquals(hash.uppercase(), hash)
    }

    @Test
    fun textProfile_producesCamelCaseStringEnumJson() {
        val json = JSONObject(ProfileDto.text("hello world").toJson())
        assertEquals("Text", json.getString("type"))
        assertEquals("hello world", json.getString("text"))
        assertEquals(false, json.getBoolean("hasData"))
        assertEquals(11L, json.getLong("size"))
        assertTrue(json.isNull("dataName"))
        // Hash must be present and uppercase hex.
        assertEquals(
            "B94D27B9934D3E08A52E52D7DA7DABFAC484EFE37A5380EE9088F7ACE2EFCDE9",
            json.getString("hash"),
        )
    }

    @Test
    fun fromJson_isCaseInsensitiveFriendlyAndRoundTrips() {
        val original = ProfileDto.text("round trip")
        val parsed = ProfileDto.fromJson(original.toJson())
        assertEquals(original, parsed)
    }

    @Test
    fun fromJson_treatsNullDataNameAsNull() {
        val raw = """{"type":"Text","hash":"ABC","text":"x","hasData":false,"dataName":null,"size":1}"""
        val parsed = ProfileDto.fromJson(raw)
        assertNull(parsed.dataName)
        assertFalse(parsed.hasData)
    }

    @Test
    fun fromJson_readsLargeTextTransferFields() {
        val raw = """{"type":"Text","hash":"DEAD","text":"preview","hasData":true,"dataName":"Text_x.txt","size":20000}"""
        val parsed = ProfileDto.fromJson(raw)
        assertTrue(parsed.hasData)
        assertEquals("Text_x.txt", parsed.dataName)
        assertEquals(20000L, parsed.size)
    }

    @Test
    fun identity_normalizesHashCase() {
        assertEquals("Text" to "ABCDEF", ProfileDto(type = "Text", hash = "abcdef").identity())
    }

    @Test
    fun textThreshold_boundary() {
        val exactly = "a".repeat(TextSync.INLINE_THRESHOLD)
        val tooBig = "a".repeat(TextSync.INLINE_THRESHOLD + 1)
        assertTrue(TextSync.isInline(exactly))
        assertFalse(TextSync.isInline(tooBig))
        assertEquals(TextSync.INLINE_THRESHOLD, TextSync.preview(tooBig).length)
        assertEquals(exactly, TextSync.preview(exactly))
    }

    @Test
    fun dataName_matchesDesktopPattern() {
        assertTrue(TextSync.buildDataName().matches(Regex("""Text_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}_\d+\.txt""")))
    }
}
