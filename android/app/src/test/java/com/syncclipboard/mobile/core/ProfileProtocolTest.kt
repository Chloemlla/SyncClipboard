package com.syncclipboard.mobile.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProfileProtocolTest {

    @Test
    fun sha256_matchesUppercaseHexOfUtf8() {
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

    @Test
    fun imageProfileHash_matchesDesktopFileProfileCombineHash() {
        val content = "hello world".toByteArray(Charsets.UTF_8)
        val contentHash = HashUtil.sha256UpperHex(content)
        val fileName = "Image_test.png"
        val expected = HashUtil.sha256UpperHex("$fileName|$contentHash")
        assertEquals(expected, ImageSync.profileHash(fileName, content))
        assertEquals(expected, FileProfileSync.profileHash(fileName, content))
    }

    @Test
    fun imageProfile_jsonShapeMatchesDesktop() {
        val content = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val fileName = "Image_2026-07-15_12-00-00_123456.png"
        val profile = ImageSync.profile(fileName, content)
        val json = JSONObject(profile.toJson())
        assertEquals("Image", json.getString("type"))
        assertEquals(true, json.getBoolean("hasData"))
        assertEquals(fileName, json.getString("text"))
        assertEquals(fileName, json.getString("dataName"))
        assertEquals(content.size.toLong(), json.getLong("size"))
        assertEquals(ImageSync.profileHash(fileName, content), json.getString("hash"))
    }

    @Test
    fun imageDataName_matchesDesktopPattern() {
        assertTrue(
            ImageSync.buildDataName("png")
                .matches(Regex("""Image_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}_\d+\.png""")),
        )
    }

    @Test
    fun desktopImageName_recognizesSupportedExtensions() {
        assertTrue(ImageSync.isDesktopImageName("a.PNG"))
        assertTrue(ImageSync.isDesktopImageName("b.jpeg"))
        assertFalse(ImageSync.isDesktopImageName("c.webp"))
        assertFalse(ImageSync.isDesktopImageName("d.txt"))
    }

    @Test
    fun fromJson_readsImageProfile() {
        val raw =
            """{"type":"Image","hash":"ABCD","text":"Image_x.png","hasData":true,"dataName":"Image_x.png","size":42}"""
        val parsed = ProfileDto.fromJson(raw)
        assertEquals(ProfileDto.TYPE_IMAGE, parsed.type)
        assertTrue(parsed.hasData)
        assertEquals("Image_x.png", parsed.dataName)
    }

    @Test
    fun fileProfile_jsonAndHash() {
        val content = "payload".toByteArray(Charsets.UTF_8)
        val name = "notes.txt"
        val profile = FileSync.profile(name, content)
        val json = JSONObject(profile.toJson())
        assertEquals("File", json.getString("type"))
        assertEquals(true, json.getBoolean("hasData"))
        assertEquals(name, json.getString("dataName"))
        assertEquals(FileProfileSync.profileHash(name, content), json.getString("hash"))
    }

    @Test
    fun groupHash_singleFile_matchesEntryFormat() {
        val bytes = "hello".toByteArray(Charsets.UTF_8)
        val part = GroupFilePart("a.txt", bytes)
        val contentHash = HashUtil.sha256UpperHex(bytes)
        // Entry line: F|a.txt|5|{contentHash}\0
        val line = "F|a.txt|${bytes.size}|$contentHash\u0000"
        val expected = HashUtil.sha256UpperHex(line.toByteArray(Charsets.UTF_8))
        assertEquals(expected, GroupSync.profileHash(listOf(part)))
    }

    @Test
    fun groupHash_twoFiles_sortsByEntryNameBytes() {
        val a = GroupFilePart("b.txt", "B".toByteArray())
        val b = GroupFilePart("a.txt", "A".toByteArray())
        // Sorted by name: a.txt then b.txt
        val lineA = "F|a.txt|1|${HashUtil.sha256UpperHex("A".toByteArray())}\u0000"
        val lineB = "F|b.txt|1|${HashUtil.sha256UpperHex("B".toByteArray())}\u0000"
        val expected = HashUtil.sha256UpperHex((lineA + lineB).toByteArray(Charsets.UTF_8))
        assertEquals(expected, GroupSync.profileHash(listOf(a, b)))
    }

    @Test
    fun groupZip_roundTrip() {
        val parts = listOf(
            GroupFilePart("one.txt", "one".toByteArray()),
            GroupFilePart("two.bin", byteArrayOf(1, 2, 3)),
        )
        val zip = GroupSync.zip(parts)
        assertTrue(zip.isNotEmpty())
        val dir = File.createTempFile("grp", "dir").apply {
            delete()
            mkdirs()
        }
        try {
            val files = GroupSync.unzipTo(zip, dir)
            assertEquals(2, files.size)
            assertTrue(files.any { it.name == "one.txt" && it.readText() == "one" })
            assertTrue(files.any { it.name == "two.bin" && it.readBytes().contentEquals(byteArrayOf(1, 2, 3)) })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun groupProfile_jsonShape() {
        val parts = listOf(GroupFilePart("a.txt", "x".toByteArray()))
        val zip = GroupSync.zip(parts)
        val dataName = "File_2026-07-15_12-00-00_111111.zip"
        val profile = GroupSync.profile(parts, dataName, zip)
        val json = JSONObject(profile.toJson())
        assertEquals("Group", json.getString("type"))
        assertEquals(true, json.getBoolean("hasData"))
        assertEquals(dataName, json.getString("dataName"))
        assertEquals("a.txt", json.getString("text"))
        assertEquals(GroupSync.profileHash(parts), json.getString("hash"))
    }

    @Test
    fun fromJson_readsFileAndGroup() {
        val fileRaw =
            """{"type":"File","hash":"AB","text":"x.bin","hasData":true,"dataName":"x.bin","size":9}"""
        assertEquals(ProfileDto.TYPE_FILE, ProfileDto.fromJson(fileRaw).type)
        val groupRaw =
            """{"type":"Group","hash":"CD","text":"a\nb","hasData":true,"dataName":"File_x.zip","size":99}"""
        assertEquals(ProfileDto.TYPE_GROUP, ProfileDto.fromJson(groupRaw).type)
    }
}
