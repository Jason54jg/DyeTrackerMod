package com.dyetracker.events

import com.dyetracker.DyeTrackerMod
import com.dyetracker.config.ConfigManager
import com.dyetracker.data.DroppedDye
import com.dyetracker.data.DungeonFloor
import com.dyetracker.data.RngDataStore
import com.dyetracker.data.VisitorEntry
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.screen.slot.Slot
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * Handles inventory screen events to capture RNG meter data.
 * Detects when RNG meter inventories are opened and scans for selected items.
 */
object InventoryHandler {

    // Delay in ticks before scanning inventory (allows GUI to fully load)
    private const val SCAN_DELAY_TICKS = 5

    // How often to re-scan a paginated GUI (Dye Compendium, Visitor's Logbook) for page changes (in ticks)
    private const val COMPENDIUM_SCAN_INTERVAL_TICKS = 10

    // Accumulated dyes across all pages while the Dye Compendium is open
    private val accumulatedDyes = mutableMapOf<String, DroppedDye>()

    // Accumulated visitors (de-duped by name) across all pages while the Visitor's Logbook is open (PBI 42)
    private val accumulatedVisitors = mutableMapOf<String, VisitorEntry>()

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

        DyeTrackerMod.debug("Detected inventory: {} ({})", title, inventoryType)

