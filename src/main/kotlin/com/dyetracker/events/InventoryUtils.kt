package com.dyetracker.events

import com.dyetracker.DyeTrackerMod
import com.dyetracker.data.DroppedDye
import com.dyetracker.data.DungeonFloor
import com.dyetracker.data.DyeRotation
import com.dyetracker.data.VisitorEntry
import com.dyetracker.data.VisitorRarity
//? if >=26.1 {
/*import net.minecraft.world.Container as Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.inventory.Slot
import net.minecraft.network.chat.Component as Text
*///?} else {
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import net.minecraft.text.Text
//?}

/**
 * Inventory type detection results.
 */
sealed class InventoryType {
    // PBI 47: SlayerRngMeter removed — slayer dyes are estimated from per-tier boss kills on the backend.
    data class DungeonRngMeter(val floor: DungeonFloor) : InventoryType()
    data object NucleusRngMeter : InventoryType()
    data object ExperimentationRngMeter : InventoryType()
    data object CommissionMilestones : InventoryType()
    data object VincentDyeCollection : InventoryType()
    data object VincentDyeRotation : InventoryType()
    // PBI 42: Garden "Visitor's Logbook" GUI — Copper Dye is sourced from every visitor seen.
    data object VisitorLogbook : InventoryType()
}

/**
 * Result from parsing a selected item's lore.
 */
data class SelectedItemInfo(
    val itemName: String,
    val goalXp: Long?
)


/**
 * Utility functions for detecting RNG meter inventory types and parsing item lore.
 */
object InventoryUtils {

    // Yarn's Slot.stack/Slot.inventory and ItemStack.name were renamed to getItem()/container and
    // getHoverName() on the official mappings 26.x uses; these shims keep every call site below
    // unchanged instead of touching each one individually.
    //? if >=26.1 {
    /*private val Slot.stack: ItemStack get() = item
    private val Slot.inventory: Inventory get() = container
    private val ItemStack.name: Text get() = hoverName
    *///?}

    // Dungeon RNG meter title patterns
    private const val M5_TITLE = "Master Mode Floor V"
    private const val M7_TITLE = "Master Mode Floor VII"

    // Other RNG meter titles
    private const val NUCLEUS_TITLE = "Crystal Hollows"
    private const val EXPERIMENTATION_TITLE = "Experimentation Table"
    private const val RNG_METER_SUFFIX = "RNG Meter"

    // Vincent dye compendium GUI title
    private const val VINCENT_TITLE = "Dye Compendium"

    // Vincent current dye rotation GUI title (a distinct chest GUI; exact match so it does not
    // collide with the "Dye Compendium" compendium screen).
    private const val ROTATION_TITLE = "Dyes"

    // Commission Milestones GUI title (the sub-screen reached from the main Commissions menu;
    // the "Milestone I Rewards" item inside it shows "X/5" stably even for maxed players).
    private const val COMMISSION_MILESTONES_TITLE = "Commission Milestones"

    // Visitor's Logbook GUI title (PBI 42). Matched with `contains` (not exact) so a paginated
    // title carrying a page-indicator suffix still resolves; no other screen contains this phrase.
    private const val VISITOR_LOGBOOK_TITLE = "Visitor's Logbook"

    // Item name inside the Commission Milestones GUI whose lore carries the absolute completed
    // count. Milestone I's threshold (5) never changes, so the "X/5" pattern is always present.
    private const val MILESTONE_I_REWARDS_ITEM = "Milestone I Rewards"

    // Pattern to extract completed count from Milestone I Rewards lore (e.g., "208/5")
    private val MILESTONE_COUNT_PATTERN = Regex("""(\d[\d,]*)/[\d,]+""")

    // Lore patterns - matches "31,900/75M" or "1,812/8.3k" format
    private val XP_PROGRESS_PATTERN = Regex("""([\d,.]+)/([\d,.]+[KkMm]?)""")
    // Matches "Stored Dungeon Score: 804" or "Stored Nucleus XP: 1,000"
    private val STORED_XP_PATTERN = Regex("""Stored (?:Dungeon Score|Nucleus XP|.*XP): ([\d,.]+)""")

