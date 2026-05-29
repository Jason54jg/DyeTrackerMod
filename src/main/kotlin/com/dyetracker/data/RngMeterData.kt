package com.dyetracker.data

import kotlinx.serialization.Serializable

// PBI 47: SlayerType + SlayerRngMeter removed — slayer dyes are estimated from per-tier
// boss kill counts on the backend, so the mod no longer models slayer RNG meters.

/**
 * Dungeon floors that have RNG meters.
 */
@Serializable
enum class DungeonFloor {
    M5,
    M7
}

/**
 * RNG meter data for a dungeon floor.
 */
@Serializable
data class DungeonRngMeter(
    val floor: DungeonFloor,
    val storedXp: Long,
    val selectedItem: String? = null,
    val goalXp: Long? = null
)

/**
 * RNG meter data for Crystal Hollows Nucleus.
 */
@Serializable
data class NucleusRngMeter(
    val storedXp: Long,
    val selectedItem: String? = null,
    val goalXp: Long? = null
)

/**
 * RNG meter data for Experimentation Table.
 */
@Serializable
data class ExperimentationRngMeter(
    val storedXp: Long,
    val selectedItem: String? = null,
    val goalXp: Long? = null
)

/**
 * Mineshaft pity counter (0-2000).
 */
@Serializable
data class MineshaftPity(
    val pityValue: Int
)

/**
 * Archfiend Dye roll tracking data.
 * Tracks dice rolls for both High Class Archfiend Dice and regular Archfiend Dice.
 */
@Serializable
data class ArchfiendDyeData(
    val highClassDiceRolls: Int = 0,
    val archfiendDiceRolls: Int = 0
)

/**
 * Garden visitor rarity tiers.
 * Each tier has different Copper Dye drop rates.
 */
@Serializable
enum class VisitorRarity {
    UNCOMMON,   // 1/100,000 drop rate
    RARE,       // 1/20,000 drop rate
    LEGENDARY,  // 1/4,000 drop rate
    MYTHIC,     // 1/800 drop rate
    SPECIAL     // 1/500 drop rate
}

/**
 * Copper Dye tracking data.
 *
 * PBI 42: holds a full snapshot of every Garden visitor seen, grouped by rarity tier,
 * read from the in-game Visitor's Logbook GUI (not just accepted offers). The
 * `visitorsSeen` JSON key matches the backend `copperDyeSchema`.
 */
@Serializable
data class CopperDyeData(
    val visitorsSeen: Map<VisitorRarity, Int> = emptyMap()
)

/**
 * A single Garden visitor as parsed from the Visitor's Logbook (PBI 42).
 *
 * [timesVisited] is the authoritative "seen" count for this visitor; [name] is the dedup key used
 * to merge re-scans across pages and capture sessions. Lives in the data layer (rather than the
 * parsing layer) because it is also the persisted unit of [VisitorLogbookData].
 */
@Serializable
data class VisitorEntry(
    val name: String,
    val rarity: VisitorRarity,
    val timesVisited: Int
)

/**
 * Persisted union of every Garden visitor ever seen in the Visitor's Logbook, keyed by visitor
 * [VisitorEntry.name] (PBI 42).
 *
 * The Logbook is paginated and Hypixel re-creates the screen on every page-turn, so any single
 * capture only ever sees one page. This union accumulates visitors across page-turns AND across
 * sessions/restarts. Because [VisitorEntry.timesVisited] is an absolute per-visitor count, merging
 * by name (last-seen wins) is idempotent — re-scanning a visitor overwrites rather than adds, so
 * totals never double-count. The synced per-tier [CopperDyeData.visitorsSeen] snapshot is derived
 * from this union, so it never shrinks when only part of the Logbook is viewed.
 *
 * Local-only: persisted to disk but never sent to the backend (the wire contract stays per-tier).
 */
@Serializable
data class VisitorLogbookData(
    val visitors: Map<String, VisitorEntry> = emptyMap()
)

/**
 * Sum [VisitorEntry.timesVisited] grouped by rarity tier — the per-tier "visitors seen" totals
 * written to the Copper snapshot. Empty input yields an empty map.
 */
fun aggregateVisitorsSeenByTier(visitors: Collection<VisitorEntry>): Map<VisitorRarity, Int> {
    val totals = mutableMapOf<VisitorRarity, Int>()
    for (entry in visitors) {
        totals[entry.rarity] = (totals[entry.rarity] ?: 0) + entry.timesVisited
    }
    return totals.toMap()
}

/**
 * Nyanza Dye tracking data.
 * Tracks total mining commissions completed (absolute count from Commission Milestones).
 */
@Serializable
data class NyanzaDyeData(
    val commissionsCompleted: Int = 0
)

/**
 * Composite data class holding all RNG data for a player.
 */
@Serializable
data class PlayerRngData(
    val dungeonMeters: Map<DungeonFloor, DungeonRngMeter> = emptyMap(),
    val nucleusMeter: NucleusRngMeter? = null,
    val experimentationMeter: ExperimentationRngMeter? = null,
    val mineshaftPity: MineshaftPity? = null,
    val archfiendDye: ArchfiendDyeData? = null,
    val copperDye: CopperDyeData? = null,
    val nyanzaDye: NyanzaDyeData? = null,
    val dyeCollection: DyeCollection? = null,
    // PBI 42: local-only persisted union of all visitors seen in the Logbook (never synced; the
    // per-tier copperDye snapshot is derived from it). Lets the per-tier total survive page-turns,
    // re-opens, and restarts without double-counting.
    val visitorLogbook: VisitorLogbookData? = null
) {
    /**
     * Returns true if any RNG data has been captured.
     */
    fun hasData(): Boolean {
        return dungeonMeters.isNotEmpty() ||
            nucleusMeter != null ||
            experimentationMeter != null ||
            mineshaftPity != null ||
            archfiendDye != null ||
            copperDye != null ||
            nyanzaDye != null ||
            dyeCollection != null ||
            visitorLogbook != null
    }
}
