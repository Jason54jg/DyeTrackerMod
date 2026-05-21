package com.dyetracker.overlay

// OverlayDownloader is the security boundary for arbitrary http(s) URLs supplied via
// `/dyetracker gif add <url>`. Every validation rule (scheme allowlist, byte cap,
// content-type prefix) lives here. Anything past this file may assume the file on
// disk is "an image under 10 MB."
//
// SSRF note: this is a client-side mod, so requests originate from the player's own
// machine. A player who pastes `http://127.0.0.1` or `http://192.168.x.x/router-admin`
// is attacking their own LAN — the scheme allowlist + size cap deliberately do not
// block private/loopback ranges (a player legitimately running a LAN image host should
// work). If a future feature ever lets one player paste a URL on behalf of another
// (e.g. shared overlays), revisit and add a private-range deny-list + redirect
// re-validation.

import com.dyetracker.DyeTrackerMod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads images for HUD overlays. Validates URL scheme, byte size, and content type
 * before persisting to disk. Returns a typed [Result] on success or
 * [OverlayDownloadException] on failure so callers can map to user-facing chat messages.
 */
object OverlayDownloader {

    private const val MAX_BYTES: Long = 10L * 1024 * 1024
    private const val GIF_CACHE_DIR_NAME = "dyetracker"
    private const val GIF_SUBDIR_NAME = "gifs"
    private const val INDEX_FILE_NAME = "index.json"
    private const val CACHE_FILE_EXTENSION = ".bin"
    private const val SHA256_ALGORITHM = "SHA-256"
    private const val CONTENT_TYPE_HEADER = "Content-Type"
    private const val CONTENT_LENGTH_HEADER = "Content-Length"
    private const val IMAGE_CONTENT_TYPE_PREFIX = "image/"
    private const val USER_AGENT_HEADER = "User-Agent"
    private const val USER_AGENT_VALUE = "DyeTrackerMod (+https://github.com/stwalsh4118/dye-tracker)"
    private const val HTTP_OK = 200
    private const val STREAM_BUFFER_SIZE = 8 * 1024
    private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
    private val READ_TIMEOUT: Duration = Duration.ofSeconds(10)
    private val ALLOWED_SCHEMES = setOf("http", "https")

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val indexLock = Any()
    private var indexCache: ConcurrentHashMap<String, IndexEntry>? = null

    /** A downloaded image file on disk. */
    data class CachedImage(
        val path: Path,
        val contentType: String,
        val originalUrl: String,
    )

    /** Why a download failed. Surfaced to chat by [OverlayDownloadException]. */
    sealed class Reason {
        data class InvalidUrl(val url: String) : Reason()
        data class UnsupportedScheme(val scheme: String?) : Reason()
        data object TooLarge : Reason()
        data class UnsupportedContentType(val contentType: String?) : Reason()
        data class NetworkError(val cause: Throwable) : Reason()
        data object Timeout : Reason()
    }

    /** Throwable wrapper for a [Reason] so we can fit in `Result.failure`. */
    class OverlayDownloadException(val reason: Reason) :
        RuntimeException(reasonMessage(reason))

    @Serializable
    private data class IndexEntry(
        val filename: String,
        val contentType: String,
        val downloadedAtMs: Long,
    )

    /**
     * Download [url] to disk and return a [CachedImage], or fail with
     * [OverlayDownloadException] wrapping a [Reason]. Runs on [Dispatchers.IO].
     */
    suspend fun download(url: String): Result<CachedImage> = withContext(Dispatchers.IO) {
        try {
            Result.success(downloadBlocking(url.trim()))
        } catch (e: OverlayDownloadException) {
            DyeTrackerMod.warn("GIF download failed for url={}: {}", url, e.message ?: e.reason)
            Result.failure(e)
        } catch (e: Throwable) {
            DyeTrackerMod.error("GIF download unexpected error for url=$url", e)
            Result.failure(OverlayDownloadException(Reason.NetworkError(e)))
        }
    }

