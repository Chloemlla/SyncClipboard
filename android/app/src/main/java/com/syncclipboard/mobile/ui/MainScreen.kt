package com.syncclipboard.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.syncclipboard.mobile.R
import com.syncclipboard.mobile.sync.PermissionHelper
import com.syncclipboard.mobile.sync.SyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result handled by system; service still starts */ }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResourceApp()) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            StatusCard(sync.status, sync.message, sync.lastText)

            SectionTitle(context.getString(R.string.section_server))
            OutlinedTextField(
                value = ui.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = { Text(context.getString(R.string.label_server_url)) },
                placeholder = { Text("192.168.1.10:5033") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text(context.getString(R.string.label_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(context.getString(R.string.label_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.pollSeconds.toString(),
                onValueChange = { v -> viewModel.onPollChange(v.toIntOrNull() ?: ui.pollSeconds) },
                label = { Text(context.getString(R.string.label_poll_seconds)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            ToggleRow(
                label = context.getString(R.string.toggle_pull),
                checked = ui.pullEnabled,
                onChange = viewModel::onPullChange,
            )
            ToggleRow(
                label = context.getString(R.string.toggle_push),
                checked = ui.pushEnabled,
                onChange = viewModel::onPushChange,
            )

            SectionTitle(context.getString(R.string.section_permissions))
            PermissionRow(
                label = context.getString(R.string.perm_battery),
                granted = ui.batteryOptExempt,
                actionLabel = context.getString(R.string.action_grant),
                onAction = {
                    runCatching {
                        context.startActivity(
                            PermissionHelper.requestIgnoreBatteryOptimizationsIntent(context),
                        )
                    }.onFailure {
                        context.startActivity(PermissionHelper.batterySettingsIntent())
                    }
                },
            )
            PermissionRow(
                label = context.getString(R.string.perm_accessibility),
                granted = ui.accessibilityEnabled,
                actionLabel = context.getString(R.string.action_open),
                onAction = { context.startActivity(PermissionHelper.accessibilitySettingsIntent()) },
            )
            Text(
                text = context.getString(R.string.hint_push_requires_accessibility),
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle(context.getString(R.string.section_control))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        ensureNotificationPermission()
                        viewModel.startService()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(context.getString(R.string.action_start)) }
                OutlinedButton(
                    onClick = { viewModel.applyAndRestart() },
                    modifier = Modifier.weight(1f),
                ) { Text(context.getString(R.string.action_apply)) }
            }
            OutlinedButton(
                onClick = { viewModel.stopService() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(context.getString(R.string.action_stop)) }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(status: SyncStatus, message: String, lastText: String) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val label = when (status) {
                SyncStatus.CONNECTED -> context.getString(R.string.status_connected)
                SyncStatus.CONNECTING -> context.getString(R.string.status_connecting)
                SyncStatus.ERROR -> context.getString(R.string.status_error, message)
                SyncStatus.STOPPED -> context.getString(R.string.status_stopped)
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (lastText.isNotBlank()) {
                Text(
                    context.getString(R.string.last_synced, lastText),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            Text(
                if (granted) context.getString(R.string.state_granted)
                else context.getString(R.string.state_missing),
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
        if (!granted) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun stringResourceApp(): String = LocalContext.current.getString(R.string.app_name)
