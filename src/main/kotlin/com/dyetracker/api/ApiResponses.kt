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
