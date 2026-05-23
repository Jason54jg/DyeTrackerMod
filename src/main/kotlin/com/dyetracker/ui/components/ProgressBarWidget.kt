package com.dyetracker.ui.components

import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.Size
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.core.Widget
import com.dyetracker.ui.theme.UiTheme
import kotlin.math.roundToInt

/**
 * A horizontal progress bar: a full-width track with a proportional fill, mirroring the
 * website single-dye overlay's `ProgressBar` (track + fill at `value%`,
 * `packages/web/src/components/ui/ProgressBar.tsx`). Purely presentational — it takes a
 * static [value] and draws; no data fetching and no animation.
 *
 * The bar reserves a fixed [width] × [height] footprint (it does not grow with content).
 * [value] is clamped to `0..100`; the fill width is `round(width * value / 100)` clamped to
 * `[0, width]`, so `value >= 100` fills the whole track and `value <= 0` shows the bare track.
 */
class ProgressBarWidget(
    private val value: Float,
    private val width: Int,
    private val height: Int = UiTheme.Sizing.PROGRESS_BAR_HEIGHT,
    private val trackColor: Int = UiTheme.Colors.PROGRESS_TRACK,
    private val fillColor: Int = UiTheme.Colors.PROGRESS_FILL,
) : Widget {

    override fun measure(): Size = Size(width, height)

    override fun draw(ctx: RenderContext, x: Int, y: Int) {
        UiDraw.fillRect(ctx.drawContext, x, y, width, height, trackColor)
        val fill = fillWidth(value, width)
        if (fill > 0) {
            UiDraw.fillRect(ctx.drawContext, x, y, fill, height, fillColor)
        }
    }

    companion object {
        const val MIN_VALUE: Float = 0f
        const val MAX_VALUE: Float = 100f

        /**
         * Pixel width of the filled portion for [value] (clamped to `0..100`) over a [width]px
         * track, clamped to `[0, width]`. Returns 0 for a non-positive [width].
         */
        fun fillWidth(value: Float, width: Int): Int {
            if (width <= 0) return 0
            val clamped = value.coerceIn(MIN_VALUE, MAX_VALUE)
            return (width * clamped / MAX_VALUE).roundToInt().coerceIn(0, width)
        }
    }
}