    /**
     * Detect the inventory type from a screen title.
     * Returns null if not a recognized RNG meter screen.
     */
    fun detectInventoryType(title: String): InventoryType? {
        val cleanTitle = stripFormatting(title)

        // Check for Vincent dye collection GUI
        if (cleanTitle == VINCENT_TITLE) {
            return InventoryType.VincentDyeCollection
        }

        // Check for Vincent current dye rotation GUI (exact match, distinct from the compendium)
        if (cleanTitle == ROTATION_TITLE) {
            return InventoryType.VincentDyeRotation
        }

        // Check for Commission Milestones GUI
        if (cleanTitle == COMMISSION_MILESTONES_TITLE) {
            return InventoryType.CommissionMilestones
        }

        // Check for the Garden Visitor's Logbook GUI (PBI 42)
        if (cleanTitle.contains(VISITOR_LOGBOOK_TITLE)) {
            return InventoryType.VisitorLogbook
        }

        // Check for experimentation table first (uses "RNG" not "RNG Meter")
        if (cleanTitle.contains(EXPERIMENTATION_TITLE) && cleanTitle.contains("RNG")) {
            return InventoryType.ExperimentationRngMeter
        }

        // Check if it's an RNG Meter inventory (for all other types)
        if (!cleanTitle.contains(RNG_METER_SUFFIX)) {
            return null
        }

        // Check for dungeon floors
        when {
            cleanTitle.contains(M5_TITLE) -> return InventoryType.DungeonRngMeter(DungeonFloor.M5)
            cleanTitle.contains(M7_TITLE) -> return InventoryType.DungeonRngMeter(DungeonFloor.M7)
        }

        // Check for nucleus
        if (cleanTitle.contains(NUCLEUS_TITLE)) {
            return InventoryType.NucleusRngMeter
        }

        return null
    }

    /**
     * Parse the dungeon floor from an inventory title.
     * Returns null if not a dungeon RNG meter.
     */
    fun parseDungeonFloor(title: String): DungeonFloor? {
        val cleanTitle = stripFormatting(title)
        return when {
            cleanTitle.contains(M5_TITLE) -> DungeonFloor.M5
            cleanTitle.contains(M7_TITLE) -> DungeonFloor.M7
            else -> null
        }
    }

    /**
     * Check if an inventory title is a Nucleus RNG meter.
     */
    fun isNucleusRngMeter(title: String): Boolean {
        val cleanTitle = stripFormatting(title)
        return cleanTitle.contains(NUCLEUS_TITLE) && cleanTitle.contains(RNG_METER_SUFFIX)
    }

    /**
     * Check if an inventory title is an Experimentation RNG meter.
     * Note: Experimentation Table uses "RNG" instead of "RNG Meter" in its title.
     */
    fun isExperimentationRngMeter(title: String): Boolean {
        val cleanTitle = stripFormatting(title)
        return cleanTitle.contains(EXPERIMENTATION_TITLE) && cleanTitle.contains("RNG")
    }

    // ==================== Lore Parsing Functions ====================

    /**
     * Check if an item's lore indicates it is selected.
     */
    fun isSelected(lore: List<Text>): Boolean {
        return lore.any { line ->
            val cleanLine = stripFormatting(line.string)
            cleanLine.contains("SELECTED") || cleanLine.contains("Click to deselect")
        }
    }

    /**
     * Extract the goal XP from an item's lore.
     * Looks for patterns like "31,900/75M" or "1,812/8.3k"
     */
    fun extractGoalXp(lore: List<Text>): Long? {
        for (line in lore) {
            val cleanLine = stripFormatting(line.string)

            // Try XP progress pattern (e.g., "31,900/75M")
            val progressMatch = XP_PROGRESS_PATTERN.find(cleanLine)
            if (progressMatch != null) {
                val goalStr = progressMatch.groupValues[2]
                return parseNumberWithSuffix(goalStr)
            }
        }
        return null
    }

    /**
     * Extract current XP progress from an item's lore.
     */
    fun extractCurrentXp(lore: List<Text>): Long? {
        for (line in lore) {
            val cleanLine = stripFormatting(line.string)

            // Try XP progress pattern (e.g., "31,900/75M")
            val progressMatch = XP_PROGRESS_PATTERN.find(cleanLine)
            if (progressMatch != null) {
                val currentStr = progressMatch.groupValues[1]
                return parseNumberWithSuffix(currentStr)
            }

            // Try stored XP pattern for when nothing is selected
            val storedMatch = STORED_XP_PATTERN.find(cleanLine)
            if (storedMatch != null) {
                val storedStr = storedMatch.groupValues[1]
                return parseNumberWithSuffix(storedStr)
            }
        }
        return null
    }

