package com.chloemlla.syncclipboard.mobile.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Saves image bytes into the public Pictures/SyncClipboard album via MediaStore
 * (or legacy public Pictures on pre-Q).
 */
object GallerySaver {
    private const val TAG = "GallerySaver"
    private const val RELATIVE_DIR = "Pictures/SyncClipboard"

    fun saveImage(context: Context, bytes: ByteArray, fileName: String): Uri? {
        if (bytes.isEmpty()) return null
        val safeName = FileProfileSync.safeFileName(fileName, fallback = ImageSync.buildDataName("png"))
        val ext = ImageSync.extensionOf(safeName).ifBlank { "png" }
        val mime = ImageSync.mimeFromExtension(ext)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(context, bytes, safeName, mime)
            } else {
                saveLegacy(context, bytes, safeName)
            }
        }.onFailure {
            Log.w(TAG, "save image failed: $safeName", it)
        }.getOrNull()
    }

    private fun saveWithMediaStore(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore insert returned null for $fileName")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: throw IOException("openOutputStream failed for $uri")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, bytes: ByteArray, fileName: String): Uri {
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dir = File(pictures, "SyncClipboard").apply { mkdirs() }
        val target = uniqueFile(dir, fileName)
        FileOutputStream(target).use { it.write(bytes) }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, target.absolutePath)
            put(MediaStore.Images.Media.DISPLAY_NAME, target.name)
            put(MediaStore.Images.Media.MIME_TYPE, ImageSync.mimeFromExtension(ImageSync.extensionOf(target.name)))
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: Uri.fromFile(target)
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        val base = File(dir, fileName)
        if (!base.exists()) return base
        val stem = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "")
        var index = 1
        while (true) {
            val candidate = if (ext.isBlank()) {
                File(dir, "${stem}_$index")
            } else {
                File(dir, "${stem}_$index.$ext")
            }
            if (!candidate.exists()) return candidate
            index++
        }
    }
}
