package com.syncclipboard.mobile.core

import java.security.MessageDigest

object HashUtil {
    /**
     * hash = HEX_UPPER(SHA256(UTF8(text))), matching
     * SyncClipboard Utility.CalculateSHA256 (Convert.ToHexString => uppercase).
     */
    fun sha256UpperHex(text: String): String = sha256UpperHex(text.toByteArray(Charsets.UTF_8))

    fun sha256UpperHex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(HEX[(b.toInt() ushr 4) and 0xF])
            sb.append(HEX[b.toInt() and 0xF])
        }
        return sb.toString()
    }

    private val HEX = "0123456789ABCDEF".toCharArray()
}
