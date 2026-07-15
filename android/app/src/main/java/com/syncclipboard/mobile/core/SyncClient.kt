package com.syncclipboard.mobile.core

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.UnknownHostException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * HTTP/WebDAV client for the SyncClipboard server protocol.
 *
 * Endpoints used:
 *  - GET/PUT  /SyncClipboard.json  (current clipboard profile)
 *  - GET/PUT  /file/{name}         (transfer data: large text or binary image)
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
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Probe the server with a lightweight authenticated GET. Throws [SyncException]
     * with a specific [SyncErrorKind] so the UI can give actionable guidance instead
     * of a raw stack message. Returns normally on success.
     */
    fun testConnection() {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment(PROFILE_PATH).build())
            .header("Authorization", authHeader)
            .get()
            .build()
        try {
            http.newCall(request).execute().use { resp ->
                when {
                    resp.isSuccessful -> return
                    resp.code == 401 || resp.code == 403 ->
                        throw SyncException(SyncErrorKind.AUTH, "HTTP ${resp.code}")
                    else -> throw SyncException(SyncErrorKind.SERVER, "HTTP ${resp.code}")
                }
            }
        } catch (e: SyncException) {
            throw e
        } catch (e: Exception) {
            throw e.asSyncException()
        }
    }

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
        val bytes = getFileBytes(dataName)
        return bytes.toString(Charsets.UTF_8)
    }

    /** PUT /file/{name} — upload BOM-less UTF-8 bytes for large text. */
    fun putFileText(dataName: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8) // Kotlin UTF-8 has no BOM
        putFileBytes(dataName, bytes)
    }

    /** GET /file/{name} as raw bytes (images / transfer blobs). */
    fun getFileBytes(dataName: String): ByteArray {
        val request = Request.Builder()
            .url(fileUrl(dataName))
            .header("Authorization", authHeader)
            .get()
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GET file failed: HTTP ${resp.code}")
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }

    /** PUT /file/{name} raw bytes (images / transfer blobs). */
    fun putFileBytes(dataName: String, bytes: ByteArray) {
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
     * Push an image: upload raw bytes first, then PUT an Image profile whose
     * hash matches desktop FileProfile (fileName|contentSha256).
     */
    fun pushImage(fileName: String, contentBytes: ByteArray) {
        if (contentBytes.isEmpty()) throw IOException("empty image")
        putFileBytes(fileName, contentBytes)
        putProfile(ImageSync.profile(fileName, contentBytes))
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

    /**
     * Download image transfer bytes for an Image profile. Returns null when the
     * profile is not an image or has no dataName.
     */
    fun resolveImageBytes(profile: ProfileDto): ByteArray? {
        if (profile.type != ProfileDto.TYPE_IMAGE) return null
        val name = profile.dataName?.takeIf { it.isNotBlank() } ?: profile.text.takeIf { it.isNotBlank() }
        if (name.isNullOrBlank()) return null
        return getFileBytes(name)
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

/** Coarse cause of a sync failure, used to surface actionable UI guidance. */
enum class SyncErrorKind { URL, AUTH, UNREACHABLE, TIMEOUT, TLS, SERVER, UNKNOWN }

/** A sync failure carrying a classified [kind] plus the raw detail for logging. */
class SyncException(
    val kind: SyncErrorKind,
    detail: String? = null,
    cause: Throwable? = null,
) : IOException(detail, cause)

/** Best-effort classification of an arbitrary throwable into a [SyncException]. */
fun Throwable.asSyncException(): SyncException = when (this) {
    is SyncException -> this
    is IllegalArgumentException -> SyncException(SyncErrorKind.URL, message, this)
    is UnknownHostException -> SyncException(SyncErrorKind.UNREACHABLE, message, this)
    is ConnectException -> SyncException(SyncErrorKind.UNREACHABLE, message, this)
    is SocketTimeoutException -> SyncException(SyncErrorKind.TIMEOUT, message, this)
    is SSLException -> SyncException(SyncErrorKind.TLS, message, this)
    else -> SyncException(SyncErrorKind.UNKNOWN, message, this)
}
