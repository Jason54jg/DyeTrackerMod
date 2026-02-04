package com.dyetracker.events

import com.dyetracker.DyeTrackerMod
import com.dyetracker.data.RngDataStore
import com.dyetracker.data.SlayerType
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.text.Text

/**
 * Handles chat messages to capture RNG XP updates from slayer boss kills.
 */
object ChatEventHandler {

    // Pattern for RNG XP in chat: "RNG Meter - 1,234,567 Stored XP" or "RNG Meter - 1.5M Stored XP"
    private val RNG_XP_PATTERN = Regex("""RNG Meter\s*-\s*([\d,.]+[KMB]?)\s*Stored XP""")

    // Pattern to strip Minecraft formatting codes (§ followed by any character)
    private val FORMAT_CODE_PATTERN = Regex("""§.""")

    // Archfiend Dice patterns - check High Class first (more specific pattern)
    private val HIGH_CLASS_DICE_PATTERN = Regex("""Your High Class Archfiend Dice rolled a \d!""")
    private val ARCHFIEND_DICE_PATTERN = Regex("""Your Archfiend Dice rolled a \d!""")

    // Slayer quest patterns - detect by mob type in the "Slay X Combat XP worth of..." message
    // Chat format is: "» Slay 2,400 Combat XP worth of Zombies."
    private val SLAYER_MOB_PATTERNS = mapOf(
        SlayerType.REVENANT to Regex("""Combat XP worth of.*Zombies""", RegexOption.IGNORE_CASE),
        SlayerType.TARANTULA to Regex("""Combat XP worth of.*Spiders""", RegexOption.IGNORE_CASE),
        SlayerType.SVEN to Regex("""Combat XP worth of.*Wolves""", RegexOption.IGNORE_CASE),
        SlayerType.VOIDGLOOM to Regex("""Combat XP worth of.*Endermen""", RegexOption.IGNORE_CASE),
        SlayerType.INFERNO to Regex("""Combat XP worth of.*Blazes""", RegexOption.IGNORE_CASE),
        SlayerType.RIFTSTALKER to Regex("""Combat XP worth of.*Vampires""", RegexOption.IGNORE_CASE)
    )

    // Track the current active slayer type
    @Volatile
    private var currentSlayerType: SlayerType? = null

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

        // Check for slayer quest start to track current slayer
        for ((slayerType, pattern) in SLAYER_MOB_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                currentSlayerType = slayerType
                DyeTrackerMod.debug("Detected slayer quest: {}", slayerType)
                return
            }
        }

        // Check for RNG XP message
        val matchResult = RNG_XP_PATTERN.find(text)
        if (matchResult != null) {
            val xpString = matchResult.groupValues[1]
            val xpValue = parseXpValue(xpString)

            if (xpValue != null) {
                val slayer = currentSlayerType
                if (slayer != null) {
                    RngDataStore.updateSlayerXp(slayer, xpValue)
                    DyeTrackerMod.debug("Updated {} RNG XP: {}", slayer, xpValue)
                } else {
                    DyeTrackerMod.debug("RNG XP detected but no active slayer type: {}", xpValue)
                }
            }
        }
    }

    /**
     * Parse an XP value string that may contain commas and K/M/B suffixes.
     * Examples: "1,234,567" -> 1234567, "1.5K" -> 1500, "75M" -> 75000000
     */
    fun parseXpValue(text: String): Long? {
        val cleanText = text.replace(",", "").trim()

        return when {
            cleanText.endsWith("K", ignoreCase = true) -> {
                val numPart = cleanText.dropLast(1).toDoubleOrNull() ?: return null
                (numPart * 1_000).toLong()
            }
            cleanText.endsWith("M", ignoreCase = true) -> {
                val numPart = cleanText.dropLast(1).toDoubleOrNull() ?: return null
                (numPart * 1_000_000).toLong()
            }
            cleanText.endsWith("B", ignoreCase = true) -> {
                val numPart = cleanText.dropLast(1).toDoubleOrNull() ?: return null
                (numPart * 1_000_000_000).toLong()
            }
            else -> cleanText.toLongOrNull()
        }
    }

    /**
     * Get the currently tracked slayer type (for testing/debugging).
     */
    fun getCurrentSlayerType(): SlayerType? = currentSlayerType

    /**
     * Reset the tracked slayer type (for testing).
     */
    fun resetSlayerType() {
        currentSlayerType = null
    }
}
