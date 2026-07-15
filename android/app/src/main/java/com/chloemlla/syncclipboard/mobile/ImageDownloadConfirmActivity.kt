package com.chloemlla.syncclipboard.mobile

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.TextView
import com.chloemlla.syncclipboard.mobile.sync.ImageDownloadConfirmBridge

/**
 * Transparent activity that asks whether a remote image should be downloaded
 * into the device clipboard. Completes [ImageDownloadConfirmBridge] and finishes.
 */
class ImageDownloadConfirmActivity : Activity() {
    private var requestId: Long = -1L
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1L)
        val request = ImageDownloadConfirmBridge.get(requestId)
        if (request == null) {
            finish()
            return
        }

        val messageView = TextView(this).apply {
            text = getString(
                R.string.image_download_confirm_message,
                request.fileName,
                formatSize(request.sizeBytes),
            )
            setPadding(48, 24, 48, 8)
            textSize = 15f
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.image_download_confirm_title)
            .setView(messageView)
            .setPositiveButton(R.string.image_download_confirm_accept) { _, _ ->
                complete(true)
            }
            .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                complete(false)
            }
            .setOnCancelListener {
                complete(false)
            }
            .setCancelable(true)
            .show()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            complete(false)
        }
        super.onDestroy()
    }

    private fun complete(accepted: Boolean) {
        if (completed) return
        completed = true
        ImageDownloadConfirmBridge.complete(requestId, accepted)
        finish()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.2f MB", mb)
    }

    companion object {
        const val EXTRA_REQUEST_ID = "request_id"
    }
}
