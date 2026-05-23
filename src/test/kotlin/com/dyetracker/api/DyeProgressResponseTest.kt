package com.dyetracker.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JSON-decoding coverage for the read-side response models added in task 34-2. Mirrors the
 * `ApiClient` Json config (`ignoreUnknownKeys = true`) and feeds representative bodies shaped
 * like the live `player.ts` endpoints, including an untrackable (`progress: null`) entry, an
 * entry missing the optional `isVerified`, and the `cute_name` → `cuteName` mapping. The real
 * network call is exercised in 34-8.
 */
class DyeProgressResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes a dye-progress response including null progress and cute_name mapping`() {
        // Includes extra fields (uuid, lastSyncedAt, profile.selected) to prove ignoreUnknownKeys.
        val body = """
            {
              "uuid": "0000-uuid",
              "username": "Notch",
              "profile": { "id": "profile-1", "cute_name": "Mango", "selected": true },
              "dyes": [
                {
                  "dyeId": "matcha", "name": "Matcha Dye", "color": "#7cb342",
                  "category": "Slayer", "progress": 42.5, "trackable": true,
                  "formula": "slayer xp / cost", "source": "Revenant Slayer",
                  "isVerified": true, "lastSyncedAt": 1700000000000
                },
                {
                  "dyeId": "fossil", "name": "Fossil Dye", "color": "#5d4037",
                  "category": "Mining", "progress": null, "trackable": false,
                  "formula": "Untrackable", "source": "Excavator"
                }
              ],
              "modSyncTimestamp": 1700000000000
            }
        """.trimIndent()

        val result = json.decodeFromString<DyeProgressResponse>(body)

        assertEquals("Notch", result.username)
        assertEquals("profile-1", result.profile.id)
        assertEquals("Mango", result.profile.cuteName)
        assertEquals(1700000000000L, result.modSyncTimestamp)
        assertEquals(2, result.dyes.size)

        val matcha = result.dyes[0]
        assertEquals("matcha", matcha.dyeId)
        assertEquals(42.5, matcha.progress)
        assertTrue(matcha.trackable)
        assertTrue(matcha.isVerified)
        assertEquals("#7cb342", matcha.color)

        val fossil = result.dyes[1]
        assertNull(fossil.progress) // untrackable
        assertFalse(fossil.trackable)
        assertFalse(fossil.isVerified) // absent key falls back to the default
    }

    @Test
    fun `decodes a dye-progress response with a null modSyncTimestamp`() {
        val body = """
            {
              "username": "Steve",
              "profile": { "id": "p", "cute_name": "Strawberry" },
              "dyes": [],
              "modSyncTimestamp": null
            }
        """.trimIndent()

        val result = json.decodeFromString<DyeProgressResponse>(body)

        assertNull(result.modSyncTimestamp)
        assertTrue(result.dyes.isEmpty())
        assertEquals("Strawberry", result.profile.cuteName)
    }

    @Test
    fun `dye entry defaults formula and source to null when absent`() {
        val body = """
            {
              "username": "Steve",
              "profile": { "id": "p", "cute_name": "Strawberry" },
              "dyes": [
                { "dyeId": "lava", "name": "Lava Dye", "color": "#ff5722", "category": "Mining" }
              ],
              "modSyncTimestamp": null
            }
        """.trimIndent()

        val entry = json.decodeFromString<DyeProgressResponse>(body).dyes.single()

        assertNull(entry.formula)
        assertNull(entry.source)
        assertNull(entry.progress)
        assertFalse(entry.trackable)
        assertFalse(entry.isVerified)
    }

    @Test
    fun `decodes a player-profiles response with no profiles`() {
        val body = """
            { "uuid": "abc-123", "username": "Notch", "profiles": [] }
        """.trimIndent()

        val result = json.decodeFromString<PlayerProfilesResponse>(body)

        assertTrue(result.profiles.isEmpty())
        assertEquals("Notch", result.username)
    }

    @Test
    fun `decodes a player-profiles response mapping cute_name and ignoring extras`() {
        val body = """
            {
              "uuid": "abc-123",
              "username": "Notch",
              "profiles": [
                { "id": "p1", "cute_name": "Mango", "selected": true, "game_mode": "ironman" },
                { "id": "p2", "cute_name": "Banana" }
              ]
            }
        """.trimIndent()

        val result = json.decodeFromString<PlayerProfilesResponse>(body)

        assertEquals("abc-123", result.uuid)
        assertEquals(2, result.profiles.size)
        assertEquals("p1", result.profiles[0].id)
        assertEquals("Mango", result.profiles[0].cuteName)
        assertEquals("p2", result.profiles[1].id)
        assertEquals("Banana", result.profiles[1].cuteName)
    }

    @Test
    fun `decodes an API error body`() {
        val body = """
            { "error": "PlayerNotFound", "message": "Player not found", "status": 404 }
        """.trimIndent()

        val result = json.decodeFromString<ApiErrorResponse>(body)

        assertEquals("Player not found", result.message)
        assertEquals(404, result.status)
    }
}
