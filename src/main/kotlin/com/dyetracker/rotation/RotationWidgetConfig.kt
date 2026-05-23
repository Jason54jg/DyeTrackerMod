package com.dyetracker.rotation

import com.dyetracker.ui.core.WidgetPlacement
import kotlinx.serialization.Serializable

/**
 * Persisted placement for the single dye-rotation HUD widget. Implements [WidgetPlacement] so the
 * generic HUD host and edit screen can position/scale/hide it.
 *
 * Unlike the GIF overlays (many, each with a random id), there is exactly one rotation widget, so
 * [id] defaults to the stable [WIDGET_ID]. Coordinates are fractions of the scaled window (0..1)
 * addressing the widget's center, so placement survives resolution changes.
 */
@Serializable
data class RotationWidgetConfig(
    override val id: String = WIDGET_ID,
    override val x: Float = DEFAULT_X,
    override val y: Float = DEFAULT_Y,
    override val scale: Float = DEFAULT_SCALE,
    override val visible: Boolean = true,
) : WidgetPlacement {
    companion object {
        /** Stable id for the singleton rotation widget (also its texture/edit-entry key). */
        const val WIDGET_ID = "dye_rotation"
        const val DEFAULT_X = 0.5f
        const val DEFAULT_Y = 0.5f
        const val DEFAULT_SCALE = 1.0f
    }
}
