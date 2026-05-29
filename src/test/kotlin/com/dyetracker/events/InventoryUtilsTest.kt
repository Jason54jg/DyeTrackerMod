package com.dyetracker.events

import com.dyetracker.data.VisitorEntry
import com.dyetracker.data.VisitorRarity
import com.dyetracker.data.aggregateVisitorsSeenByTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the pure ownership-count parsing in [InventoryUtils.extractOwnedCount].
 * Lore lines are passed already-stripped of formatting codes, as parseDyeItem supplies them.
 */
class InventoryUtilsTest {

    @Test
    fun `dropped count is returned`() {
        assertEquals(3, InventoryUtils.extractOwnedCount(listOf("You've dropped: 3")))
    }

    @Test
    fun `bought count is returned`() {
        assertEquals(5, InventoryUtils.extractOwnedCount(listOf("You've bought: 5")))
    }

    @Test
    fun `max wins when both dropped and bought are present`() {
        val lore = listOf("You've dropped: 2", "You've bought: 5")
        assertEquals(5, InventoryUtils.extractOwnedCount(lore))
    }

    @Test
    fun `max wins when dropped exceeds bought`() {
        val lore = listOf("You've bought: 1", "You've dropped: 9")
        assertEquals(9, InventoryUtils.extractOwnedCount(lore))
    }

    @Test
    fun `no ownership lines yields zero`() {
        val lore = listOf("Hex: #50C878", "Global drops: 438", "Click to collapse!")
        assertEquals(0, InventoryUtils.extractOwnedCount(lore))
    }

    @Test
    fun `empty lore yields zero`() {
        assertEquals(0, InventoryUtils.extractOwnedCount(emptyList()))
    }

    @Test
    fun `explicit zero dropped count yields zero`() {
        assertEquals(0, InventoryUtils.extractOwnedCount(listOf("You've dropped: 0")))
    }

    @Test
    fun `explicit zero bought count yields zero`() {
        assertEquals(0, InventoryUtils.extractOwnedCount(listOf("You've bought: 0")))
    }

    @Test
    fun `whitespace variations are tolerated`() {
        assertEquals(7, InventoryUtils.extractOwnedCount(listOf("You've bought:7")))
        assertEquals(8, InventoryUtils.extractOwnedCount(listOf("You've dropped:    8")))
    }

    @Test
    fun `malformed count without digits yields zero`() {
        val lore = listOf("You've dropped: many", "You've bought:")
        assertEquals(0, InventoryUtils.extractOwnedCount(lore))
    }

    @Test
    fun `count embedded in a longer lore line is found`() {
        val lore = listOf("§7You've dropped: 4 times".let { InventoryUtils.stripFormatting(it) })
        assertEquals(4, InventoryUtils.extractOwnedCount(lore))
    }

    // ==================== Visitor's Logbook parsing (PBI 42) ====================

    /** A realistic visitor tooltip's lore (the visitor name is the item's display name, not lore). */
    private fun visitorLore(rarity: String, timesVisited: Int, offersAccepted: Int = timesVisited) =
        listOf(rarity, "", "Times Visited: $timesVisited", "Offers Accepted: $offersAccepted", "Unlocked by: _Writhe")

    @Test
    fun `extractTimesVisited parses the Times Visited value`() {
        assertEquals(12, InventoryUtils.extractTimesVisited(listOf("Times Visited: 12")))
        assertEquals(0, InventoryUtils.extractTimesVisited(listOf("Times Visited: 0")))
    }

    @Test
    fun `extractTimesVisited returns null when the line is absent`() {
        assertNull(InventoryUtils.extractTimesVisited(listOf("Unlocked by: _Writhe", "RARE")))
        assertNull(InventoryUtils.extractTimesVisited(emptyList()))
    }

    @Test
    fun `extractTimesVisited ignores Offers Accepted and the Times Denied header`() {
        // Neither "Offers Accepted:" nor the third-party "Times Denied:" header must be matched.
        assertNull(InventoryUtils.extractTimesVisited(listOf("Times Denied: 99", "Offers Accepted: 5")))
    }

    @Test
    fun `extractVisitorRarity resolves each of the five tiers`() {
        for (rarity in VisitorRarity.values()) {
            assertEquals(rarity, InventoryUtils.extractVisitorRarity(listOf(rarity.name)))
        }
    }

