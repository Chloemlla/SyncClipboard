package com.chloemlla.syncclipboard.mobile.core

import org.json.JSONObject

/**
 * Wire object matching SyncClipboard.Shared.ProfileDto.
 *
 * Serialized with camelCase keys and a string [type], matching what the
 * Windows/desktop clients and the standalone server emit
 * (JsonSerializerDefaults.Web). Reads are case-insensitive on the server,
 * but we emit camelCase to stay byte-compatible.
 */
data class ProfileDto(
    val type: String = TYPE_TEXT,
    val hash: String = "",
    val text: String = "",
    val hasData: Boolean = false,
    val dataName: String? = null,
    val size: Long = 0L,
) {
    fun toJson(): String = JSONObject().apply {
        put("type", type)
        put("hash", hash)
        put("text", text)
        put("hasData", hasData)
        // JSONObject.put(String, null) removes the key; emit JSONObject.NULL instead.
        put("dataName", dataName ?: JSONObject.NULL)
        put("size", size)
    }.toString()

    /** Change-detection key: a change is a different type OR a different hash. */
    fun identity(): Pair<String, String> = type to hash.uppercase()

    companion object {
        const val TYPE_TEXT = "Text"
        const val TYPE_IMAGE = "Image"
        const val TYPE_FILE = "File"
        const val TYPE_GROUP = "Group"

        fun fromJson(raw: String): ProfileDto {
            val obj = JSONObject(raw)
            return ProfileDto(
                type = obj.optString("type", TYPE_TEXT).ifBlank { TYPE_TEXT },
                hash = obj.optString("hash", ""),
                text = obj.optString("text", ""),
                hasData = obj.optBoolean("hasData", false),
                dataName = obj.optString("dataName", "").takeIf { it.isNotBlank() && !obj.isNull("dataName") },
                size = obj.optLong("size", 0L),
            )
        }

        /** Build an inline text profile (text must be <= [TextSync.INLINE_THRESHOLD] chars). */
        fun text(content: String): ProfileDto = ProfileDto(
            type = TYPE_TEXT,
            hash = HashUtil.sha256UpperHex(content),
            text = content,
            hasData = false,
            dataName = null,
            size = content.length.toLong(),
        )
    }
}