    /**
     * Parse a selected item from an ItemStack.
     * Returns null if the item is not selected.
     */
    fun parseSelectedItem(itemStack: ItemStack): SelectedItemInfo? {
        //? if >=26.1 {
        /*val name = itemStack.hoverName?.string ?: return null
        *///?} else {
        val name = itemStack.name?.string ?: return null
        //?}
        val lore = getLore(itemStack)

        if (!isSelected(lore)) {
            return null
        }

        val goalXp = extractGoalXp(lore)
        return SelectedItemInfo(
            itemName = stripFormatting(name),
            goalXp = goalXp
        )
    }

    /**
     * Check if an item is the "Milestone I Rewards" item and extract the total completed count.
     * Parses lore for patterns like "208/5" where the first number is the total completed.
     * Milestone I is used because its threshold (5) never changes — the absolute count is shown
     * stably for all players, including those who have maxed every milestone tier.
     */
    fun parseCommissionMilestoneCount(itemStack: ItemStack): Int? {
        //? if >=26.1 {
        /*val name = stripFormatting(itemStack.hoverName?.string ?: return null)
        *///?} else {
        val name = stripFormatting(itemStack.name?.string ?: return null)
        //?}
        if (!name.contains(MILESTONE_I_REWARDS_ITEM)) return null

        val lore = getLore(itemStack)
        for (line in lore) {
            val cleanLine = stripFormatting(line.string)
            val match = MILESTONE_COUNT_PATTERN.find(cleanLine) ?: continue
            val countStr = match.groupValues[1].replace(",", "")
            return countStr.toIntOrNull()
        }
        return null
    }

    /**
     * Get the lore lines from an ItemStack.
     */
    fun getLore(itemStack: ItemStack): List<Text> {
        //? if >=26.1 {
        /*val loreComponent = itemStack.get(net.minecraft.core.component.DataComponents.LORE)
        return loreComponent?.lines() ?: emptyList()
        *///?} else {
        val loreComponent = itemStack.get(net.minecraft.component.DataComponentTypes.LORE)
        return loreComponent?.lines ?: emptyList()
        //?}
    }

    /**
     * Strip Minecraft formatting codes from a string.
     * Removes color codes like §a, §7, etc.
     */
    fun stripFormatting(text: String): String {
        return text.replace(Regex("§."), "")
    }

    // ==================== Vincent Dye Collection Parsing ====================

    /**
     * Map of display name (lowercased, without " Dye" suffix) to dye ID.
     * Must match dye IDs from DYE_DEFINITIONS in packages/shared/src/constants/dyes.ts.
     */
    private val DYE_DISPLAY_NAME_TO_ID = mapOf(
        "matcha" to "matcha",
        "brick red" to "brick_red",
        "celeste" to "celeste",
        "byzantium" to "byzantium",
        "flame" to "flame",
        "sangria" to "sangria",
        "livid" to "livid",
        "necron" to "necron",
        "jade" to "jade",
        "nadeshiko" to "nadeshiko",
        "tentacle" to "tentacle",
        "aquamarine" to "aquamarine",
        "archfiend" to "archfiend",
        "bone" to "bone",
        "carmine" to "carmine",
        "celadon" to "celadon",
        "copper" to "copper",
        "cyclamen" to "cyclamen",
        "dark purple" to "dark_purple",
        "dung" to "dung",
        "emerald" to "emerald",
        "fossil" to "fossil",
        "frostbitten" to "frostbitten",
        "holly" to "holly",
        "iceberg" to "iceberg",
        "mango" to "mango",
        "midnight" to "midnight",
        "mocha" to "mocha",
        "mythological" to "mythological",
        "nyanza" to "nyanza",
        "pearlescent" to "pearlescent",
        "pelt" to "pelt",
        "periwinkle" to "periwinkle",
        "secret" to "secret",
        "wild strawberry" to "wild_strawberry",
        "bingo blue" to "bingo_blue",
        "chocolate" to "chocolate",
        "pure black" to "pure_black",
        "pure white" to "pure_white",
        "pure blue" to "pure_blue",
        "pure yellow" to "pure_yellow",
        // Animated Fire-Sale dyes (PBI 33). Kept in lock-step with DyeSprites.DYE_IDS.
        "aurora" to "aurora",
        "black ice" to "black_ice",
        "lava" to "lava",
        "lucky" to "lucky",
        "ocean" to "ocean",
        "pastel sky" to "pastel_sky",
        "portal" to "portal",
        "rose" to "rose",
        "snowflake" to "snowflake",
        "treasure" to "treasure",
        "warden" to "warden"
    )

