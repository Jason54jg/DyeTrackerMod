package com.dyetracker.overlay

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Persisted configuration for a single GIF/image HUD overlay.
 *
 * Coordinates [x] and [y] are fractions of screen size (0.0–1.0) so overlays survive
 * resolution changes. [scale] is a float multiplier applied to the source image
 * dimensions when drawing.
 */
@Serializable
data class GifOverlayConfig(
    val id: String,
    val url: String,
    val x: Float = DEFAULT_X,
    val y: Float = DEFAULT_Y,
    val scale: Float = DEFAULT_SCALE,
    val visible: Boolean = true,
) {
    companion object {
        const val DEFAULT_X = 0.5f
        const val DEFAULT_Y = 0.5f
        const val DEFAULT_SCALE = 1.0f
        private const val ID_LENGTH = 8

        /** Generate a short opaque ID for a new overlay. */
        fun newId(): String = UUID.randomUUID().toString().take(ID_LENGTH)
    }
}
