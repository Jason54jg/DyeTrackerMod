package com.dyetracker.ui.components

import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.Size
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.core.Widget
import com.dyetracker.ui.texture.ImageTextureManager
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws an image registered with [ImageTextureManager] under [imageId]. The source may be
 * static (single frame) or animated (the widget queries the current frame from the render
 * context's wall clock, so it animates and pause-freezes consistently with every other
 * toolkit widget).
 *
 * Intrinsic size is the source dimensions × [scale], drawn with the non-tiling stretched
 * blit. If the texture has not been uploaded yet (or was released) the widget measures to
 * [Size.ZERO] and draws nothing, so a not-yet-ready image is simply skipped.
 */
class SpriteWidget(
    private val imageId: String,
    private val scale: Float = 1f,
) : Widget {

    private fun scaledDim(source: Int): Int = max(1, (source * scale).roundToInt())

    override fun measure(): Size {
        val w = ImageTextureManager.widthOf(imageId)
        val h = ImageTextureManager.heightOf(imageId)
        if (w <= 0 || h <= 0) return Size.ZERO
        return Size(scaledDim(w), scaledDim(h))
    }

    override fun draw(ctx: RenderContext, x: Int, y: Int) {
        val srcW = ImageTextureManager.widthOf(imageId)
        val srcH = ImageTextureManager.heightOf(imageId)
        if (srcW <= 0 || srcH <= 0) return
        val frameId = ImageTextureManager.currentFrameIdentifier(imageId, ctx.wallClockMs, ctx.paused) ?: return
        UiDraw.blitStretched(ctx.drawContext, frameId, x, y, scaledDim(srcW), scaledDim(srcH), srcW, srcH)
    }
}
