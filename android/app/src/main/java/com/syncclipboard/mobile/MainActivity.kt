package com.syncclipboard.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.syncclipboard.mobile.shizuku.ShizukuManager
import com.syncclipboard.mobile.ui.MainScreen
import com.syncclipboard.mobile.ui.MainViewModel
import com.syncclipboard.mobile.ui.SyncClipboardTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Refresh the UI once the user responds to the Shizuku permission prompt.
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> viewModel.refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuManager.addPermissionResultListener(shizukuPermissionListener)
        setContent {
            SyncClipboardTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    override fun onDestroy() {
        ShizukuManager.removePermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }
}
