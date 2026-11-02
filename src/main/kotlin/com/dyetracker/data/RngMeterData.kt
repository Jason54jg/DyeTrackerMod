package com.dyetracker.data

import kotlinx.serialization.Serializable

// PBI 47: SlayerType + SlayerRngMeter removed — slayer dyes are estimated from per-tier
// boss kill counts on the backend, so the mod no longer models slayer RNG meters.
// PBI 63: the dungeon (M5/M7), Crystal Nucleus and Experimentation Table RNG-meter models were
// removed the same way — the mod no longer captures those RNG meters.

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
 * Treasure-quality tiers for fishing-treasure catches (PBI 58).
 *
 * Matches the in-game catch-quality chat line `<TIER> ... CATCH!` (`⛃ GOOD/GREAT/OUTSTANDING
 * CATCH!`) parsed by the capture handler, and the per-tier count fields on [TreasureDyeData] /
 * the backend `treasureDyeSchema`.
 */
enum class TreasureQuality {
    GOOD,
    GREAT,
    OUTSTANDING
}

/**
 * Treasure Dye tracking data (PBI 58).
 *
 * Per-tier counts of fishing treasures caught — Good / Great / Outstanding — captured from the
 * in-game catch-quality chat line. Each catch is a discrete increment (unlike the Copper
 * Logbook full-snapshot replace), because the Treasure Dye rolls per catch at a per-tier rate.
 * The JSON keys (`goodCatches`/`greatCatches`/`outstandingCatches`) follow the property names,
 * matching the backend `treasureDyeSchema` exactly (PBI 58 task 58-3).
 */
@Serializable
data class TreasureDyeData(
    val goodCatches: Int = 0,
    val greatCatches: Int = 0,
    val outstandingCatches: Int = 0
)

/**
 * Fossil Dye tracking data (PBI 66).
 *
 * Count of Suspicious Scraps excavated, captured from the in-game `EXCAVATION COMPLETE` chat block.
 * Each excavation is a discrete increment (like a Treasure catch), because the Fossil Dye rolls
 * once per excavation. The JSON key (`excavations`) follows the property name, matching the
 * backend contract.
 */
@Serializable
data class FossilDyeData(
    val excavations: Int = 0
)

/**
 * Dark Purple Dye tracking data (PBI 66).
 *
 * Count of Dark Auctions attended, captured from the in-game `Transferring to Dark Auction...`
 * chat line. Each auction attended is a discrete increment. The JSON key (`auctionsAttended`)
 * follows the property name, matching the backend contract.
 */
@Serializable
data class DarkPurpleDyeData(
    val auctionsAttended: Int = 0
)

/**
 * Holly Dye tracking data (PBI 66).
 *
 * Counts of gift reward chat lines (`<RARITY>! <reward> gift with <player>!`), one per gift given
 * or opened. Keys are `"<RARITY>|<reward text>"`, kept raw: the chat line does not say the gift's
 * color, so the backend normalises the keys and estimates the red share. The JSON key
 * (`giftRewards`) follows the property name, matching the backend contract.
 */
@Serializable
data class HollyDyeData(
    val giftRewards: Map<String, Int> = emptyMap()
)

/**
 * Potion tiers I-VIII, read from a brewed potion's name (`Regeneration VII Potion`) (PBI 66).
 *
 * Used as the key of [MochaDyeData.potionsByTier], so it serialises by enum name (`"VII"`).
 */
@Serializable
enum class PotionTier { I, II, III, IV, V, VI, VII, VIII }

/**
 * Mocha Dye tracking data (PBI 66).
 *
 * Per-tier counts of potions taken out of the Brewing Stand after brewing. The Mocha Dye rolls
 * once per brewed potion at a per-tier rate. Serialises like [CopperDyeData.visitorsSeen], with
 * tier names as keys (`{"potionsByTier":{"VII":2}}`), matching the backend contract.
 */
@Serializable
data class MochaDyeData(
    val potionsByTier: Map<PotionTier, Int> = emptyMap()
)

/**
 * Composite data class holding all RNG data for a player.
 */
@Serializable
data class PlayerRngData(
    val mineshaftPity: MineshaftPity? = null,
    val archfiendDye: ArchfiendDyeData? = null,
    val copperDye: CopperDyeData? = null,
    val nyanzaDye: NyanzaDyeData? = null,
    val treasureDye: TreasureDyeData? = null,
    val fossilDye: FossilDyeData? = null,
    val darkPurpleDye: DarkPurpleDyeData? = null,
    val hollyDye: HollyDyeData? = null,
    val mochaDye: MochaDyeData? = null,
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
        return mineshaftPity != null ||
            archfiendDye != null ||
            copperDye != null ||
            nyanzaDye != null ||
            treasureDye != null ||
            fossilDye != null ||
            darkPurpleDye != null ||
            hollyDye != null ||
            mochaDye != null ||
            dyeCollection != null ||
            visitorLogbook != null
    }
}
