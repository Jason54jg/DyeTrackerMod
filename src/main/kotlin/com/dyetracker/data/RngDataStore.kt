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

    private val dungeonMeters = ConcurrentHashMap<DungeonFloor, DungeonRngMeter>()

    @Volatile
    private var nucleusMeter: NucleusRngMeter? = null

    @Volatile
    private var experimentationMeter: ExperimentationRngMeter? = null

    @Volatile
    private var mineshaftPity: MineshaftPity? = null

    @Volatile
    private var archfiendDye: ArchfiendDyeData? = null

    @Volatile
    private var copperDye: CopperDyeData? = null

    @Volatile
    private var nyanzaDye: NyanzaDyeData? = null

    @Volatile
    private var dyeCollection: DyeCollection? = null

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
            loadedData.dungeonMeters.forEach { (floor, meter) ->
                dungeonMeters[floor] = meter
            }
            nucleusMeter = loadedData.nucleusMeter
            experimentationMeter = loadedData.experimentationMeter
            mineshaftPity = loadedData.mineshaftPity
            archfiendDye = loadedData.archfiendDye
            copperDye = loadedData.copperDye
            nyanzaDye = loadedData.nyanzaDye
            dyeCollection = loadedData.dyeCollection

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
     * Increment the visitor accept count for a specific rarity tier.
     */
    fun incrementVisitorAccept(rarity: VisitorRarity) {
        val current = copperDye ?: CopperDyeData()
        val currentCount = current.visitorAccepts[rarity] ?: 0
        val updatedAccepts = current.visitorAccepts.toMutableMap()
        updatedAccepts[rarity] = currentCount + 1
        copperDye = current.copy(visitorAccepts = updatedAccepts)
        notifyListeners()
    }

    /**
     * Update the total commissions completed count for Nyanza Dye.
     * Only updates if the new value is higher than the current value (absolute count).
     */
    fun updateCommissionsCompleted(count: Int) {
        val current = nyanzaDye
        if (current != null && count <= current.commissionsCompleted) {
            return
        }
        nyanzaDye = NyanzaDyeData(commissionsCompleted = count)
        notifyListeners()
    }

    /**
     * Update the dye collection from a Vincent inventory scan.
     * Replaces the entire collection with the latest scan results.
     */
    fun updateDyeCollection(dyes: List<DroppedDye>) {
        dyeCollection = DyeCollection(
            profileId = "server-resolved", // Profile is resolved server-side by the API
            dyes = dyes,
            lastUpdated = System.currentTimeMillis()
        )
        notifyListeners()
    }

    /**
     * Get the current dye collection snapshot.
     */
    fun getDyeCollection(): DyeCollection? = dyeCollection

    /**
     * Get an immutable snapshot of all RNG data.
     */
    fun getData(): PlayerRngData {
        return PlayerRngData(
            dungeonMeters = dungeonMeters.toMap(),
            nucleusMeter = nucleusMeter,
            experimentationMeter = experimentationMeter,
            mineshaftPity = mineshaftPity,
            archfiendDye = archfiendDye,
            copperDye = copperDye,
            nyanzaDye = nyanzaDye,
            dyeCollection = dyeCollection
        )
    }

    /**
     * Clear all stored RNG data.
     */
    fun clear() {
        dungeonMeters.clear()
        nucleusMeter = null
        experimentationMeter = null
        mineshaftPity = null
        archfiendDye = null
        copperDye = null
        nyanzaDye = null
        dyeCollection = null
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
