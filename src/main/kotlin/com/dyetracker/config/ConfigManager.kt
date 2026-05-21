package com.dyetracker.config

import com.dyetracker.DyeTrackerMod
import com.dyetracker.overlay.GifOverlayConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Manages loading, saving, and accessing the mod configuration.
 *
 * Configuration is stored in the game's config directory under
 * `config/dyetracker/config.json`.
 */
object ConfigManager {
    private const val CONFIG_DIR_NAME = "dyetracker"
    private const val CONFIG_FILE_NAME = "config.json"

    private val gifsLock = Any()

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * The current configuration. Defaults to default values until [load] is called.
     */
    var config: ModConfig = ModConfig()
        private set

    /**
     * The path to the configuration file.
     */
    val configPath: Path by lazy {
        FabricLoader.getInstance().configDir
            .resolve(CONFIG_DIR_NAME)
            .resolve(CONFIG_FILE_NAME)
    }

    /**
     * The path to the configuration directory.
     */
    private val configDir: Path by lazy {
        FabricLoader.getInstance().configDir.resolve(CONFIG_DIR_NAME)
    }

    /**
     * Loads the configuration from disk.
     *
     * If the configuration file doesn't exist, creates it with default values.
     * If the file is corrupted or unreadable, logs a warning and uses defaults.
     */
    fun load() {
        try {
            DyeTrackerMod.info("Config path: {}", configPath.toAbsolutePath())
            ensureConfigDirExists()

            if (Files.exists(configPath)) {
                DyeTrackerMod.info("Loading existing config from {}", configPath.toAbsolutePath())
                val content = Files.readString(configPath)
                config = try {
                    json.decodeFromString<ModConfig>(content)
                } catch (e: Exception) {
                    DyeTrackerMod.warn("Failed to parse config file, using defaults: {}", e.message)
                    ModConfig()
                }
                DyeTrackerMod.info("Config loaded: apiUrl={}", config.apiUrl)
            } else {
                DyeTrackerMod.info("Config file not found, creating default at {}", configPath.toAbsolutePath())
                config = ModConfig()
                save()
            }
        } catch (e: Exception) {
            DyeTrackerMod.error("Failed to load configuration", e)
            config = ModConfig()
        }
    }

    /**
     * Saves the current configuration to disk.
     */
    fun save() {
        try {
            ensureConfigDirExists()
            val content = json.encodeToString(config)
            Files.writeString(configPath, content)
            DyeTrackerMod.info("Configuration saved to {}", configPath.toAbsolutePath())
        } catch (e: Exception) {
            DyeTrackerMod.error("Failed to save configuration", e)
        }
    }

    /**
     * Updates the configuration with new values and saves to disk.
     *
     * @param updater A function that receives the current config and returns the updated config.
     */
    fun update(updater: (ModConfig) -> ModConfig) {
        config = updater(config)
        save()
    }

    /**
     * Resets the configuration to default values and saves to disk.
     */
    fun reset() {
        config = ModConfig()
        save()
        DyeTrackerMod.info("Configuration reset to defaults")
    }

    /**
     * Append a new GIF overlay and persist. Thread-safe.
     */
    fun addGif(overlay: GifOverlayConfig) {
        synchronized(gifsLock) {
            config = config.copy(gifs = config.gifs + overlay)
            save()
        }
    }

    /**
     * Remove a GIF overlay by id. Returns `true` if removed, `false` if not found.
     * Thread-safe.
     */
    fun removeGif(id: String): Boolean = synchronized(gifsLock) {
        val next = config.gifs.filterNot { it.id == id }
        if (next.size == config.gifs.size) return@synchronized false
        config = config.copy(gifs = next)
        save()
        true
    }

    /**
     * Update a GIF overlay by id, applying [transform] to produce the new value.
     * Returns `true` if found and updated, `false` if not found. Thread-safe.
     */
    fun updateGif(id: String, transform: (GifOverlayConfig) -> GifOverlayConfig): Boolean =
        synchronized(gifsLock) {
            val changed = applyGifUpdate(id, transform)
            if (changed) save()
            changed
        }

    /**
     * In-memory variant of [updateGif] that does NOT persist to disk. Use during
     * interactive UI (drag, scroll-scale) to avoid a disk write on every frame, then
     * call [flushGifs] once the interaction ends to persist the final value.
     * Returns `true` if found and updated, `false` if not found. Thread-safe.
     */
    fun updateGifTransient(id: String, transform: (GifOverlayConfig) -> GifOverlayConfig): Boolean =
        synchronized(gifsLock) { applyGifUpdate(id, transform) }

    /** Persist the current config to disk. Thread-safe convenience wrapper around [save]. */
    fun flushGifs() {
        synchronized(gifsLock) { save() }
    }

    private fun applyGifUpdate(id: String, transform: (GifOverlayConfig) -> GifOverlayConfig): Boolean {
        var found = false
        val next = config.gifs.map { overlay ->
            if (overlay.id == id) {
                found = true
                transform(overlay)
            } else {
                overlay
            }
        }
        if (!found) return false
        config = config.copy(gifs = next)
        return true
    }

    private fun ensureConfigDirExists() {
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
        }
    }
}
