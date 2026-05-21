package com.dyetracker.overlay

import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Iterates configured overlays each frame and draws the current frame for every visible
 * one. Registered exactly once via [register] (idempotent). Skips entirely when the F1
 * HUD-hidden state is set; freezes animation when the game is paused.
 */
object OverlayHudRenderer {

    private const val OVERLAY_ID = "gif_overlay"

    private var registered = false
    private var lastWallMs: Long = 0L
    private var runningTimeMs: Long = 0L
    private val firstRenderLogged = HashSet<String>()

    /** Register the HUD element. Idempotent. */
    fun register() {
        if (registered) return
        registered = true
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.of(DyeTrackerMod.MOD_ID, OVERLAY_ID),
        ) { context, tickCounter -> render(context, tickCounter) }
        DyeTrackerMod.info("HUD overlay renderer attached before vanilla chat")
    }

    private fun render(context: DrawContext, @Suppress("UNUSED_PARAMETER") tickCounter: RenderTickCounter) {
        val client = MinecraftClient.getInstance() ?: return
        val now = Util.getMeasuringTimeMs()
        val paused = client.isPaused
        if (lastWallMs == 0L) {
            lastWallMs = now
        } else {
            val delta = now - lastWallMs
            lastWallMs = now
            if (!paused && delta > 0) runningTimeMs += delta
        }

        if (client.options.hudHidden) return

        val gifs = ConfigManager.config.gifs
        if (gifs.isEmpty()) return

        val window = client.window ?: return
        val screenW = window.scaledWidth
        val screenH = window.scaledHeight

        for (i in gifs.indices) {
            val cfg = gifs[i]
            if (!cfg.visible) continue
            val id = OverlayTextureManager.currentFrameIdentifier(cfg.id, runningTimeMs, paused) ?: continue
            val srcW = OverlayTextureManager.widthOf(cfg.id)
            val srcH = OverlayTextureManager.heightOf(cfg.id)
            if (srcW <= 0 || srcH <= 0) continue
            val drawW = max(1, (srcW * cfg.scale).roundToInt())
            val drawH = max(1, (srcH * cfg.scale).roundToInt())
            val centerX = (cfg.x * screenW).roundToInt()
            val centerY = (cfg.y * screenH).roundToInt()
            val x = centerX - drawW / 2
            val y = centerY - drawH / 2
            // 12-arg overload: (pipeline, id, x, y, u, v, destW, destH, regionW, regionH, textureW, textureH).
            // The 10-arg variant treats destW/destH as the texture region size too, which
            // causes the texture to TILE rather than stretch when destW > srcW. Passing
            // regionW=srcW + textureW=srcW means we sample the full texture and let the
            // GPU bilinearly scale into the destW × destH quad.
            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                id,
                x, y,
                0f, 0f,
                drawW, drawH,
                srcW, srcH,
                srcW, srcH,
            )
            if (firstRenderLogged.add(cfg.id)) {
                DyeTrackerMod.debug("First render of overlay '{}' -> {}", cfg.id, id)
            }
        }
    }

    /**
     * Drop the "first render logged" mark for [overlayId] so a re-uploaded overlay logs
     * once again. Called by [OverlayTextureManager.release] / [OverlayTextureManager.releaseAll].
     * Also prevents the set from growing unboundedly across add/remove churn.
     */
    fun clearFirstRenderMark(overlayId: String) {
        firstRenderLogged.remove(overlayId)
    }

    /** Drop all "first render logged" marks (mod shutdown / releaseAll). */
    fun clearAllFirstRenderMarks() {
        firstRenderLogged.clear()
    }
}
