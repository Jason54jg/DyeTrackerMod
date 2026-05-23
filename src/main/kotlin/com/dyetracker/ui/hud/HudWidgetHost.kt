package com.dyetracker.ui.hud

import com.dyetracker.DyeTrackerMod
import com.dyetracker.ui.core.Rect
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.Widget
import com.dyetracker.ui.texture.ImageTextureManager
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import kotlin.math.roundToInt

/**
 * Single HUD host for the UI toolkit. Registers one Fabric HUD element (before vanilla chat)
 * and, each frame, draws every visible widget contributed to [HudWidgetRegistry] — applying
 * F1 hud-hide, a shared wall-clock animation timer that freezes while the game is paused, and
 * placement (fractional center → absolute top-left, intrinsic size × scale via a matrix
 * transform). Generalized from the GIF-specific overlay renderer (PBI 28).
 */
object HudWidgetHost {

    private const val HUD_ELEMENT_ID = "ui_widget_host"

    private var registered = false
    private var lastWallMs: Long = 0L
    private var runningTimeMs: Long = 0L
    private val firstRenderLogged = HashSet<String>()

    /** Register the HUD element + texture-release hook. Idempotent. */
    fun register() {
        if (registered) return
        registered = true
        // Drop the per-id first-render mark when an image is released so a re-uploaded id
        // logs once again and the set doesn't grow across add/remove churn.
        ImageTextureManager.addReleaseListener(::clearFirstRenderMark)
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.of(DyeTrackerMod.MOD_ID, HUD_ELEMENT_ID),
        ) { context, tickCounter -> render(context, tickCounter) }
        DyeTrackerMod.info("HUD widget host attached before vanilla chat")
    }

    private fun render(context: DrawContext, @Suppress("UNUSED_PARAMETER") tickCounter: RenderTickCounter) {
        val client = MinecraftClient.getInstance() ?: return
        val now = Util.getMeasuringTimeMs()
        val paused = client.isPaused
        // Advance the shared animation clock BEFORE the F1 check so animations stay in phase
        // even while the HUD is hidden; freeze it while paused.
        if (lastWallMs == 0L) {
            lastWallMs = now
        } else {
            val delta = now - lastWallMs
            lastWallMs = now
            if (!paused && delta > 0) runningTimeMs += delta
        }

        if (client.options.hudHidden) return

        val entries = HudWidgetRegistry.entries()
        if (entries.isEmpty()) return

        val window = client.window ?: return
        val screenW = window.scaledWidth
        val screenH = window.scaledHeight
        val renderCtx = RenderContext(context, runningTimeMs, paused)

        for (entry in entries) {
            if (!entry.placement.visible) continue
            val rect = boundsOf(entry, screenW, screenH) ?: continue
            drawScaled(renderCtx, entry.widget, rect.x, rect.y, entry.placement.scale)
            if (firstRenderLogged.add(entry.id)) {
                DyeTrackerMod.debug("First render of HUD widget '{}'", entry.id)
            }
        }
    }

    /**
     * Absolute bounding rect (top-left + scaled size) of [entry] on a [screenW]×[screenH]
     * screen, derived from its measured intrinsic size and fractional-center placement.
     * Returns null when the widget has no size yet (e.g. a sprite whose texture is not
     * uploaded). Shared by the render loop and the edit screen so draw position and
     * hit-test/focus bounds always agree.
     */
    fun boundsOf(entry: HudWidgetEntry, screenW: Int, screenH: Int): Rect? {
        val size = entry.widget.measure()
        if (size.width <= 0 || size.height <= 0) return null
        val scale = entry.placement.scale
        val scaledW = (size.width * scale).roundToInt()
        val scaledH = (size.height * scale).roundToInt()
        val x = (entry.placement.x * screenW - size.width * scale / 2f).roundToInt()
        val y = (entry.placement.y * screenH - size.height * scale / 2f).roundToInt()
        return Rect(x, y, scaledW, scaledH)
    }

    /** Draw [entry]'s widget at [rect]'s top-left, scaled by the entry's placement scale. */
    fun drawEntryWidget(ctx: RenderContext, entry: HudWidgetEntry, rect: Rect) {
        drawScaled(ctx, entry.widget, rect.x, rect.y, entry.placement.scale)
    }

    /**
     * Draw [widget] at ([x], [y]) scaled by [scale]. The whole widget tree is scaled
     * uniformly via the GUI matrix stack, so text, fills, and sprites all scale together.
     */
    private fun drawScaled(ctx: RenderContext, widget: Widget, x: Int, y: Int, scale: Float) {
        if (scale == 1f) {
            widget.draw(ctx, x, y)
            return
        }
        val matrices = ctx.drawContext.matrices
        matrices.pushMatrix()
        matrices.translate(x.toFloat(), y.toFloat())
        matrices.scale(scale, scale)
        widget.draw(ctx, 0, 0)
        matrices.popMatrix()
    }

    /** Drop the "first render logged" mark for [id]. Invoked via the texture release listener. */
    fun clearFirstRenderMark(id: String) {
        firstRenderLogged.remove(id)
    }

    /** Drop all first-render marks (client shutdown). */
    fun clearAllFirstRenderMarks() {
        firstRenderLogged.clear()
    }
}
