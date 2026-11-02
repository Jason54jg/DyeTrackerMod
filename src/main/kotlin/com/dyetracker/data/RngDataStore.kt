package com.dyetracker.data

import com.dyetracker.DyeTrackerMod
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

    // PBI 66: Holly Dye gift-reward key limits. They keep a synced payload inside the backend's
    // limits (500 keys, 120-char keys; a rarity is at most 15 chars, so a 100-char reward fits).
    const val HOLLY_MAX_REWARD_KEYS = 500
    const val HOLLY_MAX_REWARD_TEXT_LENGTH = 100
    const val HOLLY_OVERFLOW_KEY = "OTHER|other"

    @Volatile
    private var mineshaftPity: MineshaftPity? = null

    @Volatile
    private var archfiendDye: ArchfiendDyeData? = null

    @Volatile
    private var copperDye: CopperDyeData? = null

    @Volatile
    private var nyanzaDye: NyanzaDyeData? = null

    @Volatile
    private var treasureDye: TreasureDyeData? = null

    @Volatile
    private var fossilDye: FossilDyeData? = null

    @Volatile
    private var darkPurpleDye: DarkPurpleDyeData? = null

    @Volatile
    private var hollyDye: HollyDyeData? = null

    @Volatile
    private var mochaDye: MochaDyeData? = null

    @Volatile
    private var dyeCollection: DyeCollection? = null

    // PBI 42: persisted union of every visitor seen in the Logbook, keyed by name. The per-tier
    // copperDye snapshot is derived from this; merging by name keeps it idempotent across paginated
    // page-turns, re-opens, and restarts. Local-only — never included in the sync request.
    @Volatile
    private var visitorLogbook: VisitorLogbookData? = null

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
            mineshaftPity = loadedData.mineshaftPity
            archfiendDye = loadedData.archfiendDye
            copperDye = loadedData.copperDye
            nyanzaDye = loadedData.nyanzaDye
            treasureDye = loadedData.treasureDye
            fossilDye = loadedData.fossilDye
            darkPurpleDye = loadedData.darkPurpleDye
            hollyDye = loadedData.hollyDye
            mochaDye = loadedData.mochaDye
            dyeCollection = loadedData.dyeCollection
            visitorLogbook = loadedData.visitorLogbook

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
     * Merge a Visitor's Logbook capture into the persisted visitor union and re-derive the per-tier
     * Copper "visitors seen" snapshot (PBI 42).
     *
     * The Logbook is paginated and Hypixel re-creates the screen on each page-turn, so a single
     * capture only ever sees one page's worth of visitors. Merging by visitor name (last-seen wins)
     * accumulates them across page-turns AND across sessions/restarts; because `timesVisited` is an
     * absolute per-visitor count, re-scanning a visitor overwrites rather than adds — so totals are
     * idempotent and never double-count. The per-tier [CopperDyeData.visitorsSeen] snapshot is then
     * derived from the FULL union, so viewing only part of the Logbook never shrinks it.
     *
     * Empty input is a no-op (a mis-identified or half-loaded screen must not clobber prior data).
     *
     * @param sessionVisitors visitors parsed this capture, keyed by [VisitorEntry.name].
     */
    fun mergeVisitorsSeen(sessionVisitors: Map<String, VisitorEntry>) {
        if (sessionVisitors.isEmpty()) {
            return
        }
        val merged = (visitorLogbook?.visitors ?: emptyMap()).toMutableMap()
        merged.putAll(sessionVisitors) // dedup by name; last-seen wins (idempotent absolute counts)
        visitorLogbook = VisitorLogbookData(visitors = merged)
        copperDye = CopperDyeData(visitorsSeen = aggregateVisitorsSeenByTier(merged.values))
        notifyListeners()
    }

    /**
     * Get the current persisted visitor-union snapshot (PBI 42), or null if none captured yet.
     */
    fun getVisitorLogbook(): VisitorLogbookData? = visitorLogbook

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
     * Increment the per-tier fishing-treasure catch count for Treasure Dye (PBI 58).
     *
     * Each treasure caught is a discrete event (like an Archfiend dice roll), so this is an
     * increment counter — not a full-snapshot replace like the Copper Logbook. The capture
     * handler (58-6) calls this once per observed `<TIER> ... CATCH!` chat line.
     */
    fun incrementTreasureCatch(quality: TreasureQuality) {
        val current = treasureDye ?: TreasureDyeData()
        treasureDye = when (quality) {
            TreasureQuality.GOOD -> current.copy(goodCatches = current.goodCatches + 1)
            TreasureQuality.GREAT -> current.copy(greatCatches = current.greatCatches + 1)
            TreasureQuality.OUTSTANDING -> current.copy(outstandingCatches = current.outstandingCatches + 1)
        }
        notifyListeners()
    }

    /**
     * Increment the Suspicious Scrap excavation count for Fossil Dye (PBI 66).
     *
     * Each excavation is a discrete event, so this is an increment counter. The capture handler
     * calls this once per observed `EXCAVATION COMPLETE` chat block.
     */
    fun incrementExcavation() {
        val current = fossilDye ?: FossilDyeData()
        fossilDye = current.copy(excavations = current.excavations + 1)
        notifyListeners()
    }

    /**
     * Increment the Dark Auctions attended count for Dark Purple Dye (PBI 66).
     *
     * Each auction attended is a discrete event, so this is an increment counter. The capture
     * handler calls this once per observed `Transferring to Dark Auction...` chat line.
     */
    fun incrementDarkAuction() {
        val current = darkPurpleDye ?: DarkPurpleDyeData()
        darkPurpleDye = current.copy(auctionsAttended = current.auctionsAttended + 1)
        notifyListeners()
    }

    /**
     * Count one gift reward chat line for Holly Dye (PBI 66).
     *
     * The key is `"<rarity>|<reward>"` with the reward cut to [HOLLY_MAX_REWARD_TEXT_LENGTH] chars,
     * kept raw (the backend normalises it). Once `HOLLY_MAX_REWARD_KEYS - 1` keys other than
     * [HOLLY_OVERFLOW_KEY] are stored, a new key counts under [HOLLY_OVERFLOW_KEY] instead, so the
     * gift still counts and the map never exceeds [HOLLY_MAX_REWARD_KEYS] keys (the backend rejects a
     * sync with more). An existing key keeps incrementing itself.
     */
    fun incrementGiftReward(rarity: String, reward: String) {
        val current = hollyDye?.giftRewards ?: emptyMap()
        val rewardKey = "$rarity|${reward.take(HOLLY_MAX_REWARD_TEXT_LENGTH)}"
        val realKeys = current.keys.count { it != HOLLY_OVERFLOW_KEY }
        val isNewRealKey = rewardKey !in current && rewardKey != HOLLY_OVERFLOW_KEY
        val key = if (isNewRealKey && realKeys >= HOLLY_MAX_REWARD_KEYS - 1) {
            HOLLY_OVERFLOW_KEY
        } else {
            rewardKey
        }
        hollyDye = HollyDyeData(giftRewards = current + (key to ((current[key] ?: 0) + 1)))
        notifyListeners()
    }

    /**
     * Count one potion of [tier] taken out of the Brewing Stand for Mocha Dye (PBI 66).
     *
     * Each brewed potion is a discrete event, so this is a per-tier increment counter. The
     * Brewing Stand watcher calls this once per finished potion that leaves the stand.
     */
    fun incrementBrewedPotion(tier: PotionTier) {
        val current = mochaDye?.potionsByTier ?: emptyMap()
        mochaDye = MochaDyeData(potionsByTier = current + (tier to ((current[tier] ?: 0) + 1)))
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
            mineshaftPity = mineshaftPity,
            archfiendDye = archfiendDye,
            copperDye = copperDye,
            nyanzaDye = nyanzaDye,
            treasureDye = treasureDye,
            fossilDye = fossilDye,
            darkPurpleDye = darkPurpleDye,
            hollyDye = hollyDye,
            mochaDye = mochaDye,
            dyeCollection = dyeCollection,
            visitorLogbook = visitorLogbook
        )
    }

    /**
     * Clear all stored RNG data.
     */
    fun clear() {
        mineshaftPity = null
        archfiendDye = null
        copperDye = null
        nyanzaDye = null
        treasureDye = null
        fossilDye = null
        darkPurpleDye = null
        hollyDye = null
        mochaDye = null
        dyeCollection = null
        visitorLogbook = null
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
