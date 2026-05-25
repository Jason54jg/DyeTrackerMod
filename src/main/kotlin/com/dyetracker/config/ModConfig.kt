package com.dyetracker.config

import com.dyetracker.data.DyeRotation
import com.dyetracker.dyeprogress.DyeProgressWidgetConfig
import com.dyetracker.overlay.GifOverlayConfig
import com.dyetracker.rotation.RotationWidgetConfig
import kotlinx.serialization.Serializable

/**
 * Configuration data class for the DyeTracker mod.
 *
 * This class defines all configurable options for the mod.
 * The configuration is stored as JSON in the config directory.
 */
@Serializable
data class ModConfig(
    /**
     * The URL of the DyeTracker API server.
     */
    val apiUrl: String = DEFAULT_API_URL,

    /**
     * Authentication token for API requests.
     * Leave empty if not authenticated.
     */
    val authToken: String = DEFAULT_AUTH_TOKEN,

    /**
     * The verified Minecraft UUID (without dashes).
     * Set after successful account linking.
     */
    val linkedUuid: String = "",

    /**
     * The verified Minecraft username.
     * Set after successful account linking.
     */
    val linkedUsername: String = "",

    /**
     * Persisted GIF/image HUD overlays added by the player. See PBI-28.
     */
    val gifs: List<GifOverlayConfig> = emptyList(),

    /**
     * Last-known Vincent dye rotation, captured from the in-game "Dyes" screen. Persisted so the
     * rotation HUD widget can render immediately on startup before the player reopens Vincent.
     * Null until the first capture. See PBI-31.
     */
    val dyeRotation: DyeRotation? = null,

    /**
     * Placement (position/scale/visibility) of the singleton dye-rotation HUD widget. Null until
     * first seeded; the rotation feature seeds a default on startup. See PBI-31.
     */
    val rotationWidget: RotationWidgetConfig? = null,

    /**
     * Persisted single-dye progress HUD widgets added by the player (one per chosen dye +
     * profile). Multi-instance, like [gifs]. Defaults to empty so existing config files without
     * this field load unchanged. See PBI-34.
     */
    val dyeProgressWidgets: List<DyeProgressWidgetConfig> = emptyList(),

    /**
     * Whether DVD-bounce mode is on. The only bounce state ever persisted — per-widget drift
     * positions are ephemeral and never written to disk, so toggling off or restarting restores
     * every widget to its saved placement. Defaults to false so existing config files load
     * unchanged. See PBI-38.
     */
    val bounceEnabled: Boolean = false
) {
    companion object {
        const val DEFAULT_API_URL = "https://dye-tracker-api.seanwalsh4118-7a3.workers.dev"
        const val DEFAULT_AUTH_TOKEN = ""
    }

    /**
     * Check if an account is currently linked.
     */
    fun isLinked(): Boolean = linkedUuid.isNotEmpty() && authToken.isNotEmpty()
}
