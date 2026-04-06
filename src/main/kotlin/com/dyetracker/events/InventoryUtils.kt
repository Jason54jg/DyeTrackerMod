package com.dyetracker.events

import com.dyetracker.data.DroppedDye
import com.dyetracker.data.DungeonFloor
import com.dyetracker.data.SlayerType
import net.minecraft.item.ItemStack
import net.minecraft.text.Text

/**
 * Inventory type detection results.
 */
sealed class InventoryType {
    data class SlayerRngMeter(val slayerType: SlayerType) : InventoryType()
    data class DungeonRngMeter(val floor: DungeonFloor) : InventoryType()
    data object NucleusRngMeter : InventoryType()
    data object ExperimentationRngMeter : InventoryType()
    data object Commissions : InventoryType()
    data object VincentDyeCollection : InventoryType()
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

    // Slayer RNG meter title patterns
    private val SLAYER_TITLE_MAP = mapOf(
        "Revenant Horror" to SlayerType.REVENANT,
        "Tarantula Broodfather" to SlayerType.TARANTULA,
        "Sven Packmaster" to SlayerType.SVEN,
        "Voidgloom Seraph" to SlayerType.VOIDGLOOM,
        "Inferno Demonlord" to SlayerType.INFERNO,
        "Riftstalker Bloodfiend" to SlayerType.RIFTSTALKER
    )

    // Dungeon RNG meter title patterns
    private const val M5_TITLE = "Master Mode Floor V"
    private const val M7_TITLE = "Master Mode Floor VII"

    // Other RNG meter titles
    private const val NUCLEUS_TITLE = "Crystal Hollows"
    private const val EXPERIMENTATION_TITLE = "Experimentation Table"
    private const val RNG_METER_SUFFIX = "RNG Meter"

    // Vincent dye collection GUI title
    private const val VINCENT_TITLE = "Your Dyes"

    // Commissions GUI title
    private const val COMMISSIONS_TITLE = "Commissions"

    // Commission Milestones item name
    private const val COMMISSION_MILESTONES_ITEM = "Commission Milestones"

    // Pattern to extract completed count from milestone lore (e.g., "208/250")
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

        // Check for Commissions GUI
        if (cleanTitle == COMMISSIONS_TITLE) {
            return InventoryType.Commissions
        }

        // Check for experimentation table first (uses "RNG" not "RNG Meter")
        if (cleanTitle.contains(EXPERIMENTATION_TITLE) && cleanTitle.contains("RNG")) {
            return InventoryType.ExperimentationRngMeter
        }

        // Check if it's an RNG Meter inventory (for all other types)
        if (!cleanTitle.contains(RNG_METER_SUFFIX)) {
            return null
        }

        // Check for slayer types
        for ((pattern, slayerType) in SLAYER_TITLE_MAP) {
            if (cleanTitle.contains(pattern)) {
                return InventoryType.SlayerRngMeter(slayerType)
            }
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
     * Parse the slayer type from an inventory title.
     * Returns null if not a slayer RNG meter.
     */
    fun parseSlayerType(title: String): SlayerType? {
        val cleanTitle = stripFormatting(title)
        for ((pattern, slayerType) in SLAYER_TITLE_MAP) {
            if (cleanTitle.contains(pattern)) {
                return slayerType
            }
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
        val name = itemStack.name?.string ?: return null
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
     * Check if an item is the "Commission Milestones" item and extract the total completed count.
     * Parses lore for patterns like "208/250" where the first number is the total completed.
     */
    fun parseCommissionMilestoneCount(itemStack: ItemStack): Int? {
        val name = stripFormatting(itemStack.name?.string ?: return null)
        if (!name.contains(COMMISSION_MILESTONES_ITEM)) return null

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
        val loreComponent = itemStack.get(net.minecraft.component.DataComponentTypes.LORE)
        return loreComponent?.lines ?: emptyList()
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
        "pure yellow" to "pure_yellow"
    )

    /** Suffix appended to dye names in inventory items */
    private const val DYE_ITEM_SUFFIX = " Dye"

    /** Lore pattern for obtained timestamp (e.g., "Obtained: 12/25/25") */
    private val OBTAINED_DATE_PATTERN = Regex("""Obtained:\s*(.+)""")

    /**
     * Parse a dye item from Vincent's inventory into a DroppedDye.
     * Returns null if the item is not a recognized dye.
     */
    fun parseDyeItem(itemStack: ItemStack): DroppedDye? {
        val rawName = itemStack.name?.string ?: return null
        val cleanName = stripFormatting(rawName)

        // Strip " Dye" suffix if present, then lowercase for lookup
        val dyeName = if (cleanName.endsWith(DYE_ITEM_SUFFIX)) {
            cleanName.dropLast(DYE_ITEM_SUFFIX.length)
        } else {
            cleanName
        }

        val dyeId = DYE_DISPLAY_NAME_TO_ID[dyeName.lowercase()] ?: return null

        // Extract metadata from lore
        val lore = getLore(itemStack)
        var obtainedAt: Long? = null
        val metadata = mutableMapOf<String, String>()

        for (line in lore) {
            val cleanLine = stripFormatting(line.string)

            // Check for obtained date
            val dateMatch = OBTAINED_DATE_PATTERN.find(cleanLine)
            if (dateMatch != null) {
                val dateStr = dateMatch.groupValues[1].trim()
                metadata["obtainedDate"] = dateStr
                // Note: obtainedAt as unix timestamp would require date parsing;
                // store the raw string in metadata for now, leave obtainedAt null
                // unless we can parse the date format reliably
            }

            // Capture any other non-empty, non-decorative lore lines as metadata
            if (cleanLine.isNotBlank() && !cleanLine.startsWith("---") && cleanLine != cleanName) {
                // Store rarity or other useful lore
                if (cleanLine.contains("COSMETIC") || cleanLine.contains("SPECIAL") ||
                    cleanLine.contains("LEGENDARY") || cleanLine.contains("RARE")
                ) {
                    metadata["rarity"] = cleanLine.trim()
                }
            }
        }

        return DroppedDye(
            dyeId = dyeId,
            obtainedAt = obtainedAt,
            metadata = metadata.ifEmpty { null }
        )
    }

    /**
     * Extract all dye items from a Vincent inventory screen.
     * Returns a list of DroppedDye for each recognized dye in the inventory.
     */
    fun extractDyeCollection(slots: Iterable<net.minecraft.screen.slot.Slot>): List<DroppedDye> {
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
