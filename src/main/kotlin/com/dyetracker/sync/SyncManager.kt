package com.dyetracker.sync

import com.dyetracker.DyeTrackerMod
import com.dyetracker.api.ApiClient
import com.dyetracker.auth.AccountVerification
import com.dyetracker.data.DyeCollection
import com.dyetracker.data.PlayerRngData
import com.dyetracker.data.RngDataChangeListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages synchronization of RNG data to the backend.
 *
 * Features:
 * - Debounced sync (30s delay after data changes)
 * - Offline queue with retry
 * - Exponential backoff on failures
 * - Manual sync trigger
 */
object SyncManager : RngDataChangeListener {
    private const val SYNC_DEBOUNCE_MS = 30_000L // 30 seconds
    private const val INITIAL_RETRY_DELAY_MS = 5_000L // 5 seconds
    private const val MAX_RETRY_DELAY_MS = 300_000L // 5 minutes
    private const val MAX_RETRY_ATTEMPTS = 5

    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "DyeTracker-Sync").apply {
            isDaemon = true
        }
    }

    @Volatile
    private var pendingSync: ScheduledFuture<*>? = null

    @Volatile
    private var pendingData: PlayerRngData? = null

    private val syncLock = Any()

    @Volatile
    private var lastSyncTime: Long = 0

    @Volatile
    private var lastSyncSuccess: Boolean = true

    private val queuedSyncCount = AtomicInteger(0)
    private val retryAttempts = AtomicInteger(0)
    private val currentRetryDelay = AtomicLong(INITIAL_RETRY_DELAY_MS)

    /**
     * Get the timestamp of the last successful sync.
     */
    fun getLastSyncTime(): Long = lastSyncTime

    /**
     * Check if the last sync was successful.
     */
    fun wasLastSyncSuccessful(): Boolean = lastSyncSuccess

    /**
     * Get the number of pending syncs in the queue.
     */
    fun getQueueSize(): Int = if (pendingData != null) 1 else 0

    /**
     * Check if a sync is currently pending.
     */
    fun isSyncPending(): Boolean = pendingSync != null

    /**
     * Called when RNG data changes. Schedules a debounced sync.
     */
    override fun onDataChanged(data: PlayerRngData) {
        DyeTrackerMod.info("SyncManager: data changed, checking if linked...")
        if (!AccountVerification.isLinked()) {
            DyeTrackerMod.info("SyncManager: skipping sync - account not linked")
            return
        }

        synchronized(syncLock) {
            pendingData = data
            cancelPendingSync()

            pendingSync = scheduler.schedule({
                performSync()
            }, SYNC_DEBOUNCE_MS, TimeUnit.MILLISECONDS)

            DyeTrackerMod.info("Sync scheduled in {}s", SYNC_DEBOUNCE_MS / 1000)
        }
    }

    /**
     * Trigger an immediate sync. Call this from the /dyetracker sync command.
     *
     * @param data The current RNG data to sync
     * @return A CompletableFuture that completes with the sync result
     */
    fun syncNow(data: PlayerRngData): java.util.concurrent.CompletableFuture<SyncResult> {
        return java.util.concurrent.CompletableFuture.supplyAsync({
            if (!AccountVerification.isLinked()) {
                return@supplyAsync SyncResult(false, "Account not linked")
            }

            synchronized(syncLock) {
                cancelPendingSync()
                pendingData = data
            }

            performSyncBlocking()
        }, scheduler)
    }

    /**
     * Cancel any pending sync operations.
     */
    fun cancelPendingSync() {
        synchronized(syncLock) {
            pendingSync?.cancel(false)
            pendingSync = null
        }
    }

    private fun performSync() {
        val result = performSyncBlocking()
        if (!result.success) {
            scheduleRetry()
        }
    }

    private fun performSyncBlocking(): SyncResult {
        val dataToSync: PlayerRngData

        synchronized(syncLock) {
            dataToSync = pendingData ?: return SyncResult(false, "No data to sync")
            pendingSync = null
        }

        DyeTrackerMod.info("Syncing RNG data to backend...")

        return try {
            val apiResult = ApiClient.syncRngData(dataToSync)

            when (apiResult) {
                is ApiClient.ApiResult.Success -> {
                    // Also sync dye collection if present
                    syncDyeCollectionIfPresent(dataToSync.dyeCollection)

                    synchronized(syncLock) {
                        pendingData = null
                        lastSyncTime = System.currentTimeMillis()
                        lastSyncSuccess = true
                        retryAttempts.set(0)
                        currentRetryDelay.set(INITIAL_RETRY_DELAY_MS)
                    }
                    DyeTrackerMod.info("RNG data synced successfully")
                    SyncResult(true, "Sync successful")
                }
                is ApiClient.ApiResult.Error -> {
                    synchronized(syncLock) {
                        lastSyncSuccess = false
                    }
                    DyeTrackerMod.warn("Sync failed: {} ({})", apiResult.message, apiResult.statusCode)
                    SyncResult(false, apiResult.message)
                }
            }
        } catch (e: Exception) {
            synchronized(syncLock) {
                lastSyncSuccess = false
            }
            DyeTrackerMod.error("Sync exception", e)
            SyncResult(false, "Network error: ${e.message}")
        }
    }

    /**
     * Sync dye collection to the backend if collection data is present.
     * Called after successful RNG data sync to piggyback on the same debounce.
     */
    private fun syncDyeCollectionIfPresent(collection: DyeCollection?) {
        if (collection == null || collection.dyes.isEmpty()) return

        DyeTrackerMod.info("Syncing dye collection ({} dyes)...", collection.dyes.size)
        try {
            val result = ApiClient.syncDyeCollection(collection)
            when (result) {
                is ApiClient.ApiResult.Success -> {
                    DyeTrackerMod.info("Dye collection synced: {} dyes", result.data.syncedCount)
                }
                is ApiClient.ApiResult.Error -> {
                    DyeTrackerMod.warn("Dye collection sync failed: {}", result.message)
                }
            }
        } catch (e: Exception) {
            DyeTrackerMod.error("Dye collection sync exception", e)
        }
    }

    private fun scheduleRetry() {
        val attempts = retryAttempts.incrementAndGet()

        if (attempts > MAX_RETRY_ATTEMPTS) {
            DyeTrackerMod.warn("Max retry attempts reached, giving up on sync")
            retryAttempts.set(0)
            currentRetryDelay.set(INITIAL_RETRY_DELAY_MS)
            return
        }

        val delay = currentRetryDelay.get()
        DyeTrackerMod.info("Scheduling sync retry {} of {} in {}s", attempts, MAX_RETRY_ATTEMPTS, delay / 1000)

        synchronized(syncLock) {
            pendingSync = scheduler.schedule({
                performSync()
            }, delay, TimeUnit.MILLISECONDS)
        }

        // Exponential backoff: double the delay, capped at max
        currentRetryDelay.updateAndGet { d ->
            minOf(d * 2, MAX_RETRY_DELAY_MS)
        }
    }

    /**
     * Result of a sync operation.
     */
    data class SyncResult(
        val success: Boolean,
        val message: String
    )
}
