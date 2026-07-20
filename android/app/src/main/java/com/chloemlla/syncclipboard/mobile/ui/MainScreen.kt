package com.chloemlla.syncclipboard.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.chloemlla.syncclipboard.mobile.R
import com.chloemlla.syncclipboard.mobile.shizuku.ShizukuAvailability
import com.chloemlla.syncclipboard.mobile.sync.PermissionHelper
import com.chloemlla.syncclipboard.mobile.sync.SyncStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenOpenSourceNotice: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenTools: () -> Unit = {},
) {
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
        contentWindowInsets = WindowInsets.safeDrawing,
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
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = context.getString(R.string.action_open_history),
                        )
                    }
                    IconButton(onClick = onOpenTools) {
                        Icon(
                            imageVector = Icons.Outlined.Build,
                            contentDescription = context.getString(R.string.action_open_tools),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                .padding(PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 24.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    shape = SyncPreferenceShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ui.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text(context.getString(R.string.label_username)) },
                    singleLine = true,
                    shape = SyncPreferenceShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ui.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(context.getString(R.string.label_password)) },
                    singleLine = true,
                    shape = SyncPreferenceShape,
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
                    shape = SyncPreferenceShape,
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
                    text = context.getString(R.string.section_content),
                    subtitle = context.getString(R.string.section_content_subtitle),
                    icon = Icons.Outlined.Tune,
                )
                ToggleCard(
                    icon = Icons.Outlined.Upload,
                    label = context.getString(R.string.toggle_push_text),
                    checked = ui.enablePushText,
                    onChange = viewModel::onEnablePushTextChange,
                )
                ToggleCard(
                    icon = Icons.Outlined.Upload,
                    label = context.getString(R.string.toggle_push_image),
                    checked = ui.enablePushImage,
                    onChange = viewModel::onEnablePushImageChange,
                )
                ToggleCard(
                    icon = Icons.Outlined.Upload,
                    label = context.getString(R.string.toggle_push_file),
                    checked = ui.enablePushFile,
                    onChange = viewModel::onEnablePushFileChange,
                )
                ToggleCard(
                    icon = Icons.Outlined.Download,
                    label = context.getString(R.string.toggle_pull_image),
                    checked = ui.enablePullImage,
                    onChange = viewModel::onEnablePullImageChange,
                )
                ToggleCard(
                    icon = Icons.Outlined.Download,
                    label = context.getString(R.string.toggle_pull_file),
                    checked = ui.enablePullFile,
                    onChange = viewModel::onEnablePullFileChange,
                )
                OutlinedTextField(
                    value = ui.maxFileMb.toString(),
                    onValueChange = { v ->
                        viewModel.onMaxFileMbChange(v.toIntOrNull() ?: ui.maxFileMb)
                    },
                    label = { Text(context.getString(R.string.label_max_file_mb)) },
                    supportingText = { Text(context.getString(R.string.max_file_mb_hint)) },
                    singleLine = true,
                    shape = SyncPreferenceShape,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionTitle(
                    text = context.getString(R.string.section_assist),
                    subtitle = context.getString(R.string.section_assist_subtitle),
                    icon = Icons.Outlined.Info,
                )
                ToggleCard(
                    icon = Icons.Outlined.Download,
                    label = context.getString(R.string.toggle_download_web_image),
                    checked = ui.downloadWebImage,
                    onChange = viewModel::onDownloadWebImageChange,
                )
                ToggleCard(
                    icon = Icons.Outlined.Upload,
                    label = context.getString(R.string.toggle_easy_copy_image),
                    checked = ui.easyCopyImage,
                    onChange = viewModel::onEasyCopyImageChange,
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
                    text = context.getString(R.string.section_ignore),
                    subtitle = context.getString(R.string.section_ignore_subtitle),
                    icon = Icons.Outlined.FilterAlt,
                )
                SoftSurface {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (ui.foregroundPackage.isBlank()) {
                                context.getString(R.string.ignore_foreground_unknown)
                            } else {
                                context.getString(R.string.ignore_foreground_current, ui.foregroundPackage)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = ui.ignoreDraft,
                            onValueChange = viewModel::onIgnoreDraftChange,
                            label = { Text(context.getString(R.string.label_ignore_package)) },
                            placeholder = { Text(context.getString(R.string.ignore_package_placeholder)) },
                            singleLine = true,
                            shape = SyncPreferenceShape,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.addIgnorePackage() },
                                shape = SyncPreferenceShape,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(context.getString(R.string.action_add_ignore))
                            }
                            OutlinedButton(
                                onClick = viewModel::addCurrentForegroundPackage,
                                shape = SyncPreferenceShape,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(context.getString(R.string.action_add_current_app))
                            }
                        }
                        if (ui.ignorePackages.isEmpty()) {
                            Text(
                                text = context.getString(R.string.ignore_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            ui.ignorePackages.forEach { pkg ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Block,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = pkg,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(onClick = { viewModel.removeIgnorePackage(pkg) }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = context.getString(R.string.action_remove_ignore),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                SectionTitle(
                    text = context.getString(R.string.section_more),
                    subtitle = context.getString(R.string.section_more_subtitle),
                    icon = Icons.Outlined.History,
                )
                OutlinedButton(
                    onClick = onOpenHistory,
                    shape = SyncPreferenceShape,
                    modifier = Modifier.fillMaxWidth(),
                ) { ButtonLabel(Icons.Outlined.History, context.getString(R.string.action_open_history)) }
                OutlinedButton(
                    onClick = onOpenTools,
                    shape = SyncPreferenceShape,
                    modifier = Modifier.fillMaxWidth(),
                ) { ButtonLabel(Icons.Outlined.Build, context.getString(R.string.action_open_tools)) }

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
                        shape = SyncPreferenceShape,
                        modifier = Modifier.weight(1f),
                    ) { ButtonLabel(Icons.Outlined.PlayArrow, context.getString(R.string.action_start)) }
                    OutlinedButton(
                        onClick = { viewModel.applyAndRestart() },
                        shape = SyncPreferenceShape,
                        modifier = Modifier.weight(1f),
                    ) { ButtonLabel(Icons.Outlined.Refresh, context.getString(R.string.action_apply)) }
                }
                OutlinedButton(
                    onClick = { showStopConfirm = true },
                    shape = SyncPreferenceShape,
                    modifier = Modifier.fillMaxWidth(),
                ) { ButtonLabel(Icons.Outlined.Stop, context.getString(R.string.action_stop)) }


                SectionTitle(
                    text = context.getString(R.string.section_about),
                    subtitle = context.getString(R.string.section_about_subtitle),
                    icon = Icons.Outlined.Policy,
                )
                OutlinedButton(
                    onClick = onOpenOpenSourceNotice,
                    shape = SyncPreferenceShape,
                    modifier = Modifier.fillMaxWidth(),
                ) { ButtonLabel(Icons.Outlined.Policy, context.getString(R.string.action_open_oss_notice)) }

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
private fun SoftSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SyncCardShape,
        color = color,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = syncCardBorder(),
        content = content,
    )
}

@Composable
private fun LeadingIconChip(
    icon: ImageVector,
    active: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(SyncIconChipShape)
            .background(if (active) containerColor else MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (active) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
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
            shape = SyncPreferenceShape,
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
private fun AppHeader(ui: UiState, sync: com.chloemlla.syncclipboard.mobile.sync.SyncSnapshot) {
    val context = LocalContext.current
    val running = sync.status == SyncStatus.CONNECTED || sync.status == SyncStatus.CONNECTING
    val host = ui.baseUrl.ifBlank { context.getString(R.string.status_not_configured) }

    SoftSurface {
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
                        .size(48.dp)
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
        shape = SyncPillShape,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = null,
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
    SoftSurface(
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LeadingIconChip(
                icon = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                containerColor = if (isError) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                },
                contentColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                },
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
    SoftSurface {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingIconChip(icon = icon, active = checked)
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
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
    SoftSurface {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingIconChip(
                icon = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                active = true,
                containerColor = if (granted) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (granted) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
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
                OutlinedButton(
                    onClick = onAction,
                    shape = SyncPreferenceShape,
                ) { Text(actionLabel) }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            LeadingIconChip(icon = icon)
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
        shape = SyncCardShape,
        colors = syncCardColors(),
        elevation = syncCardElevation(),
        border = syncCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LeadingIconChip(icon = icon)
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
        shape = SyncCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
            Button(
                onClick = onConfirm,
                shape = SyncPreferenceShape,
            ) {
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

    SoftSurface(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LeadingIconChip(
                    icon = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.Security,
                    active = true,
                    containerColor = if (ready) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                    },
                    contentColor = if (ready) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = context.getString(R.string.perm_shizuku),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ready) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (actionLabel != null) {
                    OutlinedButton(
                        onClick = onAction,
                        shape = SyncPreferenceShape,
                    ) { Text(actionLabel) }
                }
            }
            if (keepAliveOk != null) {
                Text(
                    text = context.getString(
                        if (keepAliveOk) R.string.shizuku_keepalive_ok else R.string.shizuku_keepalive_failed,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (keepAliveOk) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

