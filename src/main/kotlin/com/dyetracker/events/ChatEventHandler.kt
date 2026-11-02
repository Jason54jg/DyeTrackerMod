package com.dyetracker.events

import com.dyetracker.DyeTrackerMod
import com.dyetracker.data.RngDataStore
import com.dyetracker.data.TreasureQuality
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.text.Text

/**
 * One gift reward chat line (PBI 66): the [rarity] word and the raw [reward] text, as parsed by
 * [ChatEventHandler.parseGiftReward]. The line does not say the gift's color, so both stay raw.
 */
data class GiftReward(val rarity: String, val reward: String)

/**
 * Handles chat messages to capture RNG-relevant events (Archfiend Dice rolls, fishing-treasure
 * catches).
 *
 * PBI 47: slayer RNG-meter chat capture was removed. Slayer dyes are now estimated
 * from per-tier boss kill counts on the backend, so the mod no longer tracks the
 * active slayer or parses "RNG Meter … Stored XP" lines.
 *
 * PBI 42: the Garden visitor "OFFER ACCEPTED" chat capture was removed. Copper Dye
 * progress is now sourced from a full snapshot of every visitor seen, read from the
 * in-game Visitor's Logbook GUI (see InventoryHandler), not from accepted-offer chat.
 *
 * PBI 58: fishing-treasure catches are captured from the catch-quality chat line
 * (`⛃ GOOD/GREAT/OUTSTANDING CATCH!`) to track Treasure Dye per tier — see
 * [parseTreasureQuality].
 *
 * PBI 66: Fossil excavations (the `EXCAVATION COMPLETE` reward block, one per Suspicious Scrap)
 * and Dark Auctions attended (`Transferring to Dark Auction...`) are counted for Fossil Dye and
 * Dark Purple Dye — see [isExcavationComplete] and [isDarkAuctionTransfer]. Both match whole lines
 * (after [chatLines] trims them and strips a chat-mod stack suffix), so player chat that quotes the
 * line is never counted.
 *
 * PBI 66: gift reward lines (`<RARITY>! <reward> gift with <player>!`, one per gift given or
 * opened) are counted raw by rarity and reward for Holly Dye — see [parseGiftReward]. The backend
 * estimates how many of them were Red Gifts.
 */
object ChatEventHandler {

    // Pattern to strip Minecraft formatting codes (§ followed by any character)
    private val FORMAT_CODE_PATTERN = Regex("""§.""")

    // Archfiend Dice patterns - check High Class first (more specific pattern)
    private val HIGH_CLASS_DICE_PATTERN = Regex("""Your High Class Archfiend Dice rolled a \d!""")
    private val ARCHFIEND_DICE_PATTERN = Regex("""Your Archfiend Dice rolled a \d!""")

    // PBI 58: fishing-treasure catch-quality lines. The server prints `⛃ <TIER> CATCH!` for every
    // catch, where <TIER> is GOOD/GREAT/OUTSTANDING; junk-pool variants insert a filler word
    // (`⛃ GREAT JUNK CATCH!`). We strip the leading ⛃ icon + whitespace, then match the tier
    // ANCHORED at the start of the line. Anchoring doubles as the player-chat guard: a teammate
    // announcing "GREAT JUNK CATCH" in party/all chat arrives as "<name>: ..." (or "[rank] name:
    // ..."), which never starts with the tier word, so it is rejected without enumerating
    // Hypixel's channel/rank prefixes. The optional `(?: [A-Z]+)*` filler counts ANY loot pool
    // (treasure or junk) at the rolled tier and tolerates future pool words (per 58-1).
    private val LEADING_TREASURE_ICON_PATTERN = Regex("""^[\s⛃]+""")
    private val GOOD_CATCH_PATTERN = Regex("""^GOOD(?: [A-Z]+)* CATCH""")
    private val GREAT_CATCH_PATTERN = Regex("""^GREAT(?: [A-Z]+)* CATCH""")
    private val OUTSTANDING_CATCH_PATTERN = Regex("""^OUTSTANDING(?: [A-Z]+)* CATCH""")

    // PBI 66: some chat mods collapse repeated lines into one and append a count (` (3)`); it is
    // stripped from each line before matching so a stacked repeat still reads as the server line.
    private val STACK_SUFFIX_PATTERN = Regex("""\s*\(\d+\)$""")

    // PBI 66: the header line of the Fossil excavation reward block (one per Suspicious Scrap).
    private const val EXCAVATION_COMPLETE_LINE = "EXCAVATION COMPLETE"

    // PBI 66: the line printed when warping into a Dark Auction (one per auction attended). The
    // trailing ellipsis may arrive as three dots or the single `…` character.
    private val DARK_AUCTION_TRANSFER_PATTERN = Regex("""^Transferring to Dark Auction(?:\.\.\.|…)?$""")

