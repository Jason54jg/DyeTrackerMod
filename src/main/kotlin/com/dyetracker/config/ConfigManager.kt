package com.dyetracker.config

import com.dyetracker.DyeTrackerMod
import com.dyetracker.data.DyeRotation
import com.dyetracker.dyeprogress.DyeProgressWidgetConfig
import com.dyetracker.overlay.GifOverlayConfig
import com.dyetracker.rotation.RotationWidgetConfig
import com.dyetracker.ui.persist.PlacementStore
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
    private val rotationLock = Any()
    private val dyeProgressLock = Any()

    /**
     * Persisted placement store for the GIF/image HUD overlays. Provides the generic
     * add/remove/update/updateTransient/flush API over the `gifs` config slice; the GIF
     * subsystem mutates overlays exclusively through this. Other features create their own
     * [PlacementStore] over their own config slice the same way.
     */
    val gifPlacements: PlacementStore<GifOverlayConfig> = PlacementStore(
        read = { config.gifs },
        write = { config = config.copy(gifs = it) },
        persist = { save() },
        lock = gifsLock,
    )

    /**
     * Placement store for the singleton dye-rotation widget. Bridges the single nullable
     * [ModConfig.rotationWidget] field to the list-based [PlacementStore] (0-or-1 elements) so the
     * rotation widget reuses the same transient-update-then-flush machinery as the GIF overlays.
     */
    val rotationPlacements: PlacementStore<RotationWidgetConfig> = PlacementStore(
        read = { config.rotationWidget?.let { listOf(it) } ?: emptyList() },
        write = { config = config.copy(rotationWidget = it.firstOrNull()) },
        persist = { save() },
        lock = rotationLock,
    )

    /**
     * Placement store for the multi-instance single-dye progress widgets (PBI 34). Mirrors
     * [gifPlacements] over the `dyeProgressWidgets` config slice; the dye-progress subsystem
     * adds/removes/edits widgets exclusively through this.
     */
    val dyeProgressPlacements: PlacementStore<DyeProgressWidgetConfig> = PlacementStore(
        read = { config.dyeProgressWidgets },
        write = { config = config.copy(dyeProgressWidgets = it) },
        persist = { save() },
        lock = dyeProgressLock,
    )

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
     * Get the last-known captured dye rotation, or null if none has been captured yet.
     */
    fun getDyeRotation(): DyeRotation? = config.dyeRotation

    /**
     * Persist a newly captured dye rotation, replacing any prior one (last-write-wins). Writes
     * through the standard [update] path so it is saved to disk immediately.
     */
    fun updateDyeRotation(rotation: DyeRotation) {
        update { it.copy(dyeRotation = rotation) }
    }

    /**
     * Resets the configuration to default values and saves to disk.
     */
    fun reset() {
        config = ModConfig()
        save()
        DyeTrackerMod.info("Configuration reset to defaults")
    }

    private fun ensureConfigDirExists() {
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
        }
    }
}
