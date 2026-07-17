package com.chloemlla.syncclipboard.mobile.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chloemlla.syncclipboard.mobile.R

enum class OpenSourceNoticeMode {
    FirstRun,
    Browse,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceNoticeScreen(
    mode: OpenSourceNoticeMode,
    onContinue: () -> Unit,
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showLicenseDialog by rememberSaveable { mutableStateOf(false) }
    val isFirstRun = mode == OpenSourceNoticeMode.FirstRun

    // First-run: system back exits the app path (finish), never skips into MainScreen.
    // Browse: back closes the notice.
    BackHandler {
        if (isFirstRun) {
            (context as? android.app.Activity)?.finish()
        } else {
            onClose?.invoke()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.oss_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (!isFirstRun && onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.oss_cd_close),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = {
                            if (isFirstRun) onContinue() else onClose?.invoke()
                        },
                        shape = SyncPreferenceShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .align(Alignment.CenterHorizontally),
                    ) {
                        Icon(
                            imageVector = if (isFirstRun) Icons.Outlined.CheckCircle else Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(
                                if (isFirstRun) R.string.oss_cta_continue else R.string.oss_cta_close,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 16.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeaderCard()

                NoticeCard(
                    icon = Icons.Outlined.Security,
                    title = stringResource(R.string.oss_free_title),
                    body = stringResource(R.string.oss_free_body),
                )

                NoticeCard(
                    icon = Icons.Outlined.Code,
                    title = stringResource(R.string.oss_source_title),
                    body = stringResource(R.string.oss_source_body),
                    actions = {
                        LinkButton(
                            label = stringResource(R.string.oss_link_fork),
                            url = OpenSourceCredits.FORK_REPO_URL,
                        )
                        LinkButton(
                            label = stringResource(R.string.oss_link_upstream),
                            url = OpenSourceCredits.UPSTREAM_REPO_URL,
                        )
                    },
                )

                NoticeCard(
                    icon = Icons.Outlined.Gavel,
                    title = stringResource(R.string.oss_license_title),
                    body = stringResource(
                        R.string.oss_license_body,
                        OpenSourceCredits.PROJECT_LICENSE,
                        OpenSourceCredits.PROJECT_COPYRIGHT,
                    ),
                    actions = {
                        OutlinedButton(
                            onClick = { showLicenseDialog = true },
                            shape = SyncPreferenceShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.oss_view_full_license))
                        }
                    },
                )

                SectionHeader(
                    icon = Icons.Outlined.VolunteerActivism,
                    title = stringResource(R.string.oss_credits_title),
                    subtitle = stringResource(R.string.oss_credits_subtitle),
                )

                OpenSourceCredits.dependencies.forEach { dep ->
                    DependencyCreditCard(dep)
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (showLicenseDialog) {
        val licenseText = remember {
            runCatching {
                context.resources.openRawResource(R.raw.project_license)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            }.getOrDefault(OpenSourceCredits.PROJECT_LICENSE)
        }
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            shape = SyncCardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text(stringResource(R.string.oss_license_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = licenseText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(stringResource(R.string.oss_cta_close))
                }
            },
        )
    }
}

@Composable
private fun HeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SyncCardShape,
        colors = syncCardColors(),
        elevation = syncCardElevation(),
        border = syncCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.oss_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.oss_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoticeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actions: (@Composable () -> Unit)? = null,
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
                Surface(
                    shape = SyncIconChipShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actions != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions()
                }
            }
        }
    }
}

@Composable
private fun DependencyCreditCard(dep: DependencyCredit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SyncCardShape,
        colors = syncCardColors(),
        elevation = syncCardElevation(),
        border = syncCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = dep.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.oss_dep_author_fmt, dep.author),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(dep.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.oss_dep_license_fmt, dep.license),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!dep.url.isNullOrBlank()) {
                TextButton(
                    onClick = { openUrl(context, dep.url) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.oss_open_link))
                }
            }
        }
    }
}

@Composable
private fun LinkButton(label: String, url: String) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = { openUrl(context, url) },
        shape = SyncPreferenceShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(label)
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
