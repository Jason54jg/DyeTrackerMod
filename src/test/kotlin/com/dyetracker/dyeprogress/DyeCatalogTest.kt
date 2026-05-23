package com.dyetracker.dyeprogress

import com.dyetracker.events.InventoryUtils
import com.dyetracker.rotation.DyeSprites
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit coverage for the offline dye picker catalog (task 34-6): it is derived from the single
 * source of truth ([InventoryUtils.dyeDisplayNameToId], which is kept in lock-step with
 * [DyeSprites.DYE_IDS]) and its filter matches by name and id, case-insensitively.
 */
class DyeCatalogTest {

    @Test
    fun `catalog covers every recognizable dye id (single source of truth)`() {
        val catalogIds = DyeCatalog.all.map { it.dyeId }.toSet()
        assertEquals(InventoryUtils.dyeDisplayNameToIdValues().toSet(), catalogIds)
        // And therefore every catalog dye has a bundled sprite.
        assertTrue(DyeSprites.DYE_IDS.containsAll(catalogIds))
    }

    @Test
    fun `display names are title-cased`() {
        val byId = DyeCatalog.all.associateBy { it.dyeId }
        assertEquals("Wild Strawberry", byId.getValue("wild_strawberry").displayName)
        assertEquals("Brick Red", byId.getValue("brick_red").displayName)
        assertEquals("Matcha", byId.getValue("matcha").displayName)
    }

    @Test
    fun `empty query returns all options`() {
        assertEquals(DyeCatalog.all.size, DyeCatalog.filter("").size)
        assertEquals(DyeCatalog.all.size, DyeCatalog.filter("   ").size)
    }

    @Test
    fun `filter matches by display name case-insensitively`() {
        val results = DyeCatalog.filter("STRAW")
        assertTrue(results.any { it.dyeId == "wild_strawberry" })
        assertTrue(results.all { it.displayName.lowercase().contains("straw") || it.dyeId.contains("straw") })
    }

    @Test
    fun `filter matches by snake_case id`() {
        val results = DyeCatalog.filter("brick_red")
        assertTrue(results.any { it.dyeId == "brick_red" })
    }

    @Test
    fun `no match returns empty`() {
        assertTrue(DyeCatalog.filter("zzzznotadye").isEmpty())
    }
}