    /**
     * The dye IDs the compendium/rotation parser can resolve (values of [DYE_DISPLAY_NAME_TO_ID]).
     * Exposed for the drift-guard test that keeps the name map, [com.dyetracker.rotation.DyeSprites]
     * IDs, and bundled PNGs in lock-step.
     */
    fun dyeDisplayNameToIdValues(): Collection<String> = DYE_DISPLAY_NAME_TO_ID.values

    /**
     * The full display-name → dye-id map (single source of truth for dye labels). Exposed so the
     * dye picker (PBI 34) can derive its option list by inverting this map instead of duplicating
     * the dye list.
     */
    fun dyeDisplayNameToId(): Map<String, String> = DYE_DISPLAY_NAME_TO_ID

    /** Suffix appended to dye names in inventory items */
    private const val DYE_ITEM_SUFFIX = " Dye"

    /** Lore pattern for player drop count (e.g., "You've dropped: 3") */
    private val DROPPED_COUNT_PATTERN = Regex("""You've dropped:\s*(\d+)""")

    /** Lore pattern for player shop-purchase count (e.g., "You've bought: 5") */
    private val BOUGHT_COUNT_PATTERN = Regex("""You've bought:\s*(\d+)""")

    /**
     * All lore patterns that indicate the player owns a dye. A dye is captured when ANY of these
     * yields a count > 0; the stored count is the max across them. RNG dyes use "You've dropped:",
     * shop/Fire-Sale dyes (chocolate, pure dyes, animated dyes) use "You've bought:". No
     * dropped-vs-bought source distinction is recorded.
     */
    private val OWNED_COUNT_PATTERNS = listOf(DROPPED_COUNT_PATTERN, BOUGHT_COUNT_PATTERN)

    /** Lore pattern for hex color (e.g., "Hex: #50C878") */
    private val HEX_COLOR_PATTERN = Regex("""Hex:\s*(#[0-9A-Fa-f]{6})""")

    /** Lore pattern for global drops (e.g., "Global drops: 438") */
    private val GLOBAL_DROPS_PATTERN = Regex("""Global drops:\s*([\d,]+)""")

    /**
     * Lore pattern for the rotation boost line, e.g.
     * "This dye is 3x as common during SkyBlock Year 492!". Group 1 = multiplier, group 2 = year.
     * Matched against the lore lines joined with spaces, since Hypixel may split this sentence
     * across two lore lines.
     */
    private val ROTATION_BOOST_PATTERN =
        Regex("""This dye is\s*(\d+)x as common during SkyBlock Year\s*(\d+)""")

    /**
     * Resolve a dye item's display name to its dye ID, or null if the item is not a recognized
     * dye. Strips formatting + the " Dye" suffix and looks up [DYE_DISPLAY_NAME_TO_ID].
     */
    private fun resolveDyeId(itemStack: ItemStack): String? {
        val rawName = itemStack.name?.string ?: return null
        val cleanName = stripFormatting(rawName)
        val dyeName = if (cleanName.endsWith(DYE_ITEM_SUFFIX)) {
            cleanName.dropLast(DYE_ITEM_SUFFIX.length)
        } else {
            cleanName
        }
        return DYE_DISPLAY_NAME_TO_ID[dyeName.lowercase()]
    }

    /**
     * Extract the player's ownership count for a dye from its already-stripped lore lines.
     *
     * Pure (no Minecraft types) so it can be unit-tested. Returns the max count across every
     * pattern in [OWNED_COUNT_PATTERNS] ("You've dropped:" / "You've bought:"), or 0 if none match.
     * A return value > 0 means the player owns the dye (RNG-dropped or shop-bought).
     */
    fun extractOwnedCount(loreLines: List<String>): Int {
        var maxCount = 0
        for (line in loreLines) {
            for (pattern in OWNED_COUNT_PATTERNS) {
                val match = pattern.find(line) ?: continue
                val count = match.groupValues[1].toIntOrNull() ?: continue
                if (count > maxCount) maxCount = count
            }
        }
        return maxCount
    }

