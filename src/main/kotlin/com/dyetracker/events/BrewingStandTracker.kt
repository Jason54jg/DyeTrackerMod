package com.dyetracker.events

import com.dyetracker.data.PotionTier

/**
 * Counts finished potions as they leave the SkyBlock Brewing Stand (PBI 66, Mocha Dye).
 *
 * Pure Kotlin with no Minecraft types: the caller passes a snapshot of the stand's container slots
 * on every tick, and gets back the tiers of the potions that left the stand since the last one.
 * Watching the slots (rather than clicks) covers click, shift-click, hotkeys and the `$` sell
 * button alike.
 *
 * One tracker per opened screen. A snapshot where every slot is empty, seen before any item has
 * been seen, is ignored: the server sends the window contents after the screen opens, so the
 * first ticks can show an empty stand. The first snapshot with any item is the first observation;
 * after that, an all-empty snapshot is a real removal and is handled as usual.
 *
 * For each slot it keeps the last item name and whether the player put that item in:
 * - **first observation of a slot** (the screen just opened): remember the name, not placed by the
 *   player. Nothing is counted.
 * - **same name**: nothing changes.
 * - **empty -> item**: the player put it in.
 * - **item A -> different item B**: the item changed in place (it brewed), so it is no longer the
 *   item the player put in.
 * - **item A -> empty**: if the player did not put A in and A's name has a tier (see
 *   [InventoryUtils.parsePotionTier]), A's tier is counted. The slot is then empty.
 *
 * A slot missing from a later snapshot keeps its state. So a potion put in and taken out without
 * changing is never counted, and a brewed potion is counted once, when it leaves.
 *
 * Known gap: a potion that was already in the stand when the screen opened is counted when taken
 * out, even if it was never brewed there, because the tracker cannot tell how it got there. A
 * one-tick swap (clicking a stand slot while holding another potion) looks like a brew in place, so
 * the potion taken out is not counted and the player's own potion is counted when removed.
 */
class BrewingStandTracker {

    private data class SlotState(val name: String?, val placedByPlayer: Boolean)

    private val slots = mutableMapOf<Int, SlotState>()

    // Becomes true at the first snapshot with any item; until then all-empty snapshots are ignored
    private var loaded = false

    /**
     * Compare [slotNames] (slot index -> stripped item name, or null for an empty slot) with the
     * last snapshot and return the tiers of the potions that left the stand, in slot order of
     * [slotNames]. Returns nothing for an all-empty snapshot before the stand has loaded.
     */
    fun observe(slotNames: Map<Int, String?>): List<PotionTier> {
        if (!loaded) {
            if (slotNames.values.all { it == null }) return emptyList()
            loaded = true
        }
        val removed = mutableListOf<PotionTier>()
        for ((index, name) in slotNames) {
            val previous = slots[index]
            slots[index] = when {
                previous == null -> SlotState(name, placedByPlayer = false)
                previous.name == name -> previous
                previous.name == null -> SlotState(name, placedByPlayer = true)
                name != null -> SlotState(name, placedByPlayer = false)
                else -> {
                    if (!previous.placedByPlayer) {
                        InventoryUtils.parsePotionTier(previous.name)?.let { removed.add(it) }
                    }
                    SlotState(null, placedByPlayer = false)
                }
            }
        }
        return removed
    }
}
