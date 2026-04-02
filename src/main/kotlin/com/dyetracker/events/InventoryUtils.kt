package com.dyetracker.events

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
