package com.dyetracker.events

import com.dyetracker.DyeTrackerMod
import com.dyetracker.data.DungeonFloor
import com.dyetracker.data.RngDataStore
import com.dyetracker.data.SlayerType
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.screen.slot.Slot

/**
 * Handles inventory screen events to capture RNG meter data.
 * Detects when RNG meter inventories are opened and scans for selected items.
 */
object InventoryHandler {

    // Delay in ticks before scanning inventory (allows GUI to fully load)
    private const val SCAN_DELAY_TICKS = 5

    /**
     * Register the screen event listener.
     */
    fun register() {
        ScreenEvents.AFTER_INIT.register { client, screen, scaledWidth, scaledHeight ->
            onScreenInit(client, screen)
        }
        DyeTrackerMod.info("InventoryHandler registered")
    }

    private fun onScreenInit(client: MinecraftClient, screen: Screen) {
        if (screen !is HandledScreen<*>) {
            return
        }

        val title = screen.title.string
        val inventoryType = InventoryUtils.detectInventoryType(title) ?: return

        DyeTrackerMod.debug("Detected RNG meter inventory: {} ({})", title, inventoryType)

        // Schedule scan after a short delay to ensure inventory is loaded
        scheduleScan(client, screen, inventoryType)
    }

    private fun scheduleScan(client: MinecraftClient, screen: HandledScreen<*>, inventoryType: InventoryType) {
        var ticksRemaining = SCAN_DELAY_TICKS
        var processed = false

        ScreenEvents.afterTick(screen).register { _ ->
            if (processed) return@register
            ticksRemaining--
            if (ticksRemaining <= 0) {
                processed = true
                processInventory(screen, inventoryType)
            }
        }
    }

    private fun processInventory(screen: HandledScreen<*>, inventoryType: InventoryType) {
        when (inventoryType) {
            is InventoryType.SlayerRngMeter -> processSlayerMeter(screen, inventoryType.slayerType)
            is InventoryType.DungeonRngMeter -> processDungeonMeter(screen, inventoryType.floor)
            is InventoryType.NucleusRngMeter -> processNucleusMeter(screen)
            is InventoryType.ExperimentationRngMeter -> processExperimentationMeter(screen)
            is InventoryType.Commissions -> processCommissions(screen)
        }
    }

    /**
     * Process a slayer RNG meter inventory.
     */
    private fun processSlayerMeter(screen: HandledScreen<*>, slayerType: SlayerType) {
        val handler = screen.screenHandler
        var storedXp = 0L
        var selectedItem: String? = null
        var goalXp: Long? = null

        for (slot in handler.slots) {
            val stack = slot.stack
            if (stack.isEmpty) continue

            val lore = InventoryUtils.getLore(stack)

            // Check for stored XP
            val currentXp = InventoryUtils.extractCurrentXp(lore)
            if (currentXp != null && currentXp > storedXp) {
                storedXp = currentXp
            }

            // Check if this is a selected item
            val selected = InventoryUtils.parseSelectedItem(stack)
            if (selected != null) {
                selectedItem = selected.itemName
                goalXp = selected.goalXp
                DyeTrackerMod.debug(
                    "Found selected slayer item: {} for {} (goal: {})",
                    selectedItem,
                    slayerType,
                    goalXp
                )
            }
        }

        // Update XP first (preserves selection), then update selection (preserves XP)
        if (storedXp > 0) {
            RngDataStore.updateSlayerXp(slayerType, storedXp)
        }
        if (selectedItem != null) {
            RngDataStore.updateSlayerSelection(slayerType, selectedItem, goalXp ?: 0L)
        }

        DyeTrackerMod.debug("Updated slayer meter {}: xp={}, item={}", slayerType, storedXp, selectedItem)
    }