    /**
     * Parse a dye item from the Dye Compendium into a DroppedDye.
     * Returns null if the item is not a recognized dye or if the player does not own it.
     * A dye is captured when it has any ownership count > 0 ("You've dropped:" OR "You've bought:");
     * the stored [DroppedDye.count] is that owned count (bought count for purchase dyes).
     */
    fun parseDyeItem(itemStack: ItemStack): DroppedDye? {
        val dyeId = resolveDyeId(itemStack) ?: return null

        // Strip formatting once, then derive ownership count + metadata from the clean lines.
        val cleanLines = getLore(itemStack).map { stripFormatting(it.string) }
        val ownedCount = extractOwnedCount(cleanLines)

        val metadata = mutableMapOf<String, String>()
        for (cleanLine in cleanLines) {
            // Capture hex color
            val hexMatch = HEX_COLOR_PATTERN.find(cleanLine)
            if (hexMatch != null) {
                metadata["hex"] = hexMatch.groupValues[1]
            }

            // Capture global drops
            val globalMatch = GLOBAL_DROPS_PATTERN.find(cleanLine)
            if (globalMatch != null) {
                metadata["globalDrops"] = globalMatch.groupValues[1].replace(",", "")
            }
        }

        if (ownedCount == 0) return null

        return DroppedDye(
            dyeId = dyeId,
            count = ownedCount,
            obtainedAt = null, // Compendium doesn't show timestamp
            metadata = metadata.ifEmpty { null }
        )
    }

    /**
     * Extract all dye items from a Vincent inventory screen.
     * Returns a list of DroppedDye for each recognized dye in the inventory.
     */
    fun extractDyeCollection(slots: Iterable<Slot>): List<DroppedDye> {
        val dyes = mutableListOf<DroppedDye>()
        for (slot in slots) {
            val stack = slot.stack
            if (stack.isEmpty) continue

            val dye = parseDyeItem(stack)
            if (dye != null) {
                dyes.add(dye)
            }
        }
        return dyes
    }

    // ==================== Vincent Dye Rotation Parsing ====================

    /**
     * Extract the current dye rotation from the Vincent "Dyes" screen.
     *
     * Unlike [extractDyeCollection], this does NOT gate on "You've dropped > 0" — rotation dyes
     * the player has never obtained must still be captured. Only container slots are considered
     * ([playerInventory] slots are skipped), and non-dye container items (the `$` shop icon, the
     * info sign, the nav block/X/item) are excluded naturally because their names don't resolve to
     * a known dye. Container slot order is preserved into [DyeRotation.dyeIds] so the widget renders
     * in screen order. Per-dye boost multiplier + SkyBlock year are captured from lore when present.
     */
    fun extractDyeRotation(slots: Iterable<Slot>, playerInventory: Inventory): DyeRotation {
        val dyeIds = mutableListOf<String>()
        val boosts = mutableMapOf<String, Int>()
        var skyblockYear: Int? = null

        for (slot in slots) {
            // Skip the player inventory; only the container's slots hold the rotation.
            if (slot.inventory === playerInventory) continue

            val stack = slot.stack
            if (stack.isEmpty) continue

            val dyeId = resolveDyeId(stack)
            if (dyeId == null) {
                // Log only items that look like a dye ("<Name> Dye") but aren't in the map, so a
                // future Hypixel rename is diagnosable — without spamming for the $/sign/nav items.
                val name = stripFormatting(stack.name?.string ?: "")
                if (name.endsWith(DYE_ITEM_SUFFIX)) {
                    DyeTrackerMod.debug("Skipping unmapped rotation dye: {}", name)
                }
                continue
            }
            dyeIds.add(dyeId)

            val boost = parseRotationBoost(getLore(stack))
            if (boost != null) {
                boosts[dyeId] = boost.first
                if (skyblockYear == null) skyblockYear = boost.second
            }
        }

        return DyeRotation(
            dyeIds = dyeIds,
            capturedAt = System.currentTimeMillis(),
            boosts = boosts.ifEmpty { null },
            skyblockYear = skyblockYear,
        )
    }

    /**
     * Parse the boost line from a rotation dye's lore. Returns (multiplier, skyblockYear) or null
     * if absent. Lore lines are joined with spaces first so a sentence split across two lore lines
     * still matches.
     */
    private fun parseRotationBoost(lore: List<Text>): Pair<Int, Int>? {
        val joined = lore.joinToString(" ") { stripFormatting(it.string) }
        val match = ROTATION_BOOST_PATTERN.find(joined) ?: return null
        val multiplier = match.groupValues[1].toIntOrNull() ?: return null
        val year = match.groupValues[2].toIntOrNull() ?: return null
        return multiplier to year
    }

    // ==================== Visitor's Logbook Parsing (PBI 42) ====================

