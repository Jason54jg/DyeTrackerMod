package com.dyetracker.events

import com.dyetracker.data.TreasureQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the pure per-tier fishing-treasure parsing in
 * [ChatEventHandler.parseTreasureQuality] (PBI 58 task 58-6).
 *
 * Lines are passed already-stripped of Minecraft formatting codes (§X), as `onChatMessage`
 * supplies them — but they retain the `⛃` treasure icon (not a format code), which the parser
 * strips itself. Mirrors the pure-helper structure of [InventoryUtilsTest].
 */
class TreasureCatchParsingTest {

    @Test
    fun `good catch line maps to GOOD`() {
        assertEquals(TreasureQuality.GOOD, ChatEventHandler.parseTreasureQuality("⛃ GOOD CATCH!"))
    }

    @Test
    fun `great catch line maps to GREAT`() {
        assertEquals(TreasureQuality.GREAT, ChatEventHandler.parseTreasureQuality("⛃ GREAT CATCH!"))
    }

    @Test
    fun `outstanding catch line maps to OUTSTANDING`() {
        assertEquals(
            TreasureQuality.OUTSTANDING,
            ChatEventHandler.parseTreasureQuality("⛃ OUTSTANDING CATCH!")
        )
    }

    @Test
    fun `junk-pool variant is counted at the rolled tier (filler word tolerated)`() {
        // Backwater Bayou / Junk Sinker swap the loot to junk, but the dye roll precedes the swap,
        // so the catch still counts at its tier (per 58-1 decision).
        assertEquals(TreasureQuality.GREAT, ChatEventHandler.parseTreasureQuality("⛃ GREAT JUNK CATCH!"))
        assertEquals(TreasureQuality.GOOD, ChatEventHandler.parseTreasureQuality("⛃ GOOD JUNK CATCH!"))
        assertEquals(
            TreasureQuality.OUTSTANDING,
            ChatEventHandler.parseTreasureQuality("⛃ OUTSTANDING JUNK CATCH!")
        )
    }

    @Test
    fun `channel-prefixed player chat is rejected (Party greater-than name)`() {
        // Guild/Party/Co-op channels prepend "Channel > [rank] name:" — still never starts with
        // the tier word, so anchoring rejects it.
        assertNull(ChatEventHandler.parseTreasureQuality("Party > [MVP+] Steve: OUTSTANDING CATCH!"))
        assertNull(ChatEventHandler.parseTreasureQuality("Guild > Steve: GREAT JUNK CATCH"))
    }

    @Test
    fun `the three tiers are mutually exclusive`() {
        // GREAT must not be mis-read as GOOD (substring-style) and vice-versa.
        assertEquals(TreasureQuality.GREAT, ChatEventHandler.parseTreasureQuality("⛃ GREAT CATCH!"))
        assertEquals(
            TreasureQuality.OUTSTANDING,
            ChatEventHandler.parseTreasureQuality("⛃ OUTSTANDING CATCH!")
        )
    }

    @Test
    fun `tolerates missing icon and extra whitespace`() {
        assertEquals(TreasureQuality.GOOD, ChatEventHandler.parseTreasureQuality("GOOD CATCH!"))
        assertEquals(TreasureQuality.GOOD, ChatEventHandler.parseTreasureQuality("   ⛃   GOOD CATCH!"))
    }

    @Test
    fun `player-chat announcement with a rank prefix is rejected (anchoring guard)`() {
        assertNull(ChatEventHandler.parseTreasureQuality("[MVP+] Steve: GREAT JUNK CATCH"))
    }

    @Test
    fun `player-chat announcement without a rank is rejected`() {
        assertNull(ChatEventHandler.parseTreasureQuality("Steve: GOOD CATCH!"))
    }

    @Test
    fun `tier word mid-sentence is not counted (must be at line start)`() {
        assertNull(ChatEventHandler.parseTreasureQuality("You found a GOOD CATCH bonus chest"))
    }

    @Test
    fun `unrelated fishing line does not match any tier`() {
        assertNull(ChatEventHandler.parseTreasureQuality("You caught a Lava Shell!"))
    }

    @Test
    fun `lowercase player text does not match the uppercase server format`() {
        assertNull(ChatEventHandler.parseTreasureQuality("good catch everyone"))
    }

    @Test
    fun `empty and blank lines return null`() {
        assertNull(ChatEventHandler.parseTreasureQuality(""))
        assertNull(ChatEventHandler.parseTreasureQuality("   "))
    }
}
