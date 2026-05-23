package com.dyetracker.dyeprogress

import com.dyetracker.api.ApiClient
import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.api.DyeProgressResponse
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory cache of fetched dye progress, keyed by `profileId`. The polling
 * scheduler ([DyeProgressPoller]) writes results via [update]; the HUD widget (task 34-5) reads
 * per-dye values via [entry] and the per-profile [status] to choose its render state.
 *
 * Progress is volatile — it is NOT persisted. On startup the cache is empty (every profile
 * reads [Status.LOADING]) until the first successful fetch. On a failed fetch the previous
 * successful data is retained so the widget can keep showing the last known value instead of
 * flickering to empty.
 */
object DyeProgressStore {

    /** Per-profile fetch state, read by the widget to pick its render state. */
    enum class Status {
        /** Never fetched (cold cache). */
        LOADING,

        /** Last fetch succeeded; [byDyeId] holds fresh data. */
        OK,

        /** Last fetch failed; [byDyeId] holds the previous successful data, if any. */
        ERROR,
    }

    private data class ProfileProgress(
        val status: Status,
        val byDyeId: Map<String, DyeProgressEntry>,
        val modSyncTimestamp: Long?,
        val lastUpdatedMs: Long?,
    )

    private val byProfile = ConcurrentHashMap<String, ProfileProgress>()

    /** Fetch status for [profileId]; [Status.LOADING] if the profile has never been fetched. */
    fun status(profileId: String): Status = byProfile[profileId]?.status ?: Status.LOADING

    /** The cached progress for one dye in one profile, or null if not present. */
    fun entry(profileId: String, dyeId: String): DyeProgressEntry? =
        byProfile[profileId]?.byDyeId?.get(dyeId)

    /** The `modSyncTimestamp` from the last successful fetch for [profileId], if any. */
    fun modSyncTimestamp(profileId: String): Long? = byProfile[profileId]?.modSyncTimestamp

    /** Wall-clock ms of the last successful fetch for [profileId], or null if never succeeded. */
    fun lastUpdatedMs(profileId: String): Long? = byProfile[profileId]?.lastUpdatedMs

    /**
     * Apply a fetch [result] for [profileId]. On success the cache is replaced with the fresh
     * per-dye map; on failure the status flips to [Status.ERROR] but the previous successful
     * data (and its timestamp) is retained so the widget keeps showing the last value.
     *
     * The read-modify-write of the previous data is done atomically via [ConcurrentHashMap.compute]
     * so a manual [DyeProgressPoller.pollOnce] running alongside the scheduled tick can't clobber
     * fresh data with stale data.
     */
    fun update(profileId: String, result: ApiClient.ApiResult<DyeProgressResponse>) {
        byProfile.compute(profileId) { _, prev ->
            when (result) {
                is ApiClient.ApiResult.Success -> ProfileProgress(
                    status = Status.OK,
                    byDyeId = result.data.dyes.associateBy { it.dyeId },
                    modSyncTimestamp = result.data.modSyncTimestamp,
                    lastUpdatedMs = System.currentTimeMillis(),
                )

                is ApiClient.ApiResult.Error -> ProfileProgress(
                    status = Status.ERROR,
                    byDyeId = prev?.byDyeId ?: emptyMap(),
                    modSyncTimestamp = prev?.modSyncTimestamp,
                    lastUpdatedMs = prev?.lastUpdatedMs,
                )
            }
        }
    }

    /** Drop all cached progress (e.g. on unlink / config reset). */
    fun clear() = byProfile.clear()
}
