package com.dyetracker.commands

import com.dyetracker.DyeTrackerMod
import com.dyetracker.auth.AccountVerification
import com.dyetracker.config.ConfigManager
import com.dyetracker.data.DungeonFloor
import com.dyetracker.data.RngDataStore
import com.dyetracker.data.SlayerType
import com.dyetracker.overlay.OverlayAddPipeline
import com.dyetracker.sync.SyncManager
import com.dyetracker.ui.edit.WidgetEditScreen
import com.dyetracker.ui.texture.ImageTextureManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Registers client-side commands for the DyeTracker mod.
 */
object DyeTrackerCommands {

    private const val GIF_URL_DISPLAY_MAX = 60
    private const val GIF_URL_TRUNCATE_AT = 57
    private val gifCommandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Register all commands via the ClientCommandRegistrationCallback.
     */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            registerCommands(dispatcher)
        }
    }

    private fun registerCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommandManager.literal("dyetracker")
                .then(
                    ClientCommandManager.literal("link")
                        .then(
                            ClientCommandManager.argument("code", StringArgumentType.word())
                                .executes { context ->
                                    val code = StringArgumentType.getString(context, "code")
                                    handleLinkCommand(context.source, code)
                                    1
                                }
                        )
                        .executes { context ->
                            context.source.sendFeedback(
                                Text.literal("Usage: /dyetracker link <code>")
                                    .formatted(Formatting.YELLOW)
                            )
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("status")
                        .executes { context ->
                            handleStatusCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("unlink")
                        .executes { context ->
                            handleUnlinkCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("show")
                        .executes { context ->
                            handleShowCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("sync")
                        .executes { context ->
                            handleSyncCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("reload")
                        .executes { context ->
                            handleReloadCommand(context.source)
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("gif")
                        .then(
                            ClientCommandManager.literal("add")
                                .then(
                                    ClientCommandManager.argument("url", StringArgumentType.greedyString())
                                        .executes { context ->
                                            val url = StringArgumentType.getString(context, "url")
                                            handleGifAddCommand(context.source, url)
                                            1
                                        }
                                )
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Text.literal("Usage: /dyetracker gif add <url>")
                                            .formatted(Formatting.YELLOW)
                                    )
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("list")
                                .executes { context ->
                                    handleGifListCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("remove")
                                .then(
                                    ClientCommandManager.argument("id", StringArgumentType.word())
                                        .executes { context ->
                                            val id = StringArgumentType.getString(context, "id")
                                            handleGifRemoveCommand(context.source, id)
                                            1
                                        }
                                )
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Text.literal("Usage: /dyetracker gif remove <id>")
                                            .formatted(Formatting.YELLOW)
                                    )
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("edit")
                                .executes { context ->
                                    handleGifEditCommand(context.source)
                                    1
                                }
                        )
                        .executes { context ->
                            context.source.sendFeedback(
                                Text.literal("Usage: /dyetracker gif <add|list|remove|edit>")
                                    .formatted(Formatting.YELLOW)
                            )
                            1
                        }
                )
                .executes { context ->
                    showHelp(context.source)
                    1
                }
        )
    }

    private fun handleGifAddCommand(source: FabricClientCommandSource, rawUrl: String) {
        if (rawUrl.trim().isEmpty()) {
            source.sendFeedback(
                Text.literal("Invalid URL.")
                    .formatted(Formatting.RED)
            )
            return
        }

        source.sendFeedback(
            Text.literal("Downloading…")
                .formatted(Formatting.YELLOW)
        )

        gifCommandScope.launch {
            // The pipeline handles download → decode → upload → persist (gifPlacements.add)
            // and surfaces a single human-readable failure string. We translate it to chat.
            val outcome = OverlayAddPipeline.addFromUrl(rawUrl)
            postOnClient {
                when (outcome) {
                    is OverlayAddPipeline.Outcome.Success -> source.sendFeedback(
                        Text.literal(
                            "Added overlay '${outcome.id}' (${outcome.decoded.frames.size} frames, " +
                                "${outcome.decoded.totalDurationMs}ms loop)"
                        ).formatted(Formatting.GREEN)
                    )
                    is OverlayAddPipeline.Outcome.Failure -> source.sendFeedback(
                        Text.literal(outcome.message).formatted(Formatting.RED)
                    )
                }
            }
        }
    }

    private fun handleGifListCommand(source: FabricClientCommandSource) {
        val gifs = ConfigManager.gifPlacements.all()
        if (gifs.isEmpty()) {
            source.sendFeedback(
                Text.literal("No overlays configured. Add one with /dyetracker gif add <url>")
                    .formatted(Formatting.YELLOW)
            )
            return
        }
        for (gif in gifs) {
            val truncatedUrl = if (gif.url.length > GIF_URL_DISPLAY_MAX) {
                gif.url.take(GIF_URL_TRUNCATE_AT) + "..."
            } else {
                gif.url
            }
            val visibility = if (gif.visible) "visible" else "hidden"
            source.sendFeedback(
                Text.literal(
                    "${gif.id}: $truncatedUrl @(${gif.x}, ${gif.y}) scale ${gif.scale} [$visibility]"
                ).formatted(Formatting.GRAY)
            )
        }
    }

    private fun handleGifRemoveCommand(source: FabricClientCommandSource, id: String) {
        if (!ConfigManager.gifPlacements.remove(id)) {
            source.sendFeedback(
                Text.literal("No overlay with id '$id'.")
                    .formatted(Formatting.RED)
            )
            return
        }
        ImageTextureManager.release(id)
        source.sendFeedback(
            Text.literal("Removed overlay '$id'.")
                .formatted(Formatting.GREEN)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleGifEditCommand(source: FabricClientCommandSource) {
        // Brigadier client commands already execute on the render thread, so no marshal
        // needed. The screen IS the feedback; no chat output here.
        MinecraftClient.getInstance().setScreen(WidgetEditScreen())
    }

    /** Marshal a callback onto the client thread so we never touch client APIs from IO. */
    private fun postOnClient(block: () -> Unit) {
        MinecraftClient.getInstance().execute(block)
    }

    private fun handleLinkCommand(source: FabricClientCommandSource, code: String) {
        // Validate code format
        if (code.length != 8 || !code.all { it.isLetterOrDigit() }) {
            source.sendFeedback(
                Text.literal("Invalid code format. Please enter the 8-character code from the website.")
                    .formatted(Formatting.RED)
            )
            return
        }

        // Check if already linked
        if (AccountVerification.isLinked()) {
            source.sendFeedback(
                Text.literal("Account already linked as ")
                    .formatted(Formatting.YELLOW)
                    .append(
                        Text.literal(ConfigManager.config.linkedUsername)
                            .formatted(Formatting.AQUA)
                    )
                    .append(
                        Text.literal(". Use /dyetracker unlink first.")
                            .formatted(Formatting.YELLOW)
                    )
            )
            return
        }

        source.sendFeedback(
            Text.literal("Verifying account...")
                .formatted(Formatting.YELLOW)
        )

        // Run verification async
        AccountVerification.verifyAccount(code)
            .thenAccept { result ->
                // Must run on main thread to send feedback
                MinecraftClient.getInstance().execute {
                    if (result.success) {
                        source.sendFeedback(
                            Text.literal("\u2714 ")
                                .formatted(Formatting.GREEN)
                                .append(
                                    Text.literal(result.message)
                                        .formatted(Formatting.GREEN)
                                )
                        )
                        source.sendFeedback(
                            Text.literal("Linked as: ")
                                .formatted(Formatting.GRAY)
                                .append(
                                    Text.literal(result.username ?: "")
                                        .formatted(Formatting.AQUA, Formatting.BOLD)
                                )
                        )
                    } else {
                        source.sendFeedback(
                            Text.literal("\u2718 ")
                                .formatted(Formatting.RED)
                                .append(
                                    Text.literal(result.message)
                                        .formatted(Formatting.RED)
                                )
                        )
                    }
                }
            }
    }

    private fun handleStatusCommand(source: FabricClientCommandSource) {
        if (AccountVerification.isLinked()) {
            source.sendFeedback(
                Text.literal("Account Status: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        Text.literal("Linked")
                            .formatted(Formatting.GREEN, Formatting.BOLD)
                    )
            )
            source.sendFeedback(
                Text.literal("Username: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        Text.literal(ConfigManager.config.linkedUsername)
                            .formatted(Formatting.AQUA)
                    )
            )
            source.sendFeedback(
                Text.literal("UUID: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        Text.literal(ConfigManager.config.linkedUuid)
                            .formatted(Formatting.DARK_GRAY)
                    )
            )
            // Show token status
            val hasToken = ConfigManager.config.authToken.isNotEmpty()
            source.sendFeedback(
                Text.literal("Auth Token: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        if (hasToken) {
                            Text.literal("Valid")
                                .formatted(Formatting.GREEN)
                        } else {
                            Text.literal("Missing")
                                .formatted(Formatting.RED)
                        }
                    )
            )

            // Show sync status
            val lastSync = SyncManager.getLastSyncTime()
            val lastSyncText = if (lastSync > 0) {
                SimpleDateFormat("HH:mm:ss").format(Date(lastSync))
            } else {
                "Never"
            }
            val syncPending = SyncManager.isSyncPending()
            val lastSyncSuccess = SyncManager.wasLastSyncSuccessful()

            source.sendFeedback(
                Text.literal("Last Sync: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        Text.literal(lastSyncText)
                            .formatted(if (lastSyncSuccess) Formatting.GREEN else Formatting.RED)
                    )
                    .append(
                        if (syncPending) {
                            Text.literal(" (sync pending)")
                                .formatted(Formatting.YELLOW)
                        } else {
                            Text.literal("")
                        }
                    )
            )
        } else {
            source.sendFeedback(
                Text.literal("Account Status: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        Text.literal("Not Linked")
                            .formatted(Formatting.RED)
                    )
            )
            source.sendFeedback(
                Text.literal("Use /dyetracker link <code> to link your account.")
                    .formatted(Formatting.YELLOW)
            )
        }
    }

    private fun handleSyncCommand(source: FabricClientCommandSource) {
        if (!AccountVerification.isLinked()) {
            source.sendFeedback(
                Text.literal("Account not linked. Use /dyetracker link <code> first.")
                    .formatted(Formatting.RED)
            )
            return
        }

        val data = RngDataStore.getData()
        if (!data.hasData()) {
            source.sendFeedback(
                Text.literal("No RNG data to sync.")
                    .formatted(Formatting.YELLOW)
            )
            return
        }

        source.sendFeedback(
            Text.literal("Syncing RNG data...")
                .formatted(Formatting.YELLOW)
        )

        SyncManager.syncNow(data)
            .thenAccept { result ->
                MinecraftClient.getInstance().execute {
                    if (result.success) {
                        source.sendFeedback(
                            Text.literal("\u2714 ")
                                .formatted(Formatting.GREEN)
                                .append(
                                    Text.literal("RNG data synced successfully!")
                                        .formatted(Formatting.GREEN)
                                )
                        )
                    } else {
                        source.sendFeedback(
                            Text.literal("\u2718 ")
                                .formatted(Formatting.RED)
                                .append(
                                    Text.literal("Sync failed: ${result.message}")
                                        .formatted(Formatting.RED)
                                )
                        )
                    }
                }
            }
    }

    private fun handleReloadCommand(source: FabricClientCommandSource) {
        try {
            ConfigManager.load()
            source.sendFeedback(
                Text.literal("\u2714 Configuration reloaded")
                    .formatted(Formatting.GREEN)
            )
            source.sendFeedback(
                Text.literal("API URL: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        Text.literal(ConfigManager.config.apiUrl)
                            .formatted(Formatting.AQUA)
                    )
            )
            val hasToken = ConfigManager.config.authToken.isNotEmpty()
            source.sendFeedback(
                Text.literal("Auth Token: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        if (hasToken) {
                            Text.literal("Present")
                                .formatted(Formatting.GREEN)
                        } else {
                            Text.literal("Not set")
                                .formatted(Formatting.YELLOW)
                        }
                    )
            )
        } catch (e: Exception) {
            source.sendFeedback(
                Text.literal("\u2718 Failed to reload config: ${e.message}")
                    .formatted(Formatting.RED)
            )
            DyeTrackerMod.LOGGER.error("Failed to reload config", e)
        }
    }

    private fun handleUnlinkCommand(source: FabricClientCommandSource) {
        if (!AccountVerification.isLinked()) {
            source.sendFeedback(
                Text.literal("No account is currently linked.")
                    .formatted(Formatting.YELLOW)
            )
            return
        }

        val username = ConfigManager.config.linkedUsername
        AccountVerification.unlink()

        source.sendFeedback(
            Text.literal("Account ")
                .formatted(Formatting.GRAY)
                .append(
                    Text.literal(username)
                        .formatted(Formatting.AQUA)
                )
                .append(
                    Text.literal(" has been unlinked.")
                        .formatted(Formatting.GRAY)
                )
        )
    }

    private fun handleShowCommand(source: FabricClientCommandSource) {
        val data = RngDataStore.getData()

        if (!data.hasData()) {
            source.sendFeedback(
                Text.literal("No RNG data captured yet.")
                    .formatted(Formatting.YELLOW)
            )
            source.sendFeedback(
                Text.literal("Open RNG meter menus in-game to capture data.")
                    .formatted(Formatting.GRAY)
            )
            return
        }

        source.sendFeedback(
            Text.literal("=== RNG Data ===")
                .formatted(Formatting.GOLD, Formatting.BOLD)
        )

        // Show slayer meters
        if (data.slayerMeters.isNotEmpty()) {
            source.sendFeedback(
                Text.literal("Slayer RNG Meters:")
                    .formatted(Formatting.AQUA)
            )
            for ((slayerType, meter) in data.slayerMeters) {
                val itemInfo = if (meter.selectedItem != null) {
                    " [${meter.selectedItem}: ${formatXp(meter.goalXp ?: 0)} goal]"
                } else ""
                source.sendFeedback(
                    Text.literal("  ${slayerType.name}: ")
                        .formatted(Formatting.GRAY)
                        .append(
                            Text.literal("${formatXp(meter.storedXp)} XP")
                                .formatted(Formatting.WHITE)
                        )
                        .append(
                            Text.literal(itemInfo)
                                .formatted(Formatting.DARK_GRAY)
                        )
                )
            }
        }

        // Show dungeon meters
        if (data.dungeonMeters.isNotEmpty()) {
            source.sendFeedback(
                Text.literal("Dungeon RNG Meters:")
                    .formatted(Formatting.LIGHT_PURPLE)
            )
            for ((floor, meter) in data.dungeonMeters) {
                val itemInfo = if (meter.selectedItem != null) {
                    " [${meter.selectedItem}: ${formatXp(meter.goalXp ?: 0)} goal]"
                } else ""
                source.sendFeedback(
                    Text.literal("  ${floor.name}: ")
                        .formatted(Formatting.GRAY)
                        .append(
                            Text.literal("${formatXp(meter.storedXp)} XP")
                                .formatted(Formatting.WHITE)
                        )
                        .append(
                            Text.literal(itemInfo)
                                .formatted(Formatting.DARK_GRAY)
                        )
                )
            }
        }

        // Show nucleus meter
        data.nucleusMeter?.let { meter ->
            val itemInfo = if (meter.selectedItem != null) {
                " [${meter.selectedItem}: ${formatXp(meter.goalXp ?: 0)} goal]"
            } else ""
            source.sendFeedback(
                Text.literal("Nucleus RNG: ")
                    .formatted(Formatting.GREEN)
                    .append(
                        Text.literal("${formatXp(meter.storedXp)} XP")
                            .formatted(Formatting.WHITE)
                    )
                    .append(
                        Text.literal(itemInfo)
                            .formatted(Formatting.DARK_GRAY)
                    )
            )
        }

        // Show experimentation meter
        data.experimentationMeter?.let { meter ->
            val itemInfo = if (meter.selectedItem != null) {
                " [${meter.selectedItem}: ${formatXp(meter.goalXp ?: 0)} goal]"
            } else ""
            source.sendFeedback(
                Text.literal("Experimentation RNG: ")
                    .formatted(Formatting.BLUE)
                    .append(
                        Text.literal("${formatXp(meter.storedXp)} XP")
                            .formatted(Formatting.WHITE)
                    )
                    .append(
                        Text.literal(itemInfo)
                            .formatted(Formatting.DARK_GRAY)
                    )
            )
        }

        // Show mineshaft pity
        data.mineshaftPity?.let { pity ->
            source.sendFeedback(
                Text.literal("Mineshaft Pity: ")
                    .formatted(Formatting.DARK_AQUA)
                    .append(
                        Text.literal("${pity.pityValue}/2,000")
                            .formatted(Formatting.WHITE)
                    )
            )
        }

        // Show archfiend dye rolls
        data.archfiendDye?.let { dye ->
            source.sendFeedback(
                Text.literal("Archfiend Dye:")
                    .formatted(Formatting.DARK_RED)
            )
            source.sendFeedback(
                Text.literal("  High Class Dice: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        Text.literal("${dye.highClassDiceRolls} rolls")
                            .formatted(Formatting.WHITE)
                    )
                    .append(
                        Text.literal(" (1/666)")
                            .formatted(Formatting.DARK_GRAY)
                    )
            )
            source.sendFeedback(
                Text.literal("  Archfiend Dice: ")
                    .formatted(Formatting.GRAY)
                    .append(
                        Text.literal("${dye.archfiendDiceRolls} rolls")
                            .formatted(Formatting.WHITE)
                    )
                    .append(
                        Text.literal(" (1/6,600)")
                            .formatted(Formatting.DARK_GRAY)
                    )
            )
        }

        // Show nyanza dye commissions
        data.nyanzaDye?.let { dye ->
            source.sendFeedback(
                Text.literal("Nyanza Dye: ")
                    .formatted(Formatting.GREEN)
                    .append(
                        Text.literal("${formatXp(dye.commissionsCompleted.toLong())} commissions")
                            .formatted(Formatting.WHITE)
                    )
                    .append(
                        Text.literal(" (1/250,000)")
                            .formatted(Formatting.DARK_GRAY)
                    )
            )
        }

        // Show copper dye visitor accepts
        data.copperDye?.let { dye ->
            source.sendFeedback(
                Text.literal("Copper Dye:")
                    .formatted(Formatting.GOLD)
            )
            // Display drop rates for each rarity tier
            val dropRates = mapOf(
                "UNCOMMON" to "1/100,000",
                "RARE" to "1/20,000",
                "LEGENDARY" to "1/4,000",
                "MYTHIC" to "1/800",
                "SPECIAL" to "1/500"
            )
            for ((rarity, count) in dye.visitorAccepts) {
                val dropRate = dropRates[rarity.name] ?: "???"
                source.sendFeedback(
                    Text.literal("  ${rarity.name}: ")
                        .formatted(Formatting.GRAY)
                        .append(
                            Text.literal("$count accepts")
                                .formatted(Formatting.WHITE)
                        )
                        .append(
                            Text.literal(" ($dropRate)")
                                .formatted(Formatting.DARK_GRAY)
                        )
                )
            }
        }
    }

    /**
     * Format an XP value with commas for readability.
     */
    private fun formatXp(xp: Long): String {
        return "%,d".format(xp)
    }

    private fun showHelp(source: FabricClientCommandSource) {
        source.sendFeedback(
            Text.literal("DyeTracker Commands:")
                .formatted(Formatting.GOLD, Formatting.BOLD)
        )
        source.sendFeedback(
            Text.literal("  /dyetracker link <code>")
                .formatted(Formatting.YELLOW)
                .append(
                    Text.literal(" - Link your account")
                        .formatted(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker status")
                .formatted(Formatting.YELLOW)
                .append(
                    Text.literal(" - Show link status")
                        .formatted(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker unlink")
                .formatted(Formatting.YELLOW)
                .append(
                    Text.literal(" - Unlink your account")
                        .formatted(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker show")
                .formatted(Formatting.YELLOW)
                .append(
                    Text.literal(" - Show captured RNG data")
                        .formatted(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker sync")
                .formatted(Formatting.YELLOW)
                .append(
                    Text.literal(" - Force sync RNG data to backend")
                        .formatted(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker reload")
                .formatted(Formatting.YELLOW)
                .append(
                    Text.literal(" - Reload config (debug)")
                        .formatted(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker gif <add|list|remove|edit>")
                .formatted(Formatting.YELLOW)
                .append(
                    Text.literal(" - Manage HUD image/GIF overlays")
                        .formatted(Formatting.GRAY)
                )
        )
    }
}