    /**
     * Process a dungeon RNG meter inventory.
     */
    private fun processDungeonMeter(screen: HandledScreen<*>, floor: DungeonFloor) {
        val handler = screen.screenHandler
        var storedXp = 0L
        var selectedItem: String? = null
        var goalXp: Long? = null

        for (slot in handler.slots) {
            val stack = slot.stack
            if (stack.isEmpty) continue

            val lore = InventoryUtils.getLore(stack)

            // Check if this item shows stored XP (usually a status item)
            val currentXp = InventoryUtils.extractCurrentXp(lore)
            if (currentXp != null && currentXp > storedXp) {
                storedXp = currentXp
            }

            // Check if this is a selected item
            val selected = InventoryUtils.parseSelectedItem(stack)
            if (selected != null) {
                selectedItem = selected.itemName
                goalXp = selected.goalXp
                DyeTrackerMod.debug(
                    "Found selected dungeon item: {} for {} (goal: {})",
                    selectedItem,
                    floor,
                    goalXp
                )
            }
        }

        RngDataStore.updateDungeonMeter(floor, storedXp, selectedItem, goalXp)
        DyeTrackerMod.debug("Updated dungeon meter {}: xp={}, item={}", floor, storedXp, selectedItem)
    }

    /**
     * Process a Nucleus RNG meter inventory.
     */
    private fun processNucleusMeter(screen: HandledScreen<*>) {
        val handler = screen.screenHandler
        var storedXp = 0L
        var selectedItem: String? = null
        var goalXp: Long? = null

        for (slot in handler.slots) {
            val stack = slot.stack
            if (stack.isEmpty) continue

            val lore = InventoryUtils.getLore(stack)

            // Check for stored XP
            val currentXp = InventoryUtils.extractCurrentXp(lore)
            if (currentXp != null && currentXp > storedXp) {
                storedXp = currentXp
            }

            // Check if this is a selected item
            val selected = InventoryUtils.parseSelectedItem(stack)
            if (selected != null) {
                selectedItem = selected.itemName
                goalXp = selected.goalXp
                DyeTrackerMod.debug("Found selected nucleus item: {} (goal: {})", selectedItem, goalXp)
            }
        }

        RngDataStore.updateNucleusMeter(storedXp, selectedItem, goalXp)
        DyeTrackerMod.debug("Updated nucleus meter: xp={}, item={}", storedXp, selectedItem)
    }

    /**
     * Process a Commissions inventory to capture the total commission count for Nyanza Dye.
     * Scans for the "Commission Milestones" item and parses the total completed count from its lore.
     */
    private fun processCommissions(screen: HandledScreen<*>) {
        val handler = screen.screenHandler

        for (slot in handler.slots) {
            val stack = slot.stack
            if (stack.isEmpty) continue

            val count = InventoryUtils.parseCommissionMilestoneCount(stack)
            if (count != null) {
                RngDataStore.updateCommissionsCompleted(count)
                DyeTrackerMod.debug("Updated commission milestone count: {}", count)
                return
            }
        }

        DyeTrackerMod.debug("Commission Milestones item not found in Commissions GUI")
    }

    /**
     * Process an Experimentation Table RNG meter inventory.
     */
    private fun processExperimentationMeter(screen: HandledScreen<*>) {
        val handler = screen.screenHandler
        var storedXp = 0L
        var selectedItem: String? = null
        var goalXp: Long? = null

        for (slot in handler.slots) {
            val stack = slot.stack
            if (stack.isEmpty) continue

            val lore = InventoryUtils.getLore(stack)

            // Check for stored XP
            val currentXp = InventoryUtils.extractCurrentXp(lore)
            if (currentXp != null && currentXp > storedXp) {
                storedXp = currentXp
            }

            // Check if this is a selected item
            val selected = InventoryUtils.parseSelectedItem(stack)
            if (selected != null) {
                selectedItem = selected.itemName
                goalXp = selected.goalXp
                DyeTrackerMod.debug("Found selected experimentation item: {} (goal: {})", selectedItem, goalXp)
            }
        }

        RngDataStore.updateExperimentationMeter(storedXp, selectedItem, goalXp)
        DyeTrackerMod.debug("Updated experimentation meter: xp={}, item={}", storedXp, selectedItem)
    }
}
