package com.chloemlla.syncclipboard.mobile.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chloemlla.syncclipboard.mobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                        Icon(Icons.Outlined.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(context.getString(R.string.tools_title), fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = context.getString(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 24.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ui.error.isNotBlank()) {
                    StatusLine(text = ui.error, error = true)
                } else if (ui.status.isNotBlank()) {
                    StatusLine(text = ui.status, error = false)
                }

                ToolsSection(title = context.getString(R.string.tools_section_config), icon = Icons.Outlined.Save) {
                    OutlinedTextField(
                        value = ui.artifactBaseUrl,
                        onValueChange = viewModel::onArtifactBaseUrl,
                        label = { Text(context.getString(R.string.tools_label_artifact_base)) },
                        singleLine = true,
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ui.artifactToken,
                        onValueChange = viewModel::onArtifactToken,
                        label = { Text(context.getString(R.string.tools_label_artifact_token)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ui.imageBaseUrl,
                        onValueChange = viewModel::onImageBaseUrl,
                        label = { Text(context.getString(R.string.tools_label_image_base)) },
                        singleLine = true,
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ui.imageApiKey,
                        onValueChange = viewModel::onImageApiKey,
                        label = { Text(context.getString(R.string.tools_label_image_key)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ui.imageModel,
                        onValueChange = viewModel::onImageModel,
                        label = { Text(context.getString(R.string.tools_label_image_model)) },
                        singleLine = true,
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ui.ffmpegPath,
                        onValueChange = viewModel::onFfmpegPath,
                        label = { Text(context.getString(R.string.tools_label_ffmpeg_path)) },
                        supportingText = {
                            Text(
                                if (ui.ffmpegAvailable) {
                                    context.getString(R.string.tools_ffmpeg_found)
                                } else {
                                    context.getString(R.string.tools_ffmpeg_missing)
                                },
                            )
                        },
                        singleLine = true,
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = viewModel::saveConfig,
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(context.getString(R.string.tools_action_save_config))
                    }
                }

                ToolsSection(title = context.getString(R.string.tools_section_short), icon = Icons.Outlined.Link) {
                    OutlinedTextField(
                        value = ui.shortUrlInput,
                        onValueChange = viewModel::onShortUrlInput,
                        label = { Text(context.getString(R.string.tools_label_long_url)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = viewModel::createShortUrl,
                        enabled = !ui.busy,
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (ui.busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(context.getString(R.string.tools_action_shorten))
                    }
                    if (ui.shortUrlResult.isNotBlank()) {
                        ResultRow(value = ui.shortUrlResult, onCopy = { viewModel.copyText(ui.shortUrlResult) })
                    }
                }

                ToolsSection(title = context.getString(R.string.tools_section_artifact), icon = Icons.Outlined.Article) {
                    OutlinedTextField(
                        value = ui.artifactTitle,
                        onValueChange = viewModel::onArtifactTitle,
                        label = { Text(context.getString(R.string.tools_label_artifact_title)) },
                        singleLine = true,
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ui.artifactContent,
                        onValueChange = viewModel::onArtifactContent,
                        label = { Text(context.getString(R.string.tools_label_artifact_content)) },
                        minLines = 3,
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = viewModel::createArtifact,
                        enabled = !ui.busy,
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(context.getString(R.string.tools_action_create_artifact)) }
                    if (ui.artifactResult.isNotBlank()) {
                        ResultRow(value = ui.artifactResult, onCopy = { viewModel.copyText(ui.artifactResult) })
                    }
                }

                ToolsSection(title = context.getString(R.string.tools_section_image), icon = Icons.Outlined.Image) {
                    OutlinedTextField(
                        value = ui.imagePrompt,
                        onValueChange = viewModel::onImagePrompt,
                        label = { Text(context.getString(R.string.tools_label_image_prompt)) },
                        minLines = 2,
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = viewModel::generateImage,
                        enabled = !ui.busy,
                        shape = SyncPreferenceShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(context.getString(R.string.tools_action_generate_image)) }
                    if (ui.imageResult.isNotBlank()) {
                        ResultRow(value = ui.imageResult, onCopy = { viewModel.copyText(ui.imageResult) })
                    }
                }

                ToolsSection(title = context.getString(R.string.tools_section_media), icon = Icons.Outlined.Movie) {
                    Text(
                        text = if (ui.ffmpegAvailable) {
                            context.getString(R.string.tools_media_ready)
                        } else {
                            viewModel.mediaDisabledHint()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ToolsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SyncCardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = { content() },
            )
        }
    }
}

@Composable
private fun ResultRow(value: String, onCopy: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedButton(onClick = onCopy, shape = SyncPreferenceShape) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(context.getString(R.string.tools_action_copy))
        }
    }
}

@Composable
private fun StatusLine(text: String, error: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SyncBannerShape,
        color = if (error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}
