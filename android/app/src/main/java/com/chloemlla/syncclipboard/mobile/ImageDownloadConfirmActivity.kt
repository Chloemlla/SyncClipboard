package com.chloemlla.syncclipboard.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chloemlla.syncclipboard.mobile.sync.ImageDownloadConfirmBridge
import com.chloemlla.syncclipboard.mobile.ui.SyncClipboardTheme

/**
 * Dialog-style activity that asks whether a remote image should be downloaded
 * into the gallery and clipboard. Completes [ImageDownloadConfirmBridge] and finishes.
 *
 * UI matches the Material 3 dialog language used by [com.chloemlla.syncclipboard.mobile.ui.MainScreen].
 */
class ImageDownloadConfirmActivity : ComponentActivity() {
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

        setContent {
            SyncClipboardTheme {
                ImageDownloadConfirmDialog(
                    fileName = request.fileName,
                    sizeLabel = formatSize(request.sizeBytes),
                    onConfirm = { complete(true) },
                    onDismiss = { complete(false) },
                )
            }
        }
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
        if (bytes <= 0L) return getString(R.string.image_download_size_unknown)
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

@Composable
private fun ImageDownloadConfirmDialog(
    fileName: String,
    sizeLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.image_download_confirm_title),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.image_download_confirm_message, fileName, sizeLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.image_download_confirm_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                DialogButtonLabel(
                    icon = Icons.Outlined.Download,
                    text = stringResource(R.string.image_download_confirm_accept),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Composable
private fun DialogButtonLabel(icon: ImageVector, text: String) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.size(8.dp))
    Text(text)
}
