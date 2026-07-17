package com.dyetracker.events

import com.dyetracker.DyeTrackerMod
import com.dyetracker.data.RngDataStore
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
//? if >=26.1 {
/*import net.minecraft.client.Minecraft as MinecraftClient
*///?} else {
import net.minecraft.client.MinecraftClient
//?}

/**
 * Handles tablist parsing to capture Glacite Mineshaft pity value.
 * The pity value appears in the player's tablist when in certain areas.
 */
object TablistHandler {

    // Pattern for mineshaft pity: "Glacite Mineshafts: X/2,000"
    private val MINESHAFT_PATTERN = Regex("""Glacite Mineshafts: ([\d,]+)/2,000""")

    // Poll interval in ticks (40 ticks = 2 seconds at 20 TPS)
    private const val POLL_INTERVAL_TICKS = 40

    // Maximum pity value
    private const val MAX_PITY = 2000

    private var tickCounter = 0
    private var lastPityValue: Int? = null

    /**
     * Register the tick event listener for periodic tablist polling.
     */
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            tickCounter++
            if (tickCounter >= POLL_INTERVAL_TICKS) {
                tickCounter = 0
                pollTablist(client)
            }
        }
        DyeTrackerMod.info("TablistHandler registered")
    }

    private fun pollTablist(client: MinecraftClient) {
        //? if >=26.1 {
        /*val networkHandler = client.connection ?: return
        val playerList = networkHandler.listedOnlinePlayers ?: return
        *///?} else {
        val networkHandler = client.networkHandler ?: return
        val playerList = networkHandler.playerList ?: return
        //?}

        for (entry in playerList) {
            //? if >=26.1 {
            /*val displayName = entry.tabListDisplayName ?: continue
            *///?} else {
            val displayName = entry.displayName ?: continue
            //?}
            val text = displayName.string

            // Strip color codes for matching
            val cleanText = InventoryUtils.stripFormatting(text)

            val matchResult = MINESHAFT_PATTERN.find(cleanText)
            if (matchResult != null) {
                val pityString = matchResult.groupValues[1]
                val pityValue = parseNumber(pityString)

                if (pityValue != null && pityValue in 0..MAX_PITY) {
                    // Only log if value changed
                    if (pityValue != lastPityValue) {
                        DyeTrackerMod.debug("Mineshaft pity updated: {}/{}", pityValue, MAX_PITY)
                        lastPityValue = pityValue
                    }
                    RngDataStore.updateMineshaftPity(pityValue)
                }
                return
            }
        }
    }

    /**
     * Parse a number string that may contain commas.
     */
    private fun parseNumber(text: String): Int? {
        return text.replace(",", "").toIntOrNull()
    }

    /**
     * Get the last detected pity value (for testing/debugging).
     */
    fun getLastPityValue(): Int? = lastPityValue

    /**
     * Reset state (for testing).
     */
    fun reset() {
        tickCounter = 0
        lastPityValue = null
    }
}
