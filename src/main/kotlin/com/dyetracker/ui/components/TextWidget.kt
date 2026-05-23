package com.dyetracker.ui.components

import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.Size
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.core.Widget
import com.dyetracker.ui.theme.UiTheme
import net.minecraft.client.MinecraftClient
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A line of text drawn with the vanilla `TextRenderer`, with configurable color, drop
 * shadow, font [scale], and horizontal [alignment].
 *
 * The widget measures to its text size (× [scale]). If [minWidth] is larger than the text,
 * the widget reserves that width and aligns the text inside it per [alignment] — e.g. a
 * centered title spanning the width of a row beneath it. [scale] != 1 is applied via a
 * matrix transform (the vanilla text API has no scale parameter).
 */
class TextWidget(
    private val text: String,
    private val color: Int = UiTheme.Colors.TEXT_PRIMARY,
    private val shadow: Boolean = true,
    private val scale: Float = UiTheme.Sizing.FONT_SCALE,
    private val alignment: HorizontalAlignment = HorizontalAlignment.START,
    private val minWidth: Int = 0,
) : Widget {

    private val textRenderer get() = MinecraftClient.getInstance().textRenderer

    private fun scaledTextWidth(): Int = (textRenderer.getWidth(text) * scale).roundToInt()

    private fun scaledTextHeight(): Int = (textRenderer.fontHeight * scale).roundToInt()

    override fun measure(): Size = Size(max(scaledTextWidth(), minWidth), scaledTextHeight())

    override fun draw(ctx: RenderContext, x: Int, y: Int) {
        UiDraw.drawText(
            context = ctx.drawContext,
            textRenderer = textRenderer,
            text = text,
            x = x,
            y = y,
            argb = color,
            shadow = shadow,
            scale = scale,
            availableWidth = max(scaledTextWidth(), minWidth),
            alignment = alignment,
        )
    }
}