    // PBI 66: the gift reward line, `<RARITY>! <reward> gift with <player>!`, printed for every gift
    // given or opened. Group 1 is the rarity word, group 2 the reward (lazy, so a reward that itself
    // contains "gift" stays whole). Anchoring at the start is the player-chat guard: a chat line
    // (`[MVP+] Steve: SWEET! ...`, `Party > ...`) never starts with the rarity word.
    private val GIFT_LINE_PATTERN = Regex("""^([A-Z]{1,15})! (.+?) gift with (.+)!$""")

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

        // PBI 58: fishing-treasure catch — increment the per-tier Treasure Dye counter.
        val treasureQuality = parseTreasureQuality(text)
        if (treasureQuality != null) {
            RngDataStore.incrementTreasureCatch(treasureQuality)
            DyeTrackerMod.debug("Detected $treasureQuality treasure catch")
            return
        }

        // PBI 66: Fossil excavation — increment the Fossil Dye excavation counter.
        if (isExcavationComplete(text)) {
            RngDataStore.incrementExcavation()
            DyeTrackerMod.debug("Detected Fossil excavation")
            return
        }

        // PBI 66: Dark Auction transfer — increment the Dark Purple Dye auction counter.
        if (isDarkAuctionTransfer(text)) {
            RngDataStore.incrementDarkAuction()
            DyeTrackerMod.debug("Detected Dark Auction transfer")
            return
        }

        // PBI 66: gift reward — count it raw under its rarity and reward for Holly Dye.
        parseGiftReward(text)?.let {
            RngDataStore.incrementGiftReward(it.rarity, it.reward)
            DyeTrackerMod.debug("Detected ${it.rarity} gift reward: ${it.reward}")
            return
        }
    }

    /**
     * Split a format-code-stripped chat message into its visible lines (PBI 66).
     *
     * Pure (no Minecraft types) so it is unit-testable. Splits on `\n`, trims each line, strips a
     * chat-mod stack suffix (` (3)`, see [STACK_SUFFIX_PATTERN]) and drops empty lines. A multi-line
     * server block (the Fossil `EXCAVATION COMPLETE` / `REWARDS` block) arrives as one message, so
     * the line-wise parsers below look at each of its lines.
     */
    fun chatLines(text: String): List<String> =
        text.split("\n")
            .map { STACK_SUFFIX_PATTERN.replace(it.trim(), "") }
            .filter { it.isNotEmpty() }

    /**
     * True when a format-code-stripped chat message contains the Fossil excavation header line
     * (PBI 66) — one per Suspicious Scrap excavated.
     *
     * A line must EQUAL [EXCAVATION_COMPLETE_LINE]; exact matching is the player-chat guard (a
     * teammate's `Steve: EXCAVATION COMPLETE` never equals the bare line).
     */
    fun isExcavationComplete(text: String): Boolean =
        chatLines(text).any { it == EXCAVATION_COMPLETE_LINE }

    /**
     * True when a format-code-stripped chat message contains the Dark Auction transfer line
     * (PBI 66) — one per Dark Auction attended.
     *
     * Each line is matched whole against [DARK_AUCTION_TRANSFER_PATTERN] (anchored), which is the
     * player-chat guard, the same as [isExcavationComplete].
     */
    fun isDarkAuctionTransfer(text: String): Boolean =
        chatLines(text).any { DARK_AUCTION_TRANSFER_PATTERN.matches(it) }

    /**
     * Parse a format-code-stripped chat message into the gift reward it announces (PBI 66), or null
     * when no line of it is a gift reward line.
     *
     * Pure (no Minecraft types) so it is unit-testable. The first of [chatLines] (which already
     * strips a chat-mod stack suffix) that matches [GIFT_LINE_PATTERN] gives the rarity and the
     * trimmed reward text. Player chat that quotes the line is rejected by the anchored pattern.
     */
    fun parseGiftReward(text: String): GiftReward? =
        chatLines(text).firstNotNullOfOrNull { line ->
            GIFT_LINE_PATTERN.matchEntire(line)?.let { match ->
                GiftReward(match.groupValues[1], match.groupValues[2].trim())
            }
        }

    /**
     * Map a format-code-stripped chat line to the fishing-treasure quality tier it announces, or
     * null when the line is not a (server) catch-quality message.
     *
     * Pure (no Minecraft types) so it is unit-testable. The leading `⛃` icon + whitespace are
     * stripped, then each tier pattern is matched ANCHORED at the start of the line — so a
     * teammate's party/all-chat announcement (`<name>: GREAT JUNK CATCH`) is rejected (it does not
     * start with the tier word), while both treasure and junk loot-pool catches are counted at the
     * rolled tier. The three tier words are mutually exclusive first tokens, so the match order is
     * irrelevant (Outstanding → Great → Good for readability).
     */
    fun parseTreasureQuality(text: String): TreasureQuality? {
        val line = LEADING_TREASURE_ICON_PATTERN.replace(text, "")
        return when {
            OUTSTANDING_CATCH_PATTERN.containsMatchIn(line) -> TreasureQuality.OUTSTANDING
            GREAT_CATCH_PATTERN.containsMatchIn(line) -> TreasureQuality.GREAT
            GOOD_CATCH_PATTERN.containsMatchIn(line) -> TreasureQuality.GOOD
            else -> null
        }
    }
}
