package com.chloemlla.syncclipboard.mobile.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chloemlla.syncclipboard.mobile.R
import com.chloemlla.syncclipboard.mobile.core.ToolsConfig
import com.chloemlla.syncclipboard.mobile.core.ToolsStore
import com.chloemlla.syncclipboard.mobile.core.tools.FfmpegMediaSupport
import com.chloemlla.syncclipboard.mobile.core.tools.NetworkToolClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ToolsUiState(
    val artifactBaseUrl: String = "",
    val artifactToken: String = "",
    val imageBaseUrl: String = "",
    val imageApiKey: String = "",
    val imageModel: String = ToolsConfig.DEFAULT_IMAGE_MODEL,
    val ffmpegPath: String = "",
    val ffmpegAvailable: Boolean = false,
    val shortUrlInput: String = "",
    val shortUrlResult: String = "",
    val artifactTitle: String = "",
    val artifactContent: String = "",
    val artifactResult: String = "",
    val imagePrompt: String = "",
    val imageResult: String = "",
    val busy: Boolean = false,
    val error: String = "",
    val status: String = "",
)

class ToolsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ToolsStore(app)
    private val clients = NetworkToolClients()

    private val _ui = MutableStateFlow(loadInitial())
    val ui: StateFlow<ToolsUiState> = _ui.asStateFlow()

    private fun loadInitial(): ToolsUiState {
        val cfg = store.load()
        return ToolsUiState(
            artifactBaseUrl = cfg.artifactBaseUrl,
            artifactToken = cfg.artifactToken,
            imageBaseUrl = cfg.imageBaseUrl,
            imageApiKey = cfg.imageApiKey,
            imageModel = cfg.imageModel,
            ffmpegPath = cfg.ffmpegPath,
            ffmpegAvailable = FfmpegMediaSupport.isAvailable(cfg.ffmpegPath),
        )
    }

    fun onArtifactBaseUrl(v: String) = update { it.copy(artifactBaseUrl = v) }
    fun onArtifactToken(v: String) = update { it.copy(artifactToken = v) }
    fun onImageBaseUrl(v: String) = update { it.copy(imageBaseUrl = v) }
    fun onImageApiKey(v: String) = update { it.copy(imageApiKey = v) }
    fun onImageModel(v: String) = update { it.copy(imageModel = v) }
    fun onFfmpegPath(v: String) = update {
        it.copy(
            ffmpegPath = v,
            ffmpegAvailable = FfmpegMediaSupport.isAvailable(v),
        )
    }
    fun onShortUrlInput(v: String) = update { it.copy(shortUrlInput = v) }
    fun onArtifactTitle(v: String) = update { it.copy(artifactTitle = v) }
    fun onArtifactContent(v: String) = update { it.copy(artifactContent = v) }
    fun onImagePrompt(v: String) = update { it.copy(imagePrompt = v) }

    fun saveConfig() {
        val u = _ui.value
        store.save(
            ToolsConfig(
                artifactBaseUrl = u.artifactBaseUrl,
                artifactToken = u.artifactToken,
                imageBaseUrl = u.imageBaseUrl,
                imageApiKey = u.imageApiKey,
                imageModel = u.imageModel,
                ffmpegPath = u.ffmpegPath,
            ),
        )
        update {
            it.copy(
                status = string(R.string.tools_msg_config_saved),
                error = "",
                ffmpegAvailable = FfmpegMediaSupport.isAvailable(u.ffmpegPath),
            )
        }
    }

    fun createShortUrl() {
        val input = _ui.value.shortUrlInput.trim()
        if (input.isEmpty()) {
            update { it.copy(error = string(R.string.tools_err_url_required)) }
            return
        }
        runTool {
            val result = clients.createShortUrl(input)
            update {
                it.copy(
                    shortUrlResult = result.shortUrl,
                    status = string(R.string.tools_msg_short_ok),
                    error = "",
                )
            }
        }
    }

    fun createArtifact() {
        val u = _ui.value
        if (u.artifactBaseUrl.isBlank() || u.artifactToken.isBlank()) {
            update { it.copy(error = string(R.string.tools_err_artifact_config)) }
            return
        }
        if (u.artifactContent.isBlank()) {
            update { it.copy(error = string(R.string.tools_err_artifact_content)) }
            return
        }
        runTool {
            val result = clients.createArtifact(
                backendBaseUrl = u.artifactBaseUrl,
                accessToken = u.artifactToken,
                title = u.artifactTitle,
                content = u.artifactContent,
            )
            update {
                it.copy(
                    artifactResult = result.url.ifBlank { result.shortId },
                    status = string(R.string.tools_msg_artifact_ok),
                    error = "",
                )
            }
        }
    }

    fun generateImage() {
        val u = _ui.value
        if (u.imageBaseUrl.isBlank()) {
            update { it.copy(error = string(R.string.tools_err_image_config)) }
            return
        }
        if (u.imagePrompt.isBlank()) {
            update { it.copy(error = string(R.string.tools_err_image_prompt)) }
            return
        }
        runTool {
            val result = clients.generateImage(
                baseUrl = u.imageBaseUrl,
                apiKey = u.imageApiKey,
                model = u.imageModel.ifBlank { ToolsConfig.DEFAULT_IMAGE_MODEL },
                prompt = u.imagePrompt,
            )
            val first = result.imageUrls.firstOrNull().orEmpty()
            update {
                it.copy(
                    imageResult = first,
                    status = string(R.string.tools_msg_image_ok),
                    error = "",
                )
            }
        }
    }

    fun mediaDisabledHint(): String = string(R.string.tools_media_disabled)

    fun copyText(value: String): Boolean {
        if (value.isBlank()) return false
        val cm = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        return runCatching {
            cm.setPrimaryClip(ClipData.newPlainText("SyncClipboard Tools", value))
            update { it.copy(status = string(R.string.tools_msg_copied), error = "") }
            true
        }.getOrDefault(false)
    }

    private fun runTool(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        update { it.copy(busy = true, error = "", status = "") }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (t: Throwable) {
                update {
                    it.copy(
                        error = t.message?.takeIf { m -> m.isNotBlank() }
                            ?: string(R.string.tools_err_generic),
                        status = "",
                    )
                }
            } finally {
                update { it.copy(busy = false) }
            }
        }
    }

    private fun string(id: Int): String = getApplication<Application>().getString(id)

    private fun update(block: (ToolsUiState) -> ToolsUiState) {
        _ui.value = block(_ui.value)
    }
}
