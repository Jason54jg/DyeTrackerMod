package com.dyetracker.rotation

import com.dyetracker.DyeTrackerMod
import com.dyetracker.ui.texture.ImageFrame
import com.dyetracker.ui.texture.ImageFrames
import com.dyetracker.ui.texture.ImageTextureManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import javax.imageio.ImageIO

/**
 * Loads the bundled dye sprite PNGs (task 31-1) into the shared [ImageTextureManager] at client
 * startup so the rotation widget (31-5) can draw any dye with `SpriteWidget(DyeSprites.spriteId(id))`.
 *
 * Sprites are read from the classpath (mod resources), decoded off the main thread, and uploaded
 * via [ImageTextureManager.upload], which marshals the GPU upload onto the render thread itself.
 * A missing or undecodable sprite is logged and skipped — a `SpriteWidget` for an un-uploaded id
 * simply measures to `Size.ZERO` and draws nothing, so startup never crashes on a bad asset.
 *
 * Texture release is handled centrally by `ImageTextureManager.releaseAll()` on `CLIENT_STOPPING`
 * (see `DyeTrackerModClient`); this loader only cancels its decode scope on shutdown.
 */
object DyeSpriteLoader {

    /** Delay marking a single static (non-animated) frame, matching the GIF decoder's convention. */
    private const val STATIC_FRAME_DELAY_MS = Int.MAX_VALUE

    // Single-use: cancelled on CLIENT_STOPPING and never recreated (re-entry is blocked by loadInvoked).
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var loadInvoked = false

    /** Wire startup load + shutdown cancellation. Call once from `onInitializeClient()`. */
    fun register() {
        ClientLifecycleEvents.CLIENT_STARTED.register { _ -> loadAll() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { _ -> loadScope.cancel() }
    }

    private fun loadAll() {
        if (loadInvoked) {
            DyeTrackerMod.debug("Dye sprite load already invoked; skipping re-entry")
            return
        }
        loadInvoked = true
        loadScope.launch {
            var dispatched = 0
            for (dyeId in DyeSprites.DYE_IDS) {
                ensureActive() // cooperate with shutdown cancellation
                if (loadOne(dyeId)) dispatched++
            }
            // Count reflects successful decode + upload dispatch; an async upload failure on the
            // render thread is logged separately by ImageTextureManager.
            DyeTrackerMod.info("Dispatched {} of {} dye sprite uploads", dispatched, DyeSprites.DYE_IDS.size)
        }
    }

    private fun loadOne(dyeId: String): Boolean {
        val resourcePath = "/" + DyeSprites.resourcePath(dyeId)
        return try {
            val image = DyeSpriteLoader::class.java.getResourceAsStream(resourcePath)
                ?.use { ImageIO.read(it) }
            if (image == null) {
                DyeTrackerMod.warn("Dye sprite missing or undecodable: {}", resourcePath)
                return false
            }
            val frames = ImageFrames(
                frames = listOf(ImageFrame(image, STATIC_FRAME_DELAY_MS)),
                width = image.width,
                height = image.height,
            )
            ImageTextureManager.upload(DyeSprites.spriteId(dyeId), frames)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            DyeTrackerMod.warn(
                "Failed to load dye sprite '{}': {}",
                dyeId, e.message ?: e.javaClass.simpleName
            )
            false
        }
    }
}
