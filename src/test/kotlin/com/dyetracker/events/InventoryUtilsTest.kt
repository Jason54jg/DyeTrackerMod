package com.dyetracker.events

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
