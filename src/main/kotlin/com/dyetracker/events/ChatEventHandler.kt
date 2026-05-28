package com.dyetracker.events

import com.dyetracker.DyeTrackerMod
import com.dyetracker.data.RngDataStore
import com.dyetracker.data.VisitorRarity
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.text.Text

/**
 * Handles chat messages to capture RNG-relevant events (Archfiend Dice rolls and
 * Garden visitor offer acceptances).
 *
 * PBI 47: slayer RNG-meter chat capture was removed. Slayer dyes are now estimated
 * from per-tier boss kill counts on the backend, so the mod no longer tracks the
 * active slayer or parses "RNG Meter … Stored XP" lines.
 */
object ChatEventHandler {

    // Pattern to strip Minecraft formatting codes (§ followed by any character)
    private val FORMAT_CODE_PATTERN = Regex("""§.""")

    // Archfiend Dice patterns - check High Class first (more specific pattern)
    private val HIGH_CLASS_DICE_PATTERN = Regex("""Your High Class Archfiend Dice rolled a \d!""")
    private val ARCHFIEND_DICE_PATTERN = Regex("""Your Archfiend Dice rolled a \d!""")

    // Garden visitor offer accepted pattern - captures rarity tier
    private val VISITOR_ACCEPT_PATTERN = Regex(
        """OFFER ACCEPTED with .+ \((UNCOMMON|RARE|LEGENDARY|MYTHIC|SPECIAL)\)"""
    )

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

        // Check for garden visitor offer acceptance
        val visitorMatch = VISITOR_ACCEPT_PATTERN.find(text)
        if (visitorMatch != null) {
            val rarityStr = visitorMatch.groupValues[1]
            try {
                val rarity = VisitorRarity.valueOf(rarityStr)
                RngDataStore.incrementVisitorAccept(rarity)
                DyeTrackerMod.debug("Detected visitor accept: {} rarity", rarity)
            } catch (e: IllegalArgumentException) {
                DyeTrackerMod.warn("Unknown visitor rarity: {}", rarityStr)
            }
            return
        }
    }
}
