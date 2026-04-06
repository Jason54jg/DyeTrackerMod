package com.dyetracker.data

import kotlinx.serialization.Serializable

/**
 * A single dye that a player has obtained (dropped) from Vincent NPC.
 */
@Serializable
data class DroppedDye(
    /** Dye ID matching the website's dye constants (e.g., "matcha", "jade") */
    val dyeId: String,
    /** Unix timestamp in ms when the dye was obtained, null if unknown */
    val obtainedAt: Long? = null,
    /** Additional metadata from item NBT, null if none */
    val metadata: Map<String, String>? = null
)

/**
 * A player's full dye collection captured from Vincent NPC inventory.
 */
@Serializable
data class DyeCollection(
    /** SkyBlock profile ID this collection belongs to */
    val profileId: String,
    /** List of dropped dyes found in Vincent's inventory */
    val dyes: List<DroppedDye> = emptyList(),
    /** Unix timestamp in ms when this collection was last scanned */
    val lastUpdated: Long = 0
)
