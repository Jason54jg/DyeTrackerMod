package com.dyetracker.overlay

import com.dyetracker.ui.core.WidgetPlacement
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Persisted configuration for a single GIF/image HUD overlay. Implements [WidgetPlacement]
 * so it can be positioned/scaled/hidden by the generic HUD host, edit screen, and placement
 * store. The serialized shape is unchanged (`{id,url,x,y,scale,visible}`) — implementing the
 * interface only adds `override` modifiers.
 *
 * Coordinates [x] and [y] are fractions of screen size (0.0–1.0) so overlays survive
 * resolution changes. [scale] is a float multiplier applied to the source image
 * dimensions when drawing.
 */
@Serializable
data class GifOverlayConfig(
    override val id: String,
    val url: String,
    override val x: Float = DEFAULT_X,
    override val y: Float = DEFAULT_Y,
    override val scale: Float = DEFAULT_SCALE,
    override val visible: Boolean = true,
) : WidgetPlacement {
    companion object {
        const val DEFAULT_X = 0.5f
        const val DEFAULT_Y = 0.5f
        const val DEFAULT_SCALE = 1.0f
        private const val ID_LENGTH = 8

        /** Generate a short opaque ID for a new overlay. */
        fun newId(): String = UUID.randomUUID().toString().take(ID_LENGTH)
    }
}
