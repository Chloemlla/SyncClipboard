package com.chloemlla.syncclipboard.mobile.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Multi-file Group profile matching SyncClipboard.Shared.GroupProfile.
 *
 * Transfer blob is a UTF-8 entry-named zip (`File_{timestamp}.zip`).
 *
 * Hash algorithm (desktop GroupEntry / CaclHashAndSize):
 * 1. Collect entries (files + directories). Entry names use '/' separators.
 * 2. Sort by UTF-8 bytes of EntryName (unsigned byte order).
 * 3. For each entry, append UTF-8 of:
 *      - directory: "D|{entryName}\0"   (entryName ends with '/')
 *      - file:      "F|{entryName}|{length}|{CONTENT_SHA256_UPPER}\0"
 * 4. hash = HEX_UPPER(SHA256(concatenated bytes))
 *
 * On Android we typically only have top-level files from the clipboard (no folders),
 * so each file becomes an entry named by its display file name.
 */
data class GroupFilePart(
    val entryName: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GroupFilePart) return false
        return entryName == other.entryName && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * entryName.hashCode() + bytes.contentHashCode()
}

object GroupSync {
    private val utf8 = Charsets.UTF_8

    fun buildDataName(): String = FileProfileSync.timeStampName("File", "zip")

    fun contentHashOf(bytes: ByteArray): String = HashUtil.sha256UpperHex(bytes)

    /**
     * Desktop-compatible group hash over the given top-level file parts.
     * Empty set → SHA256 of empty input (same as desktop empty group).
     */
    fun profileHash(parts: List<GroupFilePart>): String {
        if (parts.isEmpty()) {
            return HashUtil.sha256UpperHex(ByteArray(0))
        }
        data class Sortable(val name: String, val nameBytes: ByteArray, val isDir: Boolean, val length: Long, val contentHash: String)

        val entries = parts.map { part ->
            val name = normalizeEntryName(part.entryName, isDirectory = false)
            Sortable(
                name = name,
                nameBytes = name.toByteArray(utf8),
                isDir = false,
                length = part.bytes.size.toLong(),
                contentHash = HashUtil.sha256UpperHex(part.bytes),
            )
        }.sortedWith { a, b -> compareUnsignedBytes(a.nameBytes, b.nameBytes) }

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (e in entries) {
            val line = if (e.isDir) {
                "D|${e.name}\u0000"
            } else {
                "F|${e.name}|${e.length}|${e.contentHash}\u0000"
            }
            digest.update(line.toByteArray(utf8))
        }
        val out = digest.digest()
        val sb = StringBuilder(out.size * 2)
        for (b in out) {
            sb.append("0123456789ABCDEF"[(b.toInt() ushr 4) and 0xF])
            sb.append("0123456789ABCDEF"[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    fun displayText(parts: List<GroupFilePart>): String =
        parts.joinToString("\n") { File(it.entryName).name }

    fun profile(parts: List<GroupFilePart>, dataName: String, zipBytes: ByteArray): ProfileDto = ProfileDto(
        type = ProfileDto.TYPE_GROUP,
        hash = profileHash(parts),
        text = displayText(parts),
        hasData = true,
        dataName = dataName,
        size = zipBytes.size.toLong(),
    )

    fun zip(parts: List<GroupFilePart>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Match desktop: UTF-8 entry names (Java ZipOutputStream uses UTF-8 when set)
            zos.setComment(null)
            for (part in parts) {
                val name = normalizeEntryName(part.entryName, isDirectory = false)
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(part.bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    /**
     * Extract zip into [outDir], returning absolute paths of top-level extracted files/dirs
     * that should be placed on the clipboard (files and first-level directories).
     */
    fun unzipTo(zipBytes: ByteArray, outDir: File): List<File> {
        outDir.mkdirs()
        val topLevel = linkedSetOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/').trimStart('/')
                if (name.isBlank() || name.contains("..")) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }
                val target = File(outDir, name)
                if (entry.isDirectory || name.endsWith('/')) {
                    target.mkdirs()
                    topLevel.add(name.trimEnd('/').substringBefore('/'))
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out -> zis.copyTo(out) }
                    topLevel.add(name.substringBefore('/'))
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return topLevel.mapNotNull { name ->
            val f = File(outDir, name)
            if (f.exists()) f else null
        }
    }

    private fun normalizeEntryName(raw: String, isDirectory: Boolean): String {
        var n = raw.replace('\\', '/').trim('/')
        if (isDirectory && !n.endsWith('/')) n += "/"
        return n
    }

    /** Unsigned byte-array lexicographic compare (matches .NET ByteArrayComparer for OrderBy). */
    private fun compareUnsignedBytes(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val av = a[i].toInt() and 0xFF
            val bv = b[i].toInt() and 0xFF
            if (av != bv) return av - bv
        }
        return a.size - b.size
    }
}