    @Test
    fun `extractVisitorRarity matches the exact in-game caps tier names`() {
        // Pin the literal wire strings the Logbook renders, so an enum rename can't silently
        // diverge from the GUI text without failing a test.
        assertEquals(VisitorRarity.UNCOMMON, InventoryUtils.extractVisitorRarity(listOf("UNCOMMON")))
        assertEquals(VisitorRarity.RARE, InventoryUtils.extractVisitorRarity(listOf("RARE")))
        assertEquals(VisitorRarity.LEGENDARY, InventoryUtils.extractVisitorRarity(listOf("LEGENDARY")))
        assertEquals(VisitorRarity.MYTHIC, InventoryUtils.extractVisitorRarity(listOf("MYTHIC")))
        assertEquals(VisitorRarity.SPECIAL, InventoryUtils.extractVisitorRarity(listOf("SPECIAL")))
    }

    @Test
    fun `extractVisitorRarity returns null for an unknown sixth tier`() {
        assertNull(InventoryUtils.extractVisitorRarity(listOf("DIVINE", "Times Visited: 3")))
    }

    @Test
    fun `parseVisitorEntry returns an entry for a well-formed visitor item`() {
        val entry = InventoryUtils.parseVisitorEntry("Jotraeline Greatforge", visitorLore("UNCOMMON", 5))
        assertEquals(VisitorEntry("Jotraeline Greatforge", VisitorRarity.UNCOMMON, 5), entry)
    }

    @Test
    fun `parseVisitorEntry returns null for a header item carrying only Times Denied`() {
        assertNull(InventoryUtils.parseVisitorEntry("", listOf("Times Denied: 21")))
    }

    @Test
    fun `parseVisitorEntry returns null for a filler or empty item`() {
        assertNull(InventoryUtils.parseVisitorEntry("Black Stained Glass Pane", emptyList()))
        // Has a rarity line but no Times Visited → still skipped (both are required).
        assertNull(InventoryUtils.parseVisitorEntry("Half-parsed", listOf("RARE", "Unlocked by: _Writhe")))
    }

    @Test
    fun `accumulateVisitors dedups by name with latest value winning`() {
        val first = listOf(VisitorEntry("Alice", VisitorRarity.RARE, 3))
        val afterFirst = InventoryUtils.accumulateVisitors(emptyMap(), first)

        // The same visitor seen again on a re-scan must not double-count — the latest value wins.
        val rescan = listOf(VisitorEntry("Alice", VisitorRarity.RARE, 4))
        val afterRescan = InventoryUtils.accumulateVisitors(afterFirst, rescan)

        assertEquals(1, afterRescan.size)
        assertEquals(4, afterRescan["Alice"]?.timesVisited)
    }

    @Test
    fun `accumulateVisitors accumulates distinct names`() {
        val page1 = listOf(VisitorEntry("Alice", VisitorRarity.RARE, 3))
        val page2 = listOf(VisitorEntry("Bob", VisitorRarity.SPECIAL, 7))

        val merged = InventoryUtils.accumulateVisitors(
            InventoryUtils.accumulateVisitors(emptyMap(), page1),
            page2
        )

        assertEquals(setOf("Alice", "Bob"), merged.keys)
    }

    @Test
    fun `aggregateVisitorsSeenByTier sums timesVisited per rarity`() {
        val visitors = listOf(
            VisitorEntry("Alice", VisitorRarity.RARE, 3),
            VisitorEntry("Bob", VisitorRarity.RARE, 5),
            VisitorEntry("Carol", VisitorRarity.SPECIAL, 7),
            VisitorEntry("Dave", VisitorRarity.UNCOMMON, 2)
        )

        val perTier = aggregateVisitorsSeenByTier(visitors)

        assertEquals(8, perTier[VisitorRarity.RARE])
        assertEquals(7, perTier[VisitorRarity.SPECIAL])
        assertEquals(2, perTier[VisitorRarity.UNCOMMON])
        assertNull(perTier[VisitorRarity.MYTHIC])
    }

    @Test
    fun `aggregateVisitorsSeenByTier returns an empty map for empty input`() {
        assertEquals(emptyMap(), aggregateVisitorsSeenByTier(emptyList()))
    }

    @Test
    fun `Times Denied header never contributes to a per-tier total (end-to-end of pure helpers)`() {
        // A header "item" with the third-party Times Denied overlay parses to nothing,
        // so it can never inflate the aggregated per-tier totals.
        val header = InventoryUtils.parseVisitorEntry("Header", listOf("Times Denied: 21"))
        assertNull(header)

        val realVisitors = listOf(VisitorEntry("Alice", VisitorRarity.RARE, 3))
        val perTier = aggregateVisitorsSeenByTier(
            InventoryUtils.accumulateVisitors(emptyMap(), realVisitors).values
        )
        assertEquals(mapOf(VisitorRarity.RARE to 3), perTier)
    }
}