    private fun downloadBlocking(url: String): CachedImage {
        val uri = parseAndValidateUrl(url)
        val sha = sha256Hex(url)
        val finalPath = cacheDir().resolve(sha + CACHE_FILE_EXTENSION)
        val index = loadIndex()

        index[url]?.let { entry ->
            if (Files.exists(finalPath)) {
                DyeTrackerMod.info("GIF cache hit for {} -> {}", url, entry.filename)
                return CachedImage(finalPath, entry.contentType, url)
            }
            // Stale index entry; fall through to re-download.
        }

        val started = System.nanoTime()
        val response = tryHead(uri)
        val headValidatedSize: Long? = if (response != null && response.statusCode() == HTTP_OK) {
            val ct = response.headers().firstValue(CONTENT_TYPE_HEADER).orElse(null)
            validateContentType(ct)
            val len = response.headers().firstValueAsLong(CONTENT_LENGTH_HEADER)
            if (len.isPresent) {
                val l = len.asLong
                if (l > MAX_BYTES) throw OverlayDownloadException(Reason.TooLarge)
                l
            } else {
                null
            }
        } else {
            null
        }

        // Stream GET with a hard byte cap regardless of HEAD outcome.
        val getRequest = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(READ_TIMEOUT)
            .header(USER_AGENT_HEADER, USER_AGENT_VALUE)
            .build()

        val streamResponse = try {
            httpClient.send(getRequest, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: java.net.http.HttpTimeoutException) {
            throw OverlayDownloadException(Reason.Timeout)
        } catch (e: IOException) {
            throw OverlayDownloadException(Reason.NetworkError(e))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw OverlayDownloadException(Reason.NetworkError(e))
        }

        if (streamResponse.statusCode() != HTTP_OK) {
            try { streamResponse.body().close() } catch (_: IOException) { /* best-effort */ }
            throw OverlayDownloadException(
                Reason.NetworkError(IOException("HTTP ${streamResponse.statusCode()}"))
            )
        }

        val contentType = streamResponse.headers().firstValue(CONTENT_TYPE_HEADER).orElse(null)
        validateContentType(contentType)

        val streamLen = streamResponse.headers().firstValueAsLong(CONTENT_LENGTH_HEADER)
        if (streamLen.isPresent && streamLen.asLong > MAX_BYTES) {
            streamResponse.body().close()
            throw OverlayDownloadException(Reason.TooLarge)
        }

        val tempPath = Files.createTempFile(cacheDir(), sha, ".part")
        val bytesWritten: Long
        try {
            bytesWritten = streamResponse.body().use { input ->
                Files.newOutputStream(tempPath).use { output ->
                    copyWithCap(input, output, MAX_BYTES)
                }
            }
        } catch (e: OverlayDownloadException) {
            Files.deleteIfExists(tempPath)
            throw e
        } catch (e: IOException) {
            Files.deleteIfExists(tempPath)
            throw OverlayDownloadException(Reason.NetworkError(e))
        }

        try {
            try {
                Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            Files.deleteIfExists(tempPath)
            throw OverlayDownloadException(Reason.NetworkError(e))
        }

        // contentType is guaranteed non-null because validateContentType throws otherwise.
        val resolvedContentType = contentType!!
        index[url] = IndexEntry(finalPath.fileName.toString(), resolvedContentType, Instant.now().toEpochMilli())
        saveIndex(index)

        val tookMs = (System.nanoTime() - started) / 1_000_000
        DyeTrackerMod.info(
            "GIF downloaded url={} bytes={} headLen={} type={} ms={}",
            url, bytesWritten, headValidatedSize, resolvedContentType, tookMs
        )

        return CachedImage(finalPath, resolvedContentType, url)
    }

    /** Try a `HEAD` request; return null if the server rejects HEAD (e.g. 405). */
    private fun tryHead(uri: URI): HttpResponse<Void>? {
        val req = HttpRequest.newBuilder(uri)
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .timeout(READ_TIMEOUT)
            .header(USER_AGENT_HEADER, USER_AGENT_VALUE)
            .build()
        return try {
            httpClient.send(req, HttpResponse.BodyHandlers.discarding())
        } catch (e: java.net.http.HttpTimeoutException) {
            throw OverlayDownloadException(Reason.Timeout)
        } catch (e: IOException) {
            // HEAD is best-effort; the server may not support it.
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw OverlayDownloadException(Reason.NetworkError(e))
        }
    }

    private fun parseAndValidateUrl(url: String): URI {
        if (url.isBlank()) throw OverlayDownloadException(Reason.InvalidUrl(url))
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            throw OverlayDownloadException(Reason.InvalidUrl(url))
        }
        if (uri.host.isNullOrBlank()) throw OverlayDownloadException(Reason.InvalidUrl(url))
        val scheme = uri.scheme?.lowercase()
        if (scheme == null || scheme !in ALLOWED_SCHEMES) {
            throw OverlayDownloadException(Reason.UnsupportedScheme(scheme))
        }
        return uri
    }

    private fun validateContentType(contentType: String?) {
        val lower = contentType?.lowercase()?.substringBefore(';')?.trim()
        if (lower == null || !lower.startsWith(IMAGE_CONTENT_TYPE_PREFIX)) {
            throw OverlayDownloadException(Reason.UnsupportedContentType(contentType))
        }
    }

    private fun copyWithCap(input: java.io.InputStream, output: java.io.OutputStream, cap: Long): Long {
        val buf = ByteArray(STREAM_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buf)
            if (read < 0) break
            total += read
            if (total > cap) throw OverlayDownloadException(Reason.TooLarge)
            output.write(buf, 0, read)
        }
        return total
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance(SHA256_ALGORITHM).digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            // Mask sign-extension; %02x on a signed Byte produces "ffffff80"-style strings.
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    /** Cache directory `<FabricLoader.configDir>/dyetracker/gifs/`. */
    private fun cacheDir(): Path {
        val dir = FabricLoader.getInstance().configDir
            .resolve(GIF_CACHE_DIR_NAME)
            .resolve(GIF_SUBDIR_NAME)
        if (!Files.exists(dir)) Files.createDirectories(dir)
        return dir
    }

    private fun indexPath(): Path = cacheDir().resolve(INDEX_FILE_NAME)

    private fun loadIndex(): ConcurrentHashMap<String, IndexEntry> {
        synchronized(indexLock) {
            indexCache?.let { return it }
            val path = indexPath()
            val map: ConcurrentHashMap<String, IndexEntry> = if (Files.exists(path)) {
                try {
                    val text = Files.readString(path)
                    ConcurrentHashMap(json.decodeFromString<Map<String, IndexEntry>>(text))
                } catch (e: Exception) {
                    DyeTrackerMod.warn("Failed to read GIF cache index, starting fresh: {}", e.message)
                    ConcurrentHashMap()
                }
            } else {
                ConcurrentHashMap()
            }
            indexCache = map
            return map
        }
    }

    /** Persist [map] atomically — write to .tmp and `Files.move` into place. */
    private fun saveIndex(map: ConcurrentHashMap<String, IndexEntry>) {
        synchronized(indexLock) {
            try {
                val snapshot = HashMap(map) // stable view for serialization
                val text = json.encodeToString<Map<String, IndexEntry>>(snapshot)
                val tmp = indexPath().resolveSibling("${INDEX_FILE_NAME}.tmp")
                Files.writeString(tmp, text)
                try {
                    Files.move(tmp, indexPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(tmp, indexPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: IOException) {
                DyeTrackerMod.error("Failed to persist GIF cache index", e)
            }
        }
    }

}

private fun reasonMessage(reason: OverlayDownloader.Reason): String = when (reason) {
    is OverlayDownloader.Reason.InvalidUrl -> "invalid URL: ${reason.url}"
    is OverlayDownloader.Reason.UnsupportedScheme -> "unsupported scheme: ${reason.scheme ?: "(none)"}"
    is OverlayDownloader.Reason.UnsupportedContentType -> "unsupported content type: ${reason.contentType ?: "(none)"}"
    OverlayDownloader.Reason.TooLarge -> "image exceeds 10 MB size cap"
    OverlayDownloader.Reason.Timeout -> "request timed out"
    is OverlayDownloader.Reason.NetworkError -> "network error: ${reason.cause.javaClass.simpleName}: ${reason.cause.message ?: "(no message)"}"
}
