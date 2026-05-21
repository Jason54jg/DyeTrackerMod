package com.dyetracker.config

import com.dyetracker.overlay.GifOverlayConfig
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
    val gifs: List<GifOverlayConfig> = emptyList()
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
