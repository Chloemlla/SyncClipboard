package com.syncclipboard.mobile.core

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP/WebDAV client for the SyncClipboard server protocol.
 *
 * Endpoints used:
 *  - GET/PUT  /SyncClipboard.json  (current clipboard profile)
 *  - GET/PUT  /file/{name}         (large-text transfer data)
 *
 * Auth is HTTP Basic (base64(user:password), UTF-8). The built-in LAN server
 * serves plain HTTP by default, so callers should treat traffic as unencrypted
 * unless the server has HTTPS enabled.
 */
class SyncClient(config: ServerConfig) {

    private val baseUrl: HttpUrl = normalizeBaseUrl(config.baseUrl)
    private val authHeader: String = Credentials.basic(config.username, config.password, Charsets.UTF_8)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    /** GET /SyncClipboard.json */
    fun getProfile(): ProfileDto {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment(PROFILE_PATH).build())
            .header("Authorization", authHeader)
            .get()
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GET profile failed: HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return ProfileDto()
            return ProfileDto.fromJson(body)
        }
    }

    /** PUT /SyncClipboard.json */
    fun putProfile(profile: ProfileDto) {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment(PROFILE_PATH).build())
            .header("Authorization", authHeader)
            .put(profile.toJson().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val detail = resp.body?.string().orEmpty()
                throw IOException("PUT profile failed: HTTP ${resp.code} $detail")
            }
        }
    }

    /** GET /file/{name} — full UTF-8 text for a large-text profile. */
    fun getFileText(dataName: String): String {
        val request = Request.Builder()
            .url(fileUrl(dataName))
            .header("Authorization", authHeader)
            .get()
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GET file failed: HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    /** PUT /file/{name} — upload BOM-less UTF-8 bytes for large text. */
    fun putFileText(dataName: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8) // Kotlin UTF-8 has no BOM
        val request = Request.Builder()
            .url(fileUrl(dataName))
            .header("Authorization", authHeader)
            .put(bytes.toRequestBody(OCTET_MEDIA))
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("PUT file failed: HTTP ${resp.code}")
        }
    }

    /**
     * Push text, transparently handling the large-text (>10240 chars) path:
     * upload the file first, then PUT the profile referencing it.
     */
    fun pushText(content: String) {
        if (TextSync.isInline(content)) {
            putProfile(ProfileDto.text(content))
            return
        }
        val dataName = TextSync.buildDataName()
        putFileText(dataName, content)
        putProfile(
            ProfileDto(
                type = ProfileDto.TYPE_TEXT,
                hash = HashUtil.sha256UpperHex(content), // hash over full UTF-8 file bytes
                text = TextSync.preview(content),
                hasData = true,
                dataName = dataName,
                size = content.length.toLong(),
            ),
        )
    }

    /**
     * Resolve the full text of a text profile, downloading the transfer file
     * when [ProfileDto.hasData] is set. Returns null for non-text profiles.
     */
    fun resolveText(profile: ProfileDto): String? {
        if (profile.type != ProfileDto.TYPE_TEXT) return null
        val name = profile.dataName
        return if (profile.hasData && !name.isNullOrBlank()) getFileText(name) else profile.text
    }

    private fun fileUrl(dataName: String): HttpUrl =
        baseUrl.newBuilder()
            .addPathSegment(FILE_FOLDER)
            .addPathSegment(dataName)
            .build()

    companion object {
        private const val PROFILE_PATH = "SyncClipboard.json"
        private const val FILE_FOLDER = "file"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val OCTET_MEDIA = "application/octet-stream".toMediaType()

        fun normalizeBaseUrl(raw: String): HttpUrl {
            val trimmed = raw.trim().trimEnd('/')
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
            return withScheme.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Invalid server URL: $raw")
        }
    }
}
