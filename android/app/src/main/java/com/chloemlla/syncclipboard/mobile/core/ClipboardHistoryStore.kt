package com.chloemlla.syncclipboard.mobile.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Clipboard kind stored in local history. */
enum class HistoryType {
    TEXT,
    IMAGE,
    FILE,
    GROUP,
    ;

    companion object {
        fun fromWire(raw: String?): HistoryType = when (raw?.lowercase()) {
            "image" -> IMAGE
            "file" -> FILE
            "group" -> GROUP
            else -> TEXT
        }
    }

    fun wire(): String = name.lowercase()
}

/** How the entry entered history. */
enum class HistorySource {
    PUSH,
    PULL,
    MANUAL,
    ;

    companion object {
        fun fromWire(raw: String?): HistorySource = when (raw?.lowercase()) {
            "pull" -> PULL
            "manual" -> MANUAL
            else -> PUSH
        }
    }

    fun wire(): String = name.lowercase()
}

/**
 * One local clipboard history row.
 *
 * MVP stores full text for TEXT entries so "copy again" works offline.
 * Image/file rows keep a short preview only (no binary blobs).
 */
data class HistoryEntry(
    val id: String,
    val type: HistoryType,
    val preview: String,
    val content: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val source: HistorySource = HistorySource.PUSH,
)

/**
 * Local-only clipboard history (SharedPreferences + JSON).
 *
 * Desktop has a richer History DB + optional server sync; Android ships a
 * device-local list that is enough for re-copy / delete / favorite.
 */
class ClipboardHistoryStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun list(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    parse(arr.getJSONObject(i))?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Insert or merge a successful sync snapshot.
     * Dedupes consecutive identical (type, content/preview) rows within a short window.
     */
    @Synchronized
    fun record(
        type: HistoryType,
        preview: String,
        content: String? = null,
        source: HistorySource,
    ): HistoryEntry? {
        val cleanPreview = preview.trim().replace("\r\n", "\n")
        if (cleanPreview.isEmpty() && content.isNullOrBlank()) return null
        val body = content?.take(MAX_CONTENT_CHARS)
        val display = cleanPreview.take(MAX_PREVIEW_CHARS).ifBlank {
            body?.take(MAX_PREVIEW_CHARS).orEmpty()
        }
        if (display.isBlank()) return null

        val now = System.currentTimeMillis()
        val current = list().toMutableList()
        val head = current.firstOrNull()
        if (head != null &&
            head.type == type &&
            head.preview == display &&
            head.content == body &&
            now - head.timestampMs < DEDUPE_WINDOW_MS
        ) {
            // Refresh timestamp on rapid re-sync of the same payload.
            val refreshed = head.copy(timestampMs = now, source = source)
            current[0] = refreshed
            persist(current)
            return refreshed
        }

        val entry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            type = type,
            preview = display,
            content = body,
            timestampMs = now,
            favorite = false,
            source = source,
        )
        current.add(0, entry)
        while (current.size > MAX_ENTRIES) {
            val removable = current.indexOfLast { !it.favorite }
            if (removable < 0) {
                current.removeAt(current.lastIndex)
            } else {
                current.removeAt(removable)
            }
        }
        persist(current)
        return entry
    }

    @Synchronized
    fun delete(id: String) {
        val next = list().filterNot { it.id == id }
        persist(next)
    }

    @Synchronized
    fun setFavorite(id: String, favorite: Boolean) {
        val next = list().map { if (it.id == id) it.copy(favorite = favorite) else it }
        persist(next)
    }

    @Synchronized
    fun clearNonFavorites() {
        persist(list().filter { it.favorite })
    }

    private fun persist(entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    private fun HistoryEntry.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.wire())
        put("preview", preview)
        put("content", content)
        put("timestampMs", timestampMs)
        put("favorite", favorite)
        put("source", source.wire())
    }

    private fun parse(o: JSONObject): HistoryEntry? = runCatching {
        HistoryEntry(
            id = o.getString("id"),
            type = HistoryType.fromWire(o.optString("type")),
            preview = o.optString("preview"),
            content = o.optString("content", null)?.takeIf { it.isNotEmpty() && it != "null" },
            timestampMs = o.optLong("timestampMs", System.currentTimeMillis()),
            favorite = o.optBoolean("favorite", false),
            source = HistorySource.fromWire(o.optString("source")),
        )
    }.getOrNull()

    companion object {
        private const val PREFS_NAME = "syncclipboard_history"
        private const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 100
        private const val MAX_PREVIEW_CHARS = 240
        private const val MAX_CONTENT_CHARS = 32_000
        private const val DEDUPE_WINDOW_MS = 2_000L
    }
}
