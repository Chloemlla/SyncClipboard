package com.syncclipboard.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.syncclipboard.mobile.R
import com.syncclipboard.mobile.shizuku.ShizukuAvailability
import com.syncclipboard.mobile.sync.PermissionHelper
import com.syncclipboard.mobile.sync.SyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showStopConfirm by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = context.getString(R.string.app_name),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppHeader(ui = ui, sync = sync)

                StatusBanner(status = sync.status, message = sync.message, lastText = sync.lastText)

                SectionTitle(
                    text = context.getString(R.string.section_server),
                    subtitle = context.getString(R.string.section_server_subtitle),
                    icon = Icons.Outlined.Dns,
                )
                if (ui.baseUrl.isBlank()) {
                    InfoCard(
                        title = context.getString(R.string.firstrun_title),
                        icon = Icons.Outlined.Info,
                        lines = listOf(
                            context.getString(R.string.firstrun_line1),
                            context.getString(R.string.firstrun_line2),
                        ),
                    )
                }
                OutlinedTextField(
                    value = ui.baseUrl,
                    onValueChange = viewModel::onBaseUrlChange,
                    label = { Text(context.getString(R.string.label_server_url)) },
                    placeholder = { Text(context.getString(R.string.url_placeholder)) },
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
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = context.getString(
                                    if (passwordVisible) R.string.cd_hide_password else R.string.cd_show_password,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ui.pollSeconds.toString(),
                    onValueChange = { v -> viewModel.onPollChange(v.toIntOrNull() ?: ui.pollSeconds) },
                    label = { Text(context.getString(R.string.label_poll_seconds)) },
                    supportingText = { Text(context.getString(R.string.poll_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                TestConnectionRow(
                    testing = ui.testing,
                    testOk = ui.testOk,
                    testMessage = ui.testMessage,
                    onTest = viewModel::testConnection,
                )

                SectionTitle(
                    text = context.getString(R.string.section_sync),
                    subtitle = context.getString(R.string.section_sync_subtitle),
                    icon = Icons.Outlined.SwapVert,
                )
                ToggleCard(
                    icon = Icons.Outlined.Download,
                    label = context.getString(R.string.toggle_pull),
                    checked = ui.pullEnabled,
                    onChange = viewModel::onPullChange,
                )
                ToggleCard(
                    icon = Icons.Outlined.Upload,
                    label = context.getString(R.string.toggle_push),
                    checked = ui.pushEnabled,
                    onChange = viewModel::onPushChange,
                )

                SectionTitle(
                    text = context.getString(R.string.section_permissions),
                    subtitle = context.getString(R.string.section_permissions_subtitle),
                    icon = Icons.Outlined.Security,
                )
                PermissionCard(
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
                PermissionCard(
                    label = context.getString(R.string.perm_accessibility),
                    granted = ui.accessibilityEnabled,
                    actionLabel = context.getString(R.string.action_open),
                    onAction = { context.startActivity(PermissionHelper.accessibilitySettingsIntent()) },
                )
                // Shizuku: optional advanced backend for keep-alive + accessibility-free
                // background clipboard read. Always surfaced (even when not installed) so
                // users can discover it; the card adapts its label/action to the state.
                ShizukuCard(
                    availability = ui.shizuku,
                    permanentlyDenied = ui.shizukuPermanentlyDenied,
                    keepAliveOk = ui.shizukuKeepAliveOk,
                    onAction = {
                        val intent = viewModel.onShizukuAction()
                        if (intent != null) {
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    runCatching {
                                        context.startActivity(viewModel.shizukuInstallFallbackIntent())
                                    }
                                }
                        }
                    },
                )
                if (ui.shizuku != ShizukuAvailability.READY &&
                    ui.pushEnabled && !ui.accessibilityEnabled
                ) {
                    InfoCard(
                        title = context.getString(R.string.perm_accessibility),
                        icon = Icons.Outlined.Info,
                        lines = listOf(context.getString(R.string.hint_push_requires_accessibility)),
                    )
                }

                SectionTitle(
                    text = context.getString(R.string.section_control),
                    subtitle = context.getString(R.string.section_control_subtitle),
                    icon = Icons.Outlined.PlayArrow,
                )
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
                    ) { ButtonLabel(Icons.Outlined.PlayArrow, context.getString(R.string.action_start)) }
                    OutlinedButton(
                        onClick = { viewModel.applyAndRestart() },
                        modifier = Modifier.weight(1f),
                    ) { ButtonLabel(Icons.Outlined.Refresh, context.getString(R.string.action_apply)) }
                }
                OutlinedButton(
                    onClick = { showStopConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { ButtonLabel(Icons.Outlined.Stop, context.getString(R.string.action_stop)) }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showStopConfirm) {
        ConfirmActionDialog(
            title = context.getString(R.string.stop_confirm_title),
            message = context.getString(R.string.stop_confirm_message),
            confirmText = context.getString(R.string.stop_confirm_confirm),
            confirmIcon = Icons.Outlined.Stop,
            onConfirm = {
                showStopConfirm = false
                viewModel.stopService()
            },
            onDismiss = { showStopConfirm = false },
        )
    }
}

@Composable
private fun TestConnectionRow(
    testing: Boolean,
    testOk: Boolean?,
    testMessage: String,
    onTest: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onTest,
            enabled = !testing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (testing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                Text(context.getString(R.string.action_testing))
            } else {
                ButtonLabel(Icons.Outlined.CloudSync, context.getString(R.string.action_test))
            }
        }
        if (testOk != null && testMessage.isNotBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (testOk) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (testOk) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = testMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (testOk) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AppHeader(ui: UiState, sync: com.syncclipboard.mobile.sync.SyncSnapshot) {
    val context = LocalContext.current
    val running = sync.status == SyncStatus.CONNECTED || sync.status == SyncStatus.CONNECTING
    val host = ui.baseUrl.ifBlank { context.getString(R.string.status_not_configured) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = context.getString(R.string.header_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (running) {
                            context.getString(R.string.header_subtitle_running, host)
                        } else {
                            context.getString(R.string.header_subtitle_idle)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.CloudSync,
                    label = context.getString(R.string.pill_connection),
                    value = connectionLabel(context, sync.status),
                    active = sync.status == SyncStatus.CONNECTED,
                )
                StatusPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Download,
                    label = context.getString(R.string.pill_pull),
                    value = context.getString(if (ui.pullEnabled) R.string.pill_on else R.string.pill_off),
                    active = ui.pullEnabled,
                )
                StatusPill(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Upload,
                    label = context.getString(R.string.pill_push),
                    value = context.getString(if (ui.pushEnabled) R.string.pill_on else R.string.pill_off),
                    active = ui.pushEnabled,
                )
            }
        }
    }
}

private fun connectionLabel(context: android.content.Context, status: SyncStatus): String =
    when (status) {
        SyncStatus.CONNECTED -> context.getString(R.string.pill_on)
        SyncStatus.CONNECTING -> context.getString(R.string.status_connecting)
        SyncStatus.ERROR -> context.getString(R.string.state_missing)
        SyncStatus.STOPPED -> context.getString(R.string.pill_off)
    }

@Composable
private fun StatusPill(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    active: Boolean,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(status: SyncStatus, message: String, lastText: String) {
    val context = LocalContext.current
    if (status == SyncStatus.STOPPED && lastText.isBlank()) return

    val isError = status == SyncStatus.ERROR
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val label = when (status) {
                    SyncStatus.CONNECTED -> context.getString(R.string.status_connected)
                    SyncStatus.CONNECTING -> context.getString(R.string.status_connecting)
                    SyncStatus.ERROR -> context.getString(R.string.status_error, message)
                    SyncStatus.STOPPED -> context.getString(R.string.status_stopped)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
                if (lastText.isNotBlank()) {
                    Text(
                        text = context.getString(R.string.last_synced, lastText),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleCard(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun PermissionCard(
    label: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (granted) {
                        context.getString(R.string.state_granted)
                    } else {
                        context.getString(R.string.state_missing)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            if (!granted) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    text: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    lines: List<String>,
    icon: ImageVector = Icons.Outlined.Info,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmIcon: ImageVector = Icons.Outlined.Stop,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(title) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(onClick = onConfirm) {
                ButtonLabel(confirmIcon, confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.dialog_cancel))
            }
        },
    )
}

@Composable
private fun ButtonLabel(icon: ImageVector, text: String) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.size(8.dp))
    Text(text)
}

/**
 * Card for the optional Shizuku backend. Adapts its description and action button to the
 * current [availability]: install when absent, open when not running, grant when it needs
 * permission (or was permanently denied), and a passive "active" state once ready. The last
 * keep-alive result is surfaced when known so the user can see the privileged step worked.
 */
@Composable
private fun ShizukuCard(
    availability: ShizukuAvailability,
    permanentlyDenied: Boolean,
    keepAliveOk: Boolean?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ready = availability == ShizukuAvailability.READY
    val stateText = when (availability) {
        ShizukuAvailability.READY -> context.getString(R.string.shizuku_state_ready)
        ShizukuAvailability.NEEDS_PERMISSION ->
            if (permanentlyDenied) {
                context.getString(R.string.shizuku_state_denied)
            } else {
                context.getString(R.string.shizuku_state_needs_permission)
            }
        ShizukuAvailability.NOT_RUNNING -> context.getString(R.string.shizuku_state_not_running)
        ShizukuAvailability.NOT_INSTALLED -> context.getString(R.string.shizuku_state_not_installed)
    }
    val actionLabel = when (availability) {
        ShizukuAvailability.READY -> null
        ShizukuAvailability.NEEDS_PERMISSION -> context.getString(R.string.shizuku_action_grant)
        ShizukuAvailability.NOT_RUNNING -> context.getString(R.string.shizuku_action_open)
        ShizukuAvailability.NOT_INSTALLED -> context.getString(R.string.shizuku_action_install)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = context.getString(R.string.perm_shizuku),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (actionLabel != null) {
                    OutlinedButton(onClick = onAction) { Text(actionLabel) }
                }
            }
            if (keepAliveOk != null) {
                Text(
                    text = context.getString(
                        if (keepAliveOk) R.string.shizuku_keepalive_ok else R.string.shizuku_keepalive_failed,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (keepAliveOk) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
