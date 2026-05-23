package com.dyetracker.dyeprogress

import com.dyetracker.api.ProfileSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit coverage for the pure profile-name → profileId resolution shared by the add panel
 * (task 34-6) and the add command (task 34-7).
 */
class ProfileResolutionTest {

    private val profiles = listOf(
        ProfileSummary(id = "p-mango", cuteName = "Mango"),
        ProfileSummary(id = "p-banana", cuteName = "Banana"),
        ProfileSummary(id = "p-apple", cuteName = "Apple"),
    )

    @Test
    fun `exact name resolves to its profile id`() {
        assertEquals("p-banana", DyeProgressAdder.resolveProfileId(profiles, "Banana"))
    }

    @Test
    fun `match is case-insensitive`() {
        assertEquals("p-mango", DyeProgressAdder.resolveProfileId(profiles, "mango"))
        assertEquals("p-apple", DyeProgressAdder.resolveProfileId(profiles, "APPLE"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("p-mango", DyeProgressAdder.resolveProfileId(profiles, "  Mango  "))
    }

    @Test
    fun `no match returns null`() {
        assertNull(DyeProgressAdder.resolveProfileId(profiles, "Coconut"))
    }

    @Test
    fun `blank input returns null`() {
        assertNull(DyeProgressAdder.resolveProfileId(profiles, ""))
        assertNull(DyeProgressAdder.resolveProfileId(profiles, "   "))
    }

    @Test
    fun `empty profile list returns null`() {
        assertNull(DyeProgressAdder.resolveProfileId(emptyList(), "Mango"))
    }
}
