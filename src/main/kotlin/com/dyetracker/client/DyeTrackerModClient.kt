package com.dyetracker.client

import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import com.dyetracker.overlay.GifOverlayConfig
import com.dyetracker.overlay.OverlayDecoder
import com.dyetracker.overlay.OverlayDownloader
import com.dyetracker.overlay.OverlayHudRenderer
import com.dyetracker.overlay.OverlayKeybinds
import com.dyetracker.overlay.OverlayTextureManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents

/**
 * Client-only entry point. The common [DyeTrackerMod] entry handles config + commands +
 * sync; this class wires the client-only HUD overlay subsystem (renderer + keybind +
 * startup hydration of persisted overlays).
 *
 * Lifecycle:
 *  - `onInitializeClient` registers the HUD renderer and the `G` keybind eagerly so they
 *    are live before the first frame.
 *  - `CLIENT_STARTED` runs once the client finishes booting; we then iterate
 *    `ConfigManager.config.gifs` and fetch+decode+upload each persisted overlay in the
 *    background so they reappear at their saved positions.
 *  - `CLIENT_STOPPING` releases every texture so dev hot-reloads don't leak VRAM.
 */
class DyeTrackerModClient : ClientModInitializer {

    private val overlayLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var startupLoadInvoked = false

    override fun onInitializeClient() {
        OverlayHudRenderer.register()
        OverlayKeybinds.register()

        ClientLifecycleEvents.CLIENT_STARTED.register { _ -> loadConfiguredOverlays() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { _ ->
            // Cancel any in-flight restores so worker threads don't outlive the client
            // (matters mostly in dev hot-reload). `releaseAll` is then the source of
            // truth for texture cleanup; `clearAllFirstRenderMarks` is a defensive
            // belt-and-braces clear in case any release was scheduled to the render
            // thread but the executor drained early on stop.
            overlayLoadScope.cancel()
            OverlayTextureManager.releaseAll()
            OverlayHudRenderer.clearAllFirstRenderMarks()
        }

        DyeTrackerMod.info("DyeTracker client initialized (overlays subsystem)")
    }

    /**
     * Re-hydrate every overlay in [ConfigManager.config.gifs] by re-running the
     * download → decode → upload pipeline. The downloader's on-disk cache makes this
     * cheap when the source URL hasn't changed since last run.
     */
    private fun loadConfiguredOverlays() {
        if (startupLoadInvoked) {
            DyeTrackerMod.debug("Overlay startup load already invoked; skipping re-entry")
            return
        }
        startupLoadInvoked = true
        val configured = ConfigManager.config.gifs
        if (configured.isEmpty()) {
            DyeTrackerMod.debug("No persisted overlays to restore")
            return
        }
        DyeTrackerMod.info("Restoring {} persisted overlay(s)", configured.size)
        for (overlay in configured) {
            overlayLoadScope.launch { restoreOne(overlay) }
        }
    }

    private suspend fun restoreOne(overlay: GifOverlayConfig) {
        val cachedResult = OverlayDownloader.download(overlay.url)
        val cached = cachedResult.getOrElse { error ->
            warnRestoreFailure("download", overlay, error)
            return
        }
        val decodedResult = OverlayDecoder.decode(cached.path, cached.contentType)
        val decoded = decodedResult.getOrElse { error ->
            warnRestoreFailure("decode", overlay, error)
            return
        }
        try {
            OverlayTextureManager.upload(overlay.id, decoded).join()
            DyeTrackerMod.debug("Restored overlay '{}' ({} frames)", overlay.id, decoded.frames.size)
        } catch (e: CancellationException) {
            // Client is stopping — propagate so the scope can shut down cleanly.
            throw e
        } catch (e: Throwable) {
            warnRestoreFailure("upload", overlay, e)
        }
    }

    private fun warnRestoreFailure(stage: String, overlay: GifOverlayConfig, error: Throwable) {
        DyeTrackerMod.warn(
            "Could not restore overlay '{}' ({}) from {}: {}",
            overlay.id, stage, overlay.url, error.message ?: error.javaClass.simpleName
        )
    }
}
