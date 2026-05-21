package com.dyetracker.overlay

import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Shared download → decode → upload → persist pipeline for adding a new GIF/image
 * overlay from a URL. Used by both `/dyetracker gif add` (chat-driven) and the in-screen
 * "+ Add overlay" panel inside [EditOverlaysScreen]. Callers pick how progress + result
 * are surfaced (chat formatting vs inline UI strings); the pipeline itself only does
 * the work and reports stages.
 *
 * All steps are suspend-safe and intended to run on `Dispatchers.IO`.
 */
object OverlayAddPipeline {

    /** Stages the caller may want to surface to the user. */
    enum class Stage { DOWNLOADING, DECODING, UPLOADING, FINALIZING }

    sealed class Outcome {
        /** Overlay successfully added; [id] is the persistent overlay id and [decoded] is the decode metadata. */
        data class Success(val id: String, val decoded: OverlayDecoder.DecodedImage) : Outcome()

        /** Add failed. [message] is the actionable human-readable error (no stack-trace noise). */
        data class Failure(val message: String) : Outcome()
    }

    /**
     * Run the full add pipeline for [rawUrl]. Emits stage transitions via [onStage] (each
     * one runs on the dispatcher of the caller); returns a final [Outcome]. Does NOT
     * marshal anything to the client thread — callers wrap UI updates accordingly.
     *
     * Side effect on success: appends a new [GifOverlayConfig] to [ConfigManager] and
     * uploads frames via [OverlayTextureManager]. If the config persist fails after a
     * successful upload, the freshly-allocated textures are released to avoid VRAM
     * orphans.
     */
    suspend fun addFromUrl(rawUrl: String, onStage: (Stage) -> Unit = {}): Outcome {
        val url = rawUrl.trim()
        if (url.isEmpty()) return Outcome.Failure("Invalid URL.")

        coroutineContext.ensureActive()
        onStage(Stage.DOWNLOADING)
        val cached = OverlayDownloader.download(url).getOrElse { error ->
            return Outcome.Failure(addFailureMessage(error))
        }

        coroutineContext.ensureActive()
        onStage(Stage.DECODING)
        val decoded = OverlayDecoder.decode(cached.path, cached.contentType).getOrElse { error ->
            return Outcome.Failure(addFailureMessage(error))
        }

        coroutineContext.ensureActive()
        val overlayId = GifOverlayConfig.newId()
        onStage(Stage.UPLOADING)
        try {
            OverlayTextureManager.upload(overlayId, decoded).join()
        } catch (e: Throwable) {
            DyeTrackerMod.warn("Overlay upload failed for '{}': {}", overlayId, e.message ?: e.javaClass.simpleName)
            return Outcome.Failure("Upload failed (see logs).")
        }

        // If we were cancelled between upload completion and config write, drop the
        // orphan textures so a cancelled add does not silently leave VRAM behind.
        if (!coroutineContext.isActive) {
            OverlayTextureManager.release(overlayId)
            coroutineContext.ensureActive() // throws CancellationException
        }

        onStage(Stage.FINALIZING)
        try {
            ConfigManager.addGif(GifOverlayConfig(id = overlayId, url = url))
        } catch (e: Throwable) {
            // Upload succeeded but the config write didn't — release the orphaned textures.
            OverlayTextureManager.release(overlayId)
            return Outcome.Failure("Upload succeeded but config save failed: ${e.message ?: e.javaClass.simpleName}.")
        }

        return Outcome.Success(overlayId, decoded)
    }

    /** Map any add-failure (downloader or decoder typed exception, or generic) into a chat-friendly line. */
    fun addFailureMessage(error: Throwable): String = when (error) {
        is OverlayDownloader.OverlayDownloadException -> downloaderReason(error.reason)
        is OverlayDecoder.OverlayDecodeException -> decoderReason(error.reason)
        else -> "Failed: ${error.javaClass.simpleName}: ${error.message ?: "(no message)"}"
    }

    private fun downloaderReason(reason: OverlayDownloader.Reason): String = when (reason) {
        is OverlayDownloader.Reason.InvalidUrl -> "Invalid URL: ${reason.url}"
        is OverlayDownloader.Reason.UnsupportedScheme ->
            "Unsupported scheme '${reason.scheme ?: "(none)"}'. Only http and https are allowed."
        OverlayDownloader.Reason.TooLarge -> "URL too large (max 10 MB)."
        is OverlayDownloader.Reason.UnsupportedContentType ->
            "Not an image (got ${reason.contentType ?: "(none)"})."
        OverlayDownloader.Reason.Timeout -> "Network error: request timed out."
        is OverlayDownloader.Reason.NetworkError ->
            "Network error: ${reason.cause.javaClass.simpleName}: ${reason.cause.message ?: "(no message)"}"
    }

    private fun decoderReason(reason: OverlayDecoder.Reason): String = when (reason) {
        is OverlayDecoder.Reason.Corrupt ->
            "Decode failed: corrupt image (${reason.cause?.javaClass?.simpleName ?: "unknown"})."
        is OverlayDecoder.Reason.Unsupported -> "Decode failed: unsupported format '${reason.format}'."
        OverlayDecoder.Reason.TooManyFrames -> "Decode failed: image has more than 500 frames."
        OverlayDecoder.Reason.Empty -> "Decode failed: image is empty or has no frames."
    }
}
