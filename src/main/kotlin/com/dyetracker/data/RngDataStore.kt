package com.dyetracker.data

import com.dyetracker.DyeTrackerMod
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Listener interface for RNG data changes.
 */
fun interface RngDataChangeListener {
    fun onDataChanged(data: PlayerRngData)
}

/**
 * Thread-safe singleton object for storing captured RNG data in memory.
 * This store is updated by various event handlers and read by the show command.
 */
object RngDataStore {

    private val slayerMeters = ConcurrentHashMap<SlayerType, SlayerRngMeter>()
    private val dungeonMeters = ConcurrentHashMap<DungeonFloor, DungeonRngMeter>()

    @Volatile
    private var nucleusMeter: NucleusRngMeter? = null

    @Volatile
    private var experimentationMeter: ExperimentationRngMeter? = null

    @Volatile
    private var mineshaftPity: MineshaftPity? = null

    @Volatile
    private var archfiendDye: ArchfiendDyeData? = null

    private val listeners = CopyOnWriteArrayList<RngDataChangeListener>()

    @Volatile
    private var initialized = false

    /**
     * Initialize the data store by loading persisted data from disk.
     * Should be called once during mod initialization.
     */
    fun init() {
        if (initialized) {
            DyeTrackerMod.warn("RngDataStore already initialized")
            return
        }

        val loadedData = DataPersistence.load()
        if (loadedData != null) {
            // Populate internal state from loaded data
            loadedData.slayerMeters.forEach { (type, meter) ->
                slayerMeters[type] = meter
            }
            loadedData.dungeonMeters.forEach { (floor, meter) ->
                dungeonMeters[floor] = meter
            }
            nucleusMeter = loadedData.nucleusMeter
            experimentationMeter = loadedData.experimentationMeter
            mineshaftPity = loadedData.mineshaftPity
            archfiendDye = loadedData.archfiendDye

            DyeTrackerMod.info("RNG data loaded from disk")
        }

        // Register persistence listener
        addListener { data ->
            DataPersistence.saveDebounced(data)
        }

        initialized = true
        DyeTrackerMod.info("RngDataStore initialized")
    }

    /**
     * Add a listener to be notified when RNG data changes.
     */
    fun addListener(listener: RngDataChangeListener) {
        listeners.add(listener)
    }

    /**
     * Remove a listener.
     */
    fun removeListener(listener: RngDataChangeListener) {
        listeners.remove(listener)
    }

    /**
     * Notify all listeners of data change.
     */
    private fun notifyListeners() {
        val data = getData()
        listeners.forEach { listener ->
            try {
                listener.onDataChanged(data)
            } catch (e: Exception) {
                DyeTrackerMod.error("Error notifying RNG data listener", e)
            }
        }
    }

    /**
     * Update the stored XP for a slayer type.
     * Preserves any existing selection data.
     */
    fun updateSlayerXp(slayerType: SlayerType, xp: Long) {
        slayerMeters.compute(slayerType) { _, existing ->
            if (existing != null) {
                existing.copy(storedXp = xp)
            } else {
                SlayerRngMeter(slayerType = slayerType, storedXp = xp)
            }
        }
        notifyListeners()
    }

    /**
     * Update the selected item for a slayer type.
     * Preserves any existing XP data.
     */
    fun updateSlayerSelection(slayerType: SlayerType, item: String, goalXp: Long) {
        slayerMeters.compute(slayerType) { _, existing ->
            if (existing != null) {
                existing.copy(selectedItem = item, goalXp = goalXp)
            } else {
                SlayerRngMeter(slayerType = slayerType, storedXp = 0, selectedItem = item, goalXp = goalXp)
            }
        }
        notifyListeners()
    }

    /**
     * Update the dungeon RNG meter for a floor.
     */
    fun updateDungeonMeter(floor: DungeonFloor, xp: Long, item: String?, goalXp: Long?) {
        dungeonMeters[floor] = DungeonRngMeter(
            floor = floor,
            storedXp = xp,
            selectedItem = item,
            goalXp = goalXp
        )
        notifyListeners()
    }

    /**
     * Update the nucleus RNG meter.
     */
    fun updateNucleusMeter(xp: Long, item: String?, goalXp: Long?) {
        nucleusMeter = NucleusRngMeter(
            storedXp = xp,
            selectedItem = item,
            goalXp = goalXp
        )
        notifyListeners()
    }

    /**
     * Update the experimentation table RNG meter.
     */
    fun updateExperimentationMeter(xp: Long, item: String?, goalXp: Long?) {
        experimentationMeter = ExperimentationRngMeter(
            storedXp = xp,
            selectedItem = item,
            goalXp = goalXp
        )
        notifyListeners()
    }

    /**
     * Update the mineshaft pity value.
     */
    fun updateMineshaftPity(pity: Int) {
        mineshaftPity = MineshaftPity(pityValue = pity)
        notifyListeners()
    }

    /**
     * Increment the High Class Archfiend Dice roll count.
     */
    fun incrementHighClassDiceRoll() {
        val current = archfiendDye ?: ArchfiendDyeData()
        archfiendDye = current.copy(highClassDiceRolls = current.highClassDiceRolls + 1)
        notifyListeners()
    }

    /**
     * Increment the regular Archfiend Dice roll count.
     */
    fun incrementArchfiendDiceRoll() {
        val current = archfiendDye ?: ArchfiendDyeData()
        archfiendDye = current.copy(archfiendDiceRolls = current.archfiendDiceRolls + 1)
        notifyListeners()
    }

    /**
     * Get an immutable snapshot of all RNG data.
     */
    fun getData(): PlayerRngData {
        return PlayerRngData(
            slayerMeters = slayerMeters.toMap(),
            dungeonMeters = dungeonMeters.toMap(),
            nucleusMeter = nucleusMeter,
            experimentationMeter = experimentationMeter,
            mineshaftPity = mineshaftPity,
            archfiendDye = archfiendDye
        )
    }

    /**
     * Clear all stored RNG data.
     */
    fun clear() {
        slayerMeters.clear()
        dungeonMeters.clear()
        nucleusMeter = null
        experimentationMeter = null
        mineshaftPity = null
        archfiendDye = null
        notifyListeners()
    }

    /**
     * Flush any pending persistence operations.
     * Call this during shutdown.
     */
    fun flush() {
        DataPersistence.flush(getData())
    }
}
