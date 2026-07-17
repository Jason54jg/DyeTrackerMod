package com.dyetracker.events

import com.dyetracker.DyeTrackerMod
import com.dyetracker.data.RngDataStore
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
//? if >=26.1 {
/*import net.minecraft.network.chat.Component as Text
*///?} else {
import net.minecraft.text.Text
//?}

/**
 * Handles chat messages to capture RNG-relevant events (Archfiend Dice rolls).
 *
 * PBI 47: slayer RNG-meter chat capture was removed. Slayer dyes are now estimated
 * from per-tier boss kill counts on the backend, so the mod no longer tracks the
 * active slayer or parses "RNG Meter … Stored XP" lines.
 *
 * PBI 42: the Garden visitor "OFFER ACCEPTED" chat capture was removed. Copper Dye
 * progress is now sourced from a full snapshot of every visitor seen, read from the
 * in-game Visitor's Logbook GUI (see InventoryHandler), not from accepted-offer chat.
 */
object ChatEventHandler {

    // Pattern to strip Minecraft formatting codes (§ followed by any character)
    private val FORMAT_CODE_PATTERN = Regex("""§.""")

    // Archfiend Dice patterns - check High Class first (more specific pattern)
    private val HIGH_CLASS_DICE_PATTERN = Regex("""Your High Class Archfiend Dice rolled a \d!""")
    private val ARCHFIEND_DICE_PATTERN = Regex("""Your Archfiend Dice rolled a \d!""")

    /**
     * Register the chat event listener.
     */
    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) {
                onChatMessage(message)
            }
        }
        DyeTrackerMod.info("ChatEventHandler registered")
    }

    private fun onChatMessage(message: Text) {
        val rawText = message.string
        // Strip Minecraft formatting codes (§X) before pattern matching
        val text = FORMAT_CODE_PATTERN.replace(rawText, "")

        // Check for Archfiend Dice rolls - check High Class first (more specific pattern)
        if (HIGH_CLASS_DICE_PATTERN.containsMatchIn(text)) {
            RngDataStore.incrementHighClassDiceRoll()
            DyeTrackerMod.debug("Detected High Class Archfiend Dice roll")
            return
        }

        if (ARCHFIEND_DICE_PATTERN.containsMatchIn(text)) {
            RngDataStore.incrementArchfiendDiceRoll()
            DyeTrackerMod.debug("Detected Archfiend Dice roll")
            return
        }
    }
}
