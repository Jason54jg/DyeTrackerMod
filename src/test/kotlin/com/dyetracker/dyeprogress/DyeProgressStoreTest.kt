package com.dyetracker.dyeprogress

import com.dyetracker.api.ApiClient
import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.api.DyeProgressResponse
import com.dyetracker.api.ProfileSummary
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit coverage for [DyeProgressStore] state transitions (task 34-4): success populates OK +
 * per-dye map; a subsequent failure flips to ERROR while retaining the prior data; unknown
 * profiles/dyes return LOADING/null.
 */
class DyeProgressStoreTest {

    @BeforeTest
    fun reset() = DyeProgressStore.clear()

    private fun entry(dyeId: String, progress: Double?) = DyeProgressEntry(
        dyeId = dyeId,
        name = dyeId.replaceFirstChar { it.uppercase() },
        color = "#ffffff",
        category = "Slayer",
        progress = progress,
        trackable = progress != null,
    )

    private fun success(profileId: String, vararg entries: DyeProgressEntry, modSync: Long? = 123L) =
        ApiClient.ApiResult.Success(
            DyeProgressResponse(
                username = "Notch",
                profile = ProfileSummary(id = profileId, cuteName = "Mango"),
                dyes = entries.toList(),
                modSyncTimestamp = modSync,
            ),
        )

    @Test
    fun `unknown profile reads LOADING and null entry`() {
        assertEquals(DyeProgressStore.Status.LOADING, DyeProgressStore.status("nope"))
        assertNull(DyeProgressStore.entry("nope", "matcha"))
    }

    @Test
    fun `success populates OK status and per-dye entries`() {
        DyeProgressStore.update("p1", success("p1", entry("matcha", 42.0), entry("celeste", 10.0)))

        assertEquals(DyeProgressStore.Status.OK, DyeProgressStore.status("p1"))
        assertEquals(42.0, DyeProgressStore.entry("p1", "matcha")?.progress)
        assertEquals(10.0, DyeProgressStore.entry("p1", "celeste")?.progress)
        assertEquals(123L, DyeProgressStore.modSyncTimestamp("p1"))
        assertTrue((DyeProgressStore.lastUpdatedMs("p1") ?: 0L) > 0L)
    }

    @Test
    fun `unknown dye in a known profile returns null`() {
        DyeProgressStore.update("p1", success("p1", entry("matcha", 42.0)))
        assertNull(DyeProgressStore.entry("p1", "unknown_dye"))
    }

    @Test
    fun `failure after success flips to ERROR but retains prior data`() {
        DyeProgressStore.update("p1", success("p1", entry("matcha", 42.0)))
        val priorUpdatedMs = DyeProgressStore.lastUpdatedMs("p1")

        DyeProgressStore.update("p1", ApiClient.ApiResult.Error("network down"))

        assertEquals(DyeProgressStore.Status.ERROR, DyeProgressStore.status("p1"))
        // Last known value is still readable so the widget doesn't flicker to empty.
        assertEquals(42.0, DyeProgressStore.entry("p1", "matcha")?.progress)
        assertEquals(123L, DyeProgressStore.modSyncTimestamp("p1"))
        assertEquals(priorUpdatedMs, DyeProgressStore.lastUpdatedMs("p1"))
    }

    @Test
    fun `failure with no prior data is ERROR with no entries`() {
        DyeProgressStore.update("p2", ApiClient.ApiResult.Error("network down"))

        assertEquals(DyeProgressStore.Status.ERROR, DyeProgressStore.status("p2"))
        assertNull(DyeProgressStore.entry("p2", "matcha"))
        assertNull(DyeProgressStore.lastUpdatedMs("p2"))
    }

    @Test
    fun `a later success replaces the cached data`() {
        DyeProgressStore.update("p1", success("p1", entry("matcha", 42.0)))
        DyeProgressStore.update("p1", success("p1", entry("matcha", 88.0), entry("jade", 5.0)))

        assertEquals(DyeProgressStore.Status.OK, DyeProgressStore.status("p1"))
        assertEquals(88.0, DyeProgressStore.entry("p1", "matcha")?.progress)
        assertEquals(5.0, DyeProgressStore.entry("p1", "jade")?.progress)
    }
}
