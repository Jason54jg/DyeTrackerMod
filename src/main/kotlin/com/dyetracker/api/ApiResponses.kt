package com.dyetracker.api

import com.dyetracker.data.ArchfiendDyeData
import com.dyetracker.data.CopperDyeData
import com.dyetracker.data.DroppedDye
import com.dyetracker.data.DungeonFloor
import com.dyetracker.data.NyanzaDyeData
import com.dyetracker.data.DungeonRngMeter
import com.dyetracker.data.ExperimentationRngMeter
import com.dyetracker.data.MineshaftPity
import com.dyetracker.data.NucleusRngMeter
import com.dyetracker.data.SlayerRngMeter
import com.dyetracker.data.SlayerType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for POST /api/v1/rng-data
 */
@Serializable
data class SyncRngDataRequest(
    val slayerMeters: Map<SlayerType, SlayerRngMeter> = emptyMap(),
    val dungeonMeters: Map<DungeonFloor, DungeonRngMeter> = emptyMap(),
    val nucleusMeter: NucleusRngMeter? = null,
    val experimentationMeter: ExperimentationRngMeter? = null,
    val mineshaftPity: MineshaftPity? = null,
    val archfiendDye: ArchfiendDyeData? = null,
    val copperDye: CopperDyeData? = null,
    val nyanzaDye: NyanzaDyeData? = null,
    val modTimestamp: Long
)

/**
 * Response from POST /api/v1/auth/start-verify
 */
@Serializable
data class StartVerifyResponse(
    val serverId: String
)

/**
 * Response from POST /api/v1/auth/complete-verify
 */
@Serializable
data class CompleteVerifyResponse(
    val success: Boolean,
    val uuid: String,
    val username: String,
    val token: String
)

/**
 * Error response from the API
 */
@Serializable
data class ApiErrorResponse(
    val error: String,
    val message: String,
    val status: Int
)

/**
 * Response from GET /api/v1/auth/me
 */
@Serializable
data class AuthMeResponse(
    val authenticated: Boolean,
    val uuid: String,
    val username: String
)

/**
 * Response from POST /api/v1/rng-data
 */
@Serializable
data class SyncRngDataResponse(
    val success: Boolean,
    val uuid: String,
    val updatedAt: Long
)

/**
 * Request body for POST /api/v1/dye-collection
 */
@Serializable
data class SyncDyeCollectionRequest(
    val profileId: String,
    val dyes: List<DroppedDye>,
    val modTimestamp: Long
)

/**
 * Response from POST /api/v1/dye-collection
 */
@Serializable
data class SyncDyeCollectionResponse(
    val success: Boolean,
    val uuid: String,
    val syncedCount: Int
)

/**
 * A profile id + its human-readable name (`cute_name` in the backend JSON, e.g. "Mango").
 * Used both standalone in [PlayerProfilesResponse] and as the `profile` field of
 * [DyeProgressResponse].
 */
@Serializable
data class ProfileSummary(
    val id: String,
    @SerialName("cute_name") val cuteName: String
)

/**
 * One dye's progress entry from `GET /api/v1/player/:username/profile/:profileId/dyes`.
 * Decodes only the fields the in-game single-dye widget needs; the client's
 * `ignoreUnknownKeys` Json tolerates the rest. `progress` is null for untrackable dyes.
 * Optional/absent fields default so a missing key never fails decoding.
 */
@Serializable
data class DyeProgressEntry(
    val dyeId: String,
    val name: String,
    val color: String,
    val category: String,
    val progress: Double? = null,
    val trackable: Boolean = false,
    val formula: String? = null,
    val source: String? = null,
    val isVerified: Boolean = false
)

/**
 * Response from `GET /api/v1/player/:username/profile/:profileId/dyes` (public endpoint).
 * `modSyncTimestamp` is null when the player has no mod-synced data for the profile.
 */
@Serializable
data class DyeProgressResponse(
    val username: String,
    val profile: ProfileSummary,
    val dyes: List<DyeProgressEntry>,
    val modSyncTimestamp: Long? = null
)

/**
 * Response from `GET /api/v1/player/:username` (public endpoint): the player's SkyBlock
 * profiles, used to resolve a typed profile `cute_name` to the `profileId` the dyes endpoint
 * requires.
 */
@Serializable
data class PlayerProfilesResponse(
    val uuid: String,
    val username: String,
    val profiles: List<ProfileSummary>
)
