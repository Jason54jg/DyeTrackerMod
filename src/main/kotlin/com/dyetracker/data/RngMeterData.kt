package com.dyetracker.data

import kotlinx.serialization.Serializable

/**
 * Types of slayer bosses in SkyBlock.
 */
@Serializable
enum class SlayerType {
    REVENANT,
    TARANTULA,
    SVEN,
    VOIDGLOOM,
    INFERNO,
    RIFTSTALKER
}

/**
 * Dungeon floors that have RNG meters.
 */
@Serializable
enum class DungeonFloor {
    M5,
    M7
}

/**
 * RNG meter data for a slayer boss.
 */
@Serializable
data class SlayerRngMeter(
    val slayerType: SlayerType,
    val storedXp: Long,
    val selectedItem: String? = null,
    val goalXp: Long? = null
)

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
 * Tracks garden visitor offer acceptances by rarity tier.
 */
@Serializable
data class CopperDyeData(
    val visitorAccepts: Map<VisitorRarity, Int> = emptyMap()
)

/**
 * Composite data class holding all RNG data for a player.
 */
@Serializable
data class PlayerRngData(
    val slayerMeters: Map<SlayerType, SlayerRngMeter> = emptyMap(),
    val dungeonMeters: Map<DungeonFloor, DungeonRngMeter> = emptyMap(),
    val nucleusMeter: NucleusRngMeter? = null,
    val experimentationMeter: ExperimentationRngMeter? = null,
    val mineshaftPity: MineshaftPity? = null,
    val archfiendDye: ArchfiendDyeData? = null,
    val copperDye: CopperDyeData? = null
) {
    /**
     * Returns true if any RNG data has been captured.
     */
    fun hasData(): Boolean {
        return slayerMeters.isNotEmpty() ||
            dungeonMeters.isNotEmpty() ||
            nucleusMeter != null ||
            experimentationMeter != null ||
            mineshaftPity != null ||
            archfiendDye != null ||
            copperDye != null
    }
}
