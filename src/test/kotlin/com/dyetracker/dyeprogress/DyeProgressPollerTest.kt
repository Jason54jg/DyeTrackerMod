package com.dyetracker.dyeprogress

import com.dyetracker.api.ApiClient
import com.dyetracker.api.DyeProgressResponse
import com.dyetracker.api.ProfileSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit coverage for [DyeProgressPoller.pollProfiles] (task 34-4): one fetch per DISTINCT
 * profile, and no-op gating when not linked / no username / no widgets. The 30s timer and real
 * network are exercised in 34-8; here a fake fetcher records calls.
 */
class DyeProgressPollerTest {

    private class Recorder {
        val fetched = mutableListOf<String>()
        val updated = mutableListOf<String>()

        fun fetch(username: String, profileId: String): ApiClient.ApiResult<DyeProgressResponse> {
            fetched += profileId
            return ApiClient.ApiResult.Success(
                DyeProgressResponse(
                    username = "Notch",
                    profile = ProfileSummary(id = profileId, cuteName = "Mango"),
                    dyes = emptyList(),
                    modSyncTimestamp = null,
                ),
            )
        }

        fun update(profileId: String, result: ApiClient.ApiResult<DyeProgressResponse>) {
            updated += profileId
        }
    }

    @Test
    fun `fetches each distinct profile exactly once`() {
        val rec = Recorder()
        val fetched = DyeProgressPoller.pollProfiles(
            isLinked = true,
            username = "Notch",
            profileIds = listOf("A", "A", "B"),
            fetch = rec::fetch,
            update = rec::update,
        )

        assertEquals(setOf("A", "B"), fetched)
        assertEquals(setOf("A", "B"), rec.fetched.toSet())
        assertEquals(2, rec.fetched.size) // not 3 — the duplicate A is de-duped
        assertEquals(setOf("A", "B"), rec.updated.toSet())
    }

    @Test
    fun `no-op when not linked`() {
        val rec = Recorder()
        val fetched = DyeProgressPoller.pollProfiles(
            isLinked = false,
            username = "Notch",
            profileIds = listOf("A", "B"),
            fetch = rec::fetch,
            update = rec::update,
        )

        assertTrue(fetched.isEmpty())
        assertTrue(rec.fetched.isEmpty())
        assertTrue(rec.updated.isEmpty())
    }

    @Test
    fun `no-op when username is blank`() {
        val rec = Recorder()
        val fetched = DyeProgressPoller.pollProfiles(
            isLinked = true,
            username = "   ",
            profileIds = listOf("A"),
            fetch = rec::fetch,
            update = rec::update,
        )

        assertTrue(fetched.isEmpty())
        assertTrue(rec.fetched.isEmpty())
    }

    @Test
    fun `no-op when there are no widgets`() {
        val rec = Recorder()
        val fetched = DyeProgressPoller.pollProfiles(
            isLinked = true,
            username = "Notch",
            profileIds = emptyList(),
            fetch = rec::fetch,
            update = rec::update,
        )

        assertTrue(fetched.isEmpty())
        assertTrue(rec.fetched.isEmpty())
    }

    @Test
    fun `blank profile ids are ignored`() {
        val rec = Recorder()
        val fetched = DyeProgressPoller.pollProfiles(
            isLinked = true,
            username = "Notch",
            profileIds = listOf("", "  ", "B"),
            fetch = rec::fetch,
            update = rec::update,
        )

        assertEquals(setOf("B"), fetched)
        assertEquals(listOf("B"), rec.fetched)
    }
}
