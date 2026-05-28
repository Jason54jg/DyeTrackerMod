package com.dyetracker.data

import com.dyetracker.DyeTrackerMod
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Manages persistence of RNG data to local JSON storage.
 *
 * Data is stored in the game's config directory under
 * `config/dyetracker/data.json`.
 */
object DataPersistence {
    private const val CONFIG_DIR_NAME = "dyetracker"
    private const val DATA_FILE_NAME = "data.json"
    private const val SAVE_DEBOUNCE_MS = 2000L

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "DyeTracker-Persistence").apply {
            isDaemon = true
        }
    }

    @Volatile
    private var pendingSave: ScheduledFuture<*>? = null

    private val saveLock = Any()

    /**
     * The path to the data file.
     */
    val dataPath: Path by lazy {
        FabricLoader.getInstance().configDir
            .resolve(CONFIG_DIR_NAME)
            .resolve(DATA_FILE_NAME)
    }

    /**
     * The path to the configuration directory.
     */
    private val configDir: Path by lazy {
        FabricLoader.getInstance().configDir.resolve(CONFIG_DIR_NAME)
    }

    /**
     * Loads RNG data from disk.
     *
     * @return The loaded PlayerRngData, or null if file doesn't exist or is corrupted.
     */
    fun load(): PlayerRngData? {
        return try {
            DyeTrackerMod.info("Data path: {}", dataPath.toAbsolutePath())

            if (Files.exists(dataPath)) {
                DyeTrackerMod.info("Loading existing RNG data from {}", dataPath.toAbsolutePath())
                val content = Files.readString(dataPath)
                val data = json.decodeFromString<PlayerRngData>(content)
                DyeTrackerMod.info("RNG data loaded: {} dungeon meters", data.dungeonMeters.size)
                data
            } else {
                DyeTrackerMod.info("No existing RNG data file found at {}", dataPath.toAbsolutePath())
                null
            }
        } catch (e: Exception) {
            DyeTrackerMod.error("Failed to load RNG data", e)
            null
        }
    }

    /**
     * Saves RNG data to disk immediately.
     *
     * @param data The PlayerRngData to save.
     */
    fun saveImmediate(data: PlayerRngData) {
        synchronized(saveLock) {
            cancelPendingSave()
            doSave(data)
        }
    }

    /**
     * Schedules a debounced save of RNG data to disk.
     * Multiple rapid calls will be coalesced into a single save after the debounce period.
     *
     * @param data The PlayerRngData to save.
     */
    fun saveDebounced(data: PlayerRngData) {
        synchronized(saveLock) {
            cancelPendingSave()
            pendingSave = scheduler.schedule({
                synchronized(saveLock) {
                    doSave(data)
                    pendingSave = null
                }
            }, SAVE_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * Flush any pending save immediately.
     * Call this during shutdown to ensure data is persisted.
     */
    fun flush(data: PlayerRngData) {
        synchronized(saveLock) {
            if (pendingSave != null) {
                cancelPendingSave()
                doSave(data)
            }
        }
    }

    private fun cancelPendingSave() {
        pendingSave?.cancel(false)
        pendingSave = null
    }

    private fun doSave(data: PlayerRngData) {
        try {
            ensureConfigDirExists()
            val content = json.encodeToString(data)
            Files.writeString(dataPath, content)
            DyeTrackerMod.debug("RNG data saved to {}", dataPath.toAbsolutePath())
        } catch (e: Exception) {
            DyeTrackerMod.error("Failed to save RNG data", e)
        }
    }

    private fun ensureConfigDirExists() {
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
        }
    }
}
