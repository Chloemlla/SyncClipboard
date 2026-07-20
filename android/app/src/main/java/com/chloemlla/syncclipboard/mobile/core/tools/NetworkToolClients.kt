package com.chloemlla.syncclipboard.mobile.core.tools

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ShortUrlResult(
    val originalUrl: String,
    val shortUrl: String,
)

data class ArtifactCreateResult(
    val shortId: String,
    val url: String,
    val title: String?,
)

data class ImageGenerationResult(
    val imageUrls: List<String>,
    val rawResponse: String,
)

/**
 * Kotlin ports of winui [MmpShortUrlClient] / [NexaiArtifactsClient] /
 * [OpenAiImageGenerationClient].
 */
class NetworkToolClients(
    private val http: OkHttpClient = defaultClient(),
) {
    fun createShortUrl(longUrl: String): ShortUrlResult {
        val trimmed = longUrl.trim()
        requireHttpUrl(trimmed)
        val requestUri = "https://api.mmp.cc/api/dwz?longurl=" +
            java.net.URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        val response = http.newCall(Request.Builder().url(requestUri).get().build()).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("Short URL API failed: HTTP ${response.code}")
        }
        val doc = JSONObject(body.ifBlank { "{}" })
        val status = doc.optInt("status", -1)
        val shortUrl = doc.optString("shorturl", "")
        if (status == 200 && shortUrl.isNotBlank()) {
            return ShortUrlResult(originalUrl = trimmed, shortUrl = shortUrl)
        }
        val message = doc.optString("msg").ifBlank { "Short URL API returned an unexpected payload." }
        throw IllegalStateException(message)
    }

    fun createArtifact(
        backendBaseUrl: String,
        accessToken: String,
        title: String,
        content: String,
        contentType: String = "text",
        language: String? = null,
    ): ArtifactCreateResult {
        if (accessToken.isBlank()) {
            throw IllegalStateException("Sign in before creating artifacts.")
        }
        val base = backendBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) {
            throw IllegalStateException("Artifact backend base URL is required.")
        }
        val url = "$base/artifacts"
        val payload = JSONObject().apply {
            put("title", title.trim().ifBlank { "SyncClipboard Artifact" })
            put("content_type", contentType.ifBlank { "text" })
            put(
                "content",
                // Keep pure-JVM (unit tests) and Android compatible without android.util.Base64.
                encodeBase64(content.toByteArray(Charsets.UTF_8)),
            )
            put("visibility", "public")
            if (!language.isNullOrBlank()) put("language", language)
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${accessToken.trim()}")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        val response = http.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        val doc = JSONObject(body.ifBlank { "{}" })
        if (response.code !in listOf(200, 201)) {
            val err = doc.optString("error").ifBlank { "HTTP ${response.code}" }
            throw IllegalStateException(err)
        }
        val data = doc.optJSONObject("data")
            ?: throw IllegalStateException("Artifact API returned no data.")
        val shortId = data.optString("shortId")
            .ifBlank { data.optString("short_id") }
            .ifBlank { data.optString("id") }
        val resultUrl = data.optString("url")
            .ifBlank { data.optString("shareUrl") }
            .ifBlank {
                if (shortId.isNotBlank()) "$base/artifacts/$shortId" else ""
            }
        if (shortId.isBlank() && resultUrl.isBlank()) {
            throw IllegalStateException("Artifact API response missing short id/url.")
        }
        return ArtifactCreateResult(
            shortId = shortId,
            url = resultUrl,
            title = data.optString("title").ifBlank { title },
        )
    }

    fun generateImage(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
    ): ImageGenerationResult {
        if (prompt.isBlank()) throw IllegalStateException("Prompt is required.")
        val base = baseUrl.trim().trimEnd('/')
        if (base.isBlank()) throw IllegalStateException("Image API base URL is required.")
        val endpoint = "$base/images/generations"
        val payload = JSONObject().apply {
            put("model", model.ifBlank { "dall-e-3" })
            put("prompt", prompt)
            put("size", "1024x1024")
            put("response_format", "url")
            put("n", 1)
        }
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }
        val response = http.newCall(requestBuilder.build()).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            return generateViaChatFallback(base, apiKey, model, prompt, body)
        }
        val urls = parseImageUrls(body)
        if (urls.isEmpty()) throw IllegalStateException("Image API returned no image URLs.")
        return ImageGenerationResult(imageUrls = urls, rawResponse = body)
    }

    private fun generateViaChatFallback(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        primaryErrorBody: String,
    ): ImageGenerationResult {
        val endpoint = "$baseUrl/chat/completions"
        val payload = JSONObject().apply {
            put("model", model.ifBlank { "dall-e-3" })
            put("stream", false)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", prompt),
                ),
            )
        }
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }
        val response = http.newCall(requestBuilder.build()).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException(
                "Image generation failed. images/generations error + chat fallback " +
                    "HTTP ${response.code}. ${trimBody(primaryErrorBody)}",
            )
        }
        val doc = JSONObject(body.ifBlank { "{}" })
        val content = runCatching {
            doc.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }.getOrDefault(body)
        val urls = extractUrls(content)
        if (urls.isEmpty()) {
            throw IllegalStateException("No image URL found in chat fallback response.")
        }
        return ImageGenerationResult(imageUrls = urls, rawResponse = content)
    }

    private fun parseImageUrls(body: String): List<String> {
        val doc = JSONObject(body.ifBlank { "{}" })
        val data = doc.optJSONArray("data") ?: return emptyList()
        val urls = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val url = item.optString("url")
            if (url.isNotBlank()) {
                urls += url
            } else {
                val b64 = item.optString("b64_json")
                if (b64.isNotBlank()) urls += "data:image/png;base64,$b64"
            }
        }
        return urls
    }

    private fun extractUrls(content: String): List<String> {
        val matcher = URL_PATTERN.matcher(content)
        val found = linkedSetOf<String>()
        while (matcher.find()) {
            found += matcher.group()
        }
        return found.toList()
    }

    private fun requireHttpUrl(value: String) {
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw IllegalStateException("Please provide a valid http(s) URL.")
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IllegalStateException("Please provide a valid http(s) URL.")
        }
        if (uri.host.isNullOrBlank()) {
            throw IllegalStateException("Please provide a valid http(s) URL.")
        }
    }

    private fun trimBody(value: String): String {
        val compact = value.replace(Regex("\\s+"), " ").trim()
        return if (compact.length <= 240) compact else compact.take(240) + "…"
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val URL_PATTERN: Pattern =
            Pattern.compile("""https?://[^\s\)\]"']+""")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        /** RFC 4648 base64 without line breaks (same as java.util.Base64.getEncoder()). */
        internal fun encodeBase64(bytes: ByteArray): String {
            val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val out = StringBuilder((bytes.size + 2) / 3 * 4)
            var i = 0
            while (i + 2 < bytes.size) {
                val n = ((bytes[i].toInt() and 0xFF) shl 16) or
                    ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                    (bytes[i + 2].toInt() and 0xFF)
                out.append(table[(n shr 18) and 63])
                out.append(table[(n shr 12) and 63])
                out.append(table[(n shr 6) and 63])
                out.append(table[n and 63])
                i += 3
            }
            val rem = bytes.size - i
            if (rem == 1) {
                val n = (bytes[i].toInt() and 0xFF) shl 16
                out.append(table[(n shr 18) and 63])
                out.append(table[(n shr 12) and 63])
                out.append("==")
            } else if (rem == 2) {
                val n = ((bytes[i].toInt() and 0xFF) shl 16) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                out.append(table[(n shr 18) and 63])
                out.append(table[(n shr 12) and 63])
                out.append(table[(n shr 6) and 63])
                out.append('=')
            }
            return out.toString()
        }
    }
}

/**
 * Optional media tools (ffmpeg). Android does not bundle ffmpeg; when the binary
 * is missing we surface a localized disabled message instead of failing silently.
 */
object FfmpegMediaSupport {
    fun resolveFfmpegPath(configured: String?): String? {
        val explicit = configured?.trim().orEmpty()
        if (explicit.isNotEmpty()) {
            val file = java.io.File(explicit)
            if (file.isFile && file.canExecute()) return file.absolutePath
            // Some OEMs return canExecute=false for user-provided binaries; still try if exists.
            if (file.isFile) return file.absolutePath
        }
        val candidates = listOf(
            "ffmpeg",
            "/system/bin/ffmpeg",
            "/system/xbin/ffmpeg",
            "/data/local/tmp/ffmpeg",
        )
        for (c in candidates) {
            if (c == "ffmpeg") {
                // PATH lookup is not reliable on Android; skip unless configured.
                continue
            }
            val f = java.io.File(c)
            if (f.isFile) return f.absolutePath
        }
        return null
    }

    fun isAvailable(configured: String?): Boolean = resolveFfmpegPath(configured) != null
}