        when (inventoryType) {
            is InventoryType.VincentDyeCollection ->
                // Dye Compendium is paginated — scan continuously and accumulate
                scheduleCompendiumScan(client, screen)
            is InventoryType.VisitorLogbook ->
                // Visitor's Logbook is paginated — scan continuously and accumulate (PBI 42)
                scheduleLogbookScan(client, screen)
            else ->
                // Other inventory types: one-shot scan after delay
                scheduleScan(client, screen, inventoryType)
        }
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
            is InventoryType.DungeonRngMeter -> processDungeonMeter(screen, inventoryType.floor)
            is InventoryType.NucleusRngMeter -> processNucleusMeter(screen)
            is InventoryType.ExperimentationRngMeter -> processExperimentationMeter(screen)
            is InventoryType.CommissionMilestones -> processCommissionMilestones(screen)
            is InventoryType.VincentDyeRotation -> processDyeRotation(screen)
            is InventoryType.VincentDyeCollection -> {} // Handled by scheduleCompendiumScan
            is InventoryType.VisitorLogbook -> {} // PBI 42: paginated capture wired in 42-5 (scheduleLogbookScan)
        }
    }

    /**
     * Process the Vincent "Dyes" current-rotation screen: parse the container's dye slots into a
     * [com.dyetracker.data.DyeRotation], persist it (last-write-wins) and notify the player. Skips
     * persistence when no dyes were parsed so a half-loaded or mis-identified screen never clobbers
     * the last-known rotation.
     */
    private fun processDyeRotation(screen: HandledScreen<*>) {
        val player = MinecraftClient.getInstance().player ?: return
        val rotation = InventoryUtils.extractDyeRotation(screen.screenHandler.slots, player.inventory)

        if (rotation.dyeIds.isEmpty()) {
            DyeTrackerMod.debug("Dyes screen detected but no rotation dyes parsed; not persisting")
            return
        }

        ConfigManager.updateDyeRotation(rotation)
        DyeTrackerMod.info("Captured dye rotation ({} dyes): {}", rotation.dyeIds.size, rotation.dyeIds)

        player.sendMessage(
            Text.literal("Captured current dye rotation (${rotation.dyeIds.size} dyes)")
                .formatted(Formatting.GREEN),
            false
        )
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
     * Process a Commission Milestones inventory to capture the total commission count for Nyanza
     * Dye. Scans for the "Milestone I Rewards" item and parses the absolute completed count from
     * its lore (e.g. "208/5"). Milestone I is used because its threshold never changes, so the
     * count is shown stably even for maxed players — unlike the dynamic "Commission Milestones"
     * item in the parent Commissions GUI, which drops the count once every tier is complete.
     */
    private fun processCommissionMilestones(screen: HandledScreen<*>) {
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

        DyeTrackerMod.debug("Milestone I Rewards item not found in Commission Milestones GUI")
    }

    /**
     * Schedule continuous scanning of the Dye Compendium.
     * Scans every COMPENDIUM_SCAN_INTERVAL_TICKS ticks to pick up page changes,
     * accumulates dyes across pages, and finalizes when the screen closes.
     */
    private fun scheduleCompendiumScan(client: MinecraftClient, screen: HandledScreen<*>) {
        accumulatedDyes.clear()
        var ticksSinceLastScan = SCAN_DELAY_TICKS // Start with initial delay worth of ticks

        // Periodic scan while screen is open
        ScreenEvents.afterTick(screen).register { _ ->
            ticksSinceLastScan++
            if (ticksSinceLastScan >= COMPENDIUM_SCAN_INTERVAL_TICKS) {
                ticksSinceLastScan = 0
                scanCompendiumPage(screen)
            }
        }

        // Finalize when screen closes
        ScreenEvents.remove(screen).register { _ ->
            finalizeCompendium()
        }
    }

    /**
     * Scan the current page of the Dye Compendium and accumulate obtained dyes.
     */
    private fun scanCompendiumPage(screen: HandledScreen<*>) {
        val handler = screen.screenHandler
        val pageDyes = InventoryUtils.extractDyeCollection(handler.slots)

        for (dye in pageDyes) {
            if (!accumulatedDyes.containsKey(dye.dyeId)) {
                accumulatedDyes[dye.dyeId] = dye
                DyeTrackerMod.debug("Found obtained dye: {}", dye.dyeId)
            }
        }
    }

    /**
     * Called when the Dye Compendium screen closes.
     * Merges accumulated dyes with existing collection and pushes to the data store.
     */
    private fun finalizeCompendium() {
        if (accumulatedDyes.isEmpty()) {
            DyeTrackerMod.debug("No obtained dyes found in Dye Compendium")
            return
        }

        // Merge with existing collection so previous pages aren't lost
        val existing = RngDataStore.getDyeCollection()
        val merged = mutableMapOf<String, DroppedDye>()
        existing?.dyes?.forEach { merged[it.dyeId] = it }
        merged.putAll(accumulatedDyes) // New data overwrites old for same dye ID

        RngDataStore.updateDyeCollection(merged.values.toList())
        DyeTrackerMod.info(
            "Dye Compendium: {} new dyes this session, {} total in collection",
            accumulatedDyes.size, merged.size
        )
        accumulatedDyes.clear()
    }

    /**
     * Schedule continuous scanning of the Visitor's Logbook (PBI 42).
     * Mirrors [scheduleCompendiumScan]: scans every [COMPENDIUM_SCAN_INTERVAL_TICKS] ticks to pick
     * up page changes, accumulates visitors de-duped by name while this screen is open, and on close
     * merges them into the persisted visitor union (see [finalizeLogbook]).
     */
    private fun scheduleLogbookScan(client: MinecraftClient, screen: HandledScreen<*>) {
        accumulatedVisitors.clear()
        var ticksSinceLastScan = SCAN_DELAY_TICKS // Start with initial delay worth of ticks

        // Periodic scan while screen is open
        ScreenEvents.afterTick(screen).register { _ ->
            ticksSinceLastScan++
            if (ticksSinceLastScan >= COMPENDIUM_SCAN_INTERVAL_TICKS) {
                ticksSinceLastScan = 0
                scanLogbookPage(screen)
            }
        }

        // Finalize when screen closes
        ScreenEvents.remove(screen).register { _ ->
            finalizeLogbook()
        }
    }

    /**
     * Scan the current page of the Visitor's Logbook and accumulate visitors de-duped by name
     * (latest "Times Visited" value wins) via the pure [InventoryUtils.accumulateVisitors].
     */
    private fun scanLogbookPage(screen: HandledScreen<*>) {
        val pageEntries = InventoryUtils.extractVisitorEntriesFromSlots(screen.screenHandler.slots)
        if (pageEntries.isEmpty()) return

        // accumulateVisitors returns a fresh merged map (not an alias of the field), so rebuilding
        // the field from it via clear()+putAll() cannot drop previously-accumulated visitors.
        val merged = InventoryUtils.accumulateVisitors(accumulatedVisitors, pageEntries)
        accumulatedVisitors.clear()
        accumulatedVisitors.putAll(merged)
        DyeTrackerMod.debug(
            "Logbook page scanned: {} visitors this page, {} accumulated",
            pageEntries.size, accumulatedVisitors.size
        )
    }

    /**
     * Called when the Visitor's Logbook screen closes. Merges the visitors accumulated while this
     * screen was open into the persisted visitor union via [RngDataStore.mergeVisitorsSeen], which
     * re-derives the per-tier Copper snapshot from the full union.
     *
     * Crucially this is a MERGE, not a full replace: Hypixel re-creates the Logbook screen on each
     * page-turn, so each page closes and finalizes separately. Merging by visitor name accumulates
     * the per-tier totals across all pages (and across re-opens/restarts) without double-counting,
     * where a full replace would keep only the last page viewed. If nothing was accumulated (e.g. a
     * mis-identified or half-loaded screen), the merge is a no-op — never clobber prior data
     * (mirrors [finalizeCompendium]'s empty guard).
     */
    private fun finalizeLogbook() {
        if (accumulatedVisitors.isEmpty()) {
            DyeTrackerMod.debug("No visitors found in Visitor's Logbook; not writing snapshot")
            return
        }

        RngDataStore.mergeVisitorsSeen(accumulatedVisitors.toMap())
        DyeTrackerMod.info(
            "Visitor's Logbook: merged {} visitors from this view into the seen union",
            accumulatedVisitors.size
        )
        accumulatedVisitors.clear()
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
