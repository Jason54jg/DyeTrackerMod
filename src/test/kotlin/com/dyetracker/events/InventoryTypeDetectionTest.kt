package com.dyetracker.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [InventoryUtils.detectInventoryType] on Vincent's screens. Hypixel renamed the
 * collection screen from "Dye Compendium" to "Dye Guide" (PBI 62), with a page prefix such as
 * "(1/3) Dye Guide"; every variant must be detected. PBI 63: RNG-meter screens are no longer
 * captured, so their titles must not be detected.
 */
class InventoryTypeDetectionTest {

    @Test
    fun `Dye Guide is the Vincent dye collection`() {
        assertEquals(InventoryType.VincentDyeCollection, InventoryUtils.detectInventoryType("Dye Guide"))
    }

    @Test
    fun `formatted Dye Guide title is detected after stripping`() {
        assertEquals(InventoryType.VincentDyeCollection, InventoryUtils.detectInventoryType("§8Dye Guide"))
    }

    @Test
    fun `legacy Dye Compendium title is still the Vincent dye collection`() {
        assertEquals(InventoryType.VincentDyeCollection, InventoryUtils.detectInventoryType("Dye Compendium"))
    }

    @Test
    fun `Dyes is still the Vincent rotation screen`() {
        assertEquals(InventoryType.VincentDyeRotation, InventoryUtils.detectInventoryType("Dyes"))
    }

    @Test
    fun `paged Dye Guide titles are the Vincent dye collection`() {
        assertEquals(InventoryType.VincentDyeCollection, InventoryUtils.detectInventoryType("(1/3) Dye Guide"))
        assertEquals(InventoryType.VincentDyeCollection, InventoryUtils.detectInventoryType("§8(3/3) Dye Guide"))
    }

    @Test
    fun `Brewing Stand is the brewing stand screen (PBI 66)`() {
        assertEquals(InventoryType.BrewingStand, InventoryUtils.detectInventoryType("Brewing Stand"))
    }

    @Test
    fun `Brewing Stand Upgrades is not the brewing stand screen (PBI 66)`() {
        assertNull(InventoryUtils.detectInventoryType("Brewing Stand Upgrades"))
    }

    @Test
    fun `unrelated title is not detected`() {
        assertNull(InventoryUtils.detectInventoryType("Dye Shop"))
    }

    @Test
    fun `dungeon RNG meter titles are not detected (PBI 63)`() {
        assertNull(InventoryUtils.detectInventoryType("Catacombs RNG Meter - Master Mode Floor VII"))
        assertNull(InventoryUtils.detectInventoryType("Master Mode Floor V RNG Meter"))
    }

    @Test
    fun `Crystal Hollows RNG meter title is not detected (PBI 63)`() {
        assertNull(InventoryUtils.detectInventoryType("Crystal Hollows RNG Meter"))
    }

    @Test
    fun `Experimentation Table RNG title is not detected (PBI 63)`() {
        assertNull(InventoryUtils.detectInventoryType("Experimentation Table RNG"))
    }
}