    // Authoritative per-visitor "seen" count. Anchored on "Times Visited:" so it never matches
    // the "Offers Accepted:" line nor the third-party "Times Denied:" header overlay — that header
    // comes from a separate mod and is intentionally NOT parsed (it must not contribute to totals).
    private val TIMES_VISITED_PATTERN = Regex("""Times Visited:\s*(\d+)""")

    // Lookup of the five visitor rarity tiers by their exact (caps) name, for matching the
    // standalone rarity lore line. An unrecognized 6th tier yields null (skipped, not mis-bucketed).
    private val VISITOR_RARITY_BY_NAME: Map<String, VisitorRarity> =
        VisitorRarity.values().associateBy { it.name }

    /**
     * Parse the "Times Visited: N" value from already-stripped lore lines. Pure (no Minecraft
     * types) so it is unit-testable. Returns null if no "Times Visited:" line is present. Does NOT
     * match "Offers Accepted:" or the "Times Denied:" header.
     */
    fun extractTimesVisited(loreLines: List<String>): Int? {
        for (line in loreLines) {
            val match = TIMES_VISITED_PATTERN.find(line) ?: continue
            return match.groupValues[1].toIntOrNull()
        }
        return null
    }

    /**
     * Find the standalone rarity line (one of the five [VisitorRarity] names) in already-stripped
     * lore lines. Matching the explicit caps line is more robust than inferring from the visitor
     * name's color code. Returns null when no line matches (an unknown/6th tier is skipped).
     */
    fun extractVisitorRarity(loreLines: List<String>): VisitorRarity? {
        for (line in loreLines) {
            val rarity = VISITOR_RARITY_BY_NAME[line.trim()]
            if (rarity != null) return rarity
        }
        return null
    }

    /**
     * Parse a single Logbook visitor item into a [VisitorEntry]. Returns an entry only when BOTH a
     * recognizable rarity line AND a "Times Visited" line are present; otherwise null — so header,
     * filler, and navigation items (which carry neither) are naturally skipped.
     */
    fun parseVisitorEntry(name: String, loreLines: List<String>): VisitorEntry? {
        val rarity = extractVisitorRarity(loreLines) ?: return null
        val timesVisited = extractTimesVisited(loreLines) ?: return null
        return VisitorEntry(name = name, rarity = rarity, timesVisited = timesVisited)
    }

    /**
     * Merge a page's worth of [VisitorEntry]s into an accumulator keyed by visitor [name].
     * De-dups across re-scans/pages: a name already present is overwritten with the latest value
     * (last-seen wins), so paging back and forth never double-counts a visitor.
     */
    fun accumulateVisitors(
        existing: Map<String, VisitorEntry>,
        pageEntries: List<VisitorEntry>
    ): Map<String, VisitorEntry> {
        val merged = existing.toMutableMap()
        for (entry in pageEntries) {
            merged[entry.name] = entry
        }
        return merged
    }

    /**
     * Extract all visitor entries from a Visitor's Logbook screen's slots. Thin MC-typed shell
     * (analogous to [extractDyeCollection]): reads each slot's name + lore and delegates to the
     * pure [parseVisitorEntry]. Non-visitor slots (filler/navigation) resolve to null and are
     * skipped.
     */
    fun extractVisitorEntriesFromSlots(slots: Iterable<Slot>): List<VisitorEntry> {
        val entries = mutableListOf<VisitorEntry>()
        for (slot in slots) {
            val stack = slot.stack
            if (stack.isEmpty) continue

            val name = stripFormatting(stack.name?.string ?: continue)
            val loreLines = getLore(stack).map { stripFormatting(it.string) }
            val entry = parseVisitorEntry(name, loreLines)
            if (entry != null) entries.add(entry)
        }
        return entries
    }

    /**
     * Parse a number string that may contain commas and K/M suffixes.
     * Examples: "75M" -> 75000000, "8.3k" -> 8300, "1,812" -> 1812
     */
    private fun parseNumberWithSuffix(text: String): Long? {
        val cleanText = text.replace(",", "").trim()

        // Check for M/k suffix
        val multiplier = when {
            cleanText.endsWith("M", ignoreCase = true) -> 1_000_000L
            cleanText.endsWith("k", ignoreCase = true) -> 1_000L
            else -> 1L
        }

        val numberPart = if (multiplier > 1) {
            cleanText.dropLast(1)
        } else {
            cleanText
        }

        return numberPart.toDoubleOrNull()?.let { (it * multiplier).toLong() }
    }
}
