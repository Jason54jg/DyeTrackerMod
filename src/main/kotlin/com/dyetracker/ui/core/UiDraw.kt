package com.dyetracker.ui.core

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import kotlin.math.roundToInt

/**
 * Thin wrappers over the repetitive [DrawContext] primitives used across the toolkit:
 * solid fills, 1px bordered rectangles, alignment-aware text, and the non-tiling
 * stretched-texture blit. Centralizing these keeps widgets free of raw `DrawContext`
 * boilerplate and documents the single correct way to do each — notably the 12-arg
 * `drawTexture` overload that *stretches* the source (the 10-arg variant tiles instead).
 */
object UiDraw {

    const val DEFAULT_BORDER_PX = 1

    /** Fill a solid rectangle of [width]×[height] at ([x], [y]) with [argb]. No-op if empty. */
    fun fillRect(context: DrawContext, x: Int, y: Int, width: Int, height: Int, argb: Int) {
        if (width <= 0 || height <= 0) return
        context.fill(x, y, x + width, y + height, argb)
    }

    /**
     * Stroke a [thickness]px border along the inner edges of the [width]×[height] rect at
     * ([x], [y]). Corners are drawn once (the left/right strokes are inset between the
     * top/bottom strokes). For an *outset* focus border, pass an expanded rect.
     */
    fun strokeRect(
        context: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        argb: Int,
        thickness: Int = DEFAULT_BORDER_PX,
    ) {
        if (width <= 0 || height <= 0 || thickness <= 0) return
        val right = x + width
        val bottom = y + height
        context.fill(x, y, right, y + thickness, argb) // top
        context.fill(x, bottom - thickness, right, bottom, argb) // bottom
        context.fill(x, y + thickness, x + thickness, bottom - thickness, argb) // left
        context.fill(right - thickness, y + thickness, right, bottom - thickness, argb) // right
    }

    /** Fill [fillArgb] then stroke a [borderThickness]px [borderArgb] border on the same rect. */
    fun filledBox(
        context: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fillArgb: Int,
        borderArgb: Int,
        borderThickness: Int = DEFAULT_BORDER_PX,
    ) {
        fillRect(context, x, y, width, height, fillArgb)
        strokeRect(context, x, y, width, height, borderArgb, borderThickness)
    }

    /**
     * Draw [text] at ([x], [y]) honoring [alignment] within [availableWidth] (defaults to the
     * text's own width × [scale], i.e. no extra alignment box). [x] is the left edge of the
     * alignment box; the text is offset within it. Optionally drawn with a drop [shadow].
     *
     * [scale] != 1 is applied via a matrix transform (the vanilla text API has no scale
     * parameter); the alignment box and child width are both measured in scaled pixels so the
     * glyphs land at the correct position and size.
     */
    fun drawText(
        context: DrawContext,
        textRenderer: TextRenderer,
        text: String,
        x: Int,
        y: Int,
        argb: Int,
        shadow: Boolean = true,
        scale: Float = 1f,
        availableWidth: Int = (textRenderer.getWidth(text) * scale).roundToInt(),
        alignment: HorizontalAlignment = HorizontalAlignment.START,
    ) {
        val textWidth = (textRenderer.getWidth(text) * scale).roundToInt()
        val drawX = x + alignment.offset(availableWidth, textWidth)
        if (scale == 1f) {
            context.drawText(textRenderer, text, drawX, y, argb, shadow)
        } else {
            val matrices = context.matrices
            matrices.pushMatrix()
            matrices.translate(drawX.toFloat(), y.toFloat())
            matrices.scale(scale, scale)
            context.drawText(textRenderer, text, 0, 0, argb, shadow)
            matrices.popMatrix()
        }
    }

    /**
     * Blit [textureId] into a [destWidth]×[destHeight] quad at ([x], [y]), sampling the full
     * [srcWidth]×[srcHeight] source so the GPU bilinearly *stretches* it when the destination
     * differs from the source. This is the 12-arg `drawTexture` overload; the 10-arg variant
     * treats dest dims as the sampled region and would tile rather than scale.
     */
    fun blitStretched(
        context: DrawContext,
        textureId: Identifier,
        x: Int,
        y: Int,
        destWidth: Int,
        destHeight: Int,
        srcWidth: Int,
        srcHeight: Int,
    ) {
        if (destWidth <= 0 || destHeight <= 0 || srcWidth <= 0 || srcHeight <= 0) return
        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            textureId,
            x, y,
            0f, 0f,
            destWidth, destHeight,
            srcWidth, srcHeight,
            srcWidth, srcHeight,
        )
    }
}
