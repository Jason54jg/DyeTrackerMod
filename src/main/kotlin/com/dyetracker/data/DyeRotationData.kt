package com.dyetracker.data

import kotlinx.serialization.Serializable

/**
 * The current Vincent dye rotation captured from the in-game "Dyes" screen (PBI 31).
 *
 * Distinct from [DyeCollection], which is the player's full obtained-dye collection. A rotation
 * is the small ordered set of dyes currently boosted/in-rotation, used by the HUD rotation
 * widget. Optional metadata is nullable so the model can grow without a serialization break.
 *
 * The rotation screen exposes no explicit expiry/refresh timestamp, so none is modeled; the
 * available metadata is the per-dye boost multiplier and the observed SkyBlock year.
 */
@Serializable
data class DyeRotation(
    /** Dye IDs currently in rotation, in screen order. */
    val dyeIds: List<String> = emptyList(),
    /** Unix timestamp in ms when this rotation was captured. */
    val capturedAt: Long = 0L,
    /**
     * Per-dye boost multiplier from the lore line
     * "This dye is Nx as common during SkyBlock Year Y!" (dyeId → N). Null when no boost
     * metadata was present.
     */
    val boosts: Map<String, Int>? = null,
    /** SkyBlock year observed in the boost lore, null if not present. */
    val skyblockYear: Int? = null,
) {
    /** True when no dyes have been captured yet (the empty/absent state). */
    fun isEmpty(): Boolean = dyeIds.isEmpty()
}
