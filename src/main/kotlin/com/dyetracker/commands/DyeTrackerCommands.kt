package com.dyetracker.commands

import com.dyetracker.DyeTrackerMod
import com.dyetracker.auth.AccountVerification
import com.dyetracker.config.ConfigManager
import com.dyetracker.data.DungeonFloor
import com.dyetracker.data.RngDataStore
import com.dyetracker.dyeprogress.DyeProgressAdder
import com.dyetracker.dyeprogress.DyeProgressPlacementEditor
import com.dyetracker.overlay.OverlayAddPipeline
import com.dyetracker.rotation.DyeSprites
import com.dyetracker.rotation.RotationPlacementEditor
import com.dyetracker.rotation.RotationWidgetConfig
import com.dyetracker.sync.SyncManager
import com.dyetracker.ui.bounce.BounceController
import com.dyetracker.ui.edit.WidgetEditScreen
import com.dyetracker.ui.texture.ImageTextureManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommands as ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft as MinecraftClient
import net.minecraft.commands.CommandBuildContext as CommandRegistryAccess
import net.minecraft.network.chat.Component as Text
import net.minecraft.network.chat.MutableComponent as MutableText
import net.minecraft.ChatFormatting as Formatting
*///?} else {
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
//?}
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Registers client-side commands for the DyeTracker mod.
 */
object DyeTrackerCommands {

    // Yarn's Text#formatted(Formatting...) was renamed to withStyle(ChatFormatting...) on the
    // official mappings 26.x uses; this shim keeps every call site below unchanged.
    //? if >=26.1 {
    /*private fun MutableText.styled(vararg colors: Formatting): MutableText = withStyle(*colors)
    *///?} else {
    private fun MutableText.styled(vararg colors: Formatting): MutableText = formatted(*colors)
    //?}

    private const val GIF_URL_DISPLAY_MAX = 60
    private const val GIF_URL_TRUNCATE_AT = 57
    private val gifCommandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** IO scope for the async dye-widget add command (profile resolution). */
    private val dyeCommandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                                    .styled(Formatting.YELLOW)
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
                                            .styled(Formatting.YELLOW)
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
                                            .styled(Formatting.YELLOW)
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
                                    .styled(Formatting.YELLOW)
                            )
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("rotation")
                        .then(
                            ClientCommandManager.literal("toggle")
                                .executes { context ->
                                    handleRotationToggleCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("edit")
                                .executes { context ->
                                    handleRotationEditCommand(context.source)
                                    1
                                }
                        )
                        .executes { context ->
                            context.source.sendFeedback(
                                Text.literal("Usage: /dyetracker rotation <toggle|edit>")
                                    .styled(Formatting.YELLOW)
                            )
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("dye")
                        .then(
                            ClientCommandManager.literal("list")
                                .executes { context ->
                                    handleDyeListCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("remove")
                                .then(
                                    ClientCommandManager.argument("id", StringArgumentType.word())
                                        .executes { context ->
                                            handleDyeRemoveCommand(context.source, StringArgumentType.getString(context, "id"))
                                            1
                                        }
                                )
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Text.literal("Usage: /dyetracker dye remove <id>")
                                            .styled(Formatting.YELLOW)
                                    )
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("toggle")
                                .then(
                                    ClientCommandManager.argument("id", StringArgumentType.word())
                                        .executes { context ->
                                            handleDyeToggleCommand(context.source, StringArgumentType.getString(context, "id"))
                                            1
                                        }
                                )
                                .executes { context ->
                                    // No id → toggle every dye widget at once.
                                    handleDyeToggleCommand(context.source, null)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("edit")
                                .executes { context ->
                                    handleDyeEditCommand(context.source)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("add")
                                .then(
                                    ClientCommandManager.argument("dyeId", StringArgumentType.word())
                                        .then(
                                            ClientCommandManager.argument("profile", StringArgumentType.greedyString())
                                                .executes { context ->
                                                    handleDyeAddCommand(
                                                        context.source,
                                                        StringArgumentType.getString(context, "dyeId"),
                                                        StringArgumentType.getString(context, "profile"),
                                                    )
                                                    1
                                                }
                                        )
                                        .executes { context ->
                                            context.source.sendFeedback(
                                                Text.literal("Usage: /dyetracker dye add <dyeId> <profile>")
                                                    .styled(Formatting.YELLOW)
                                            )
                                            1
                                        }
                                )
                                .executes { context ->
                                    context.source.sendFeedback(
                                        Text.literal("Usage: /dyetracker dye add <dyeId> <profile>")
                                            .styled(Formatting.YELLOW)
                                    )
                                    1
                                }
                        )
                        .executes { context ->
                            context.source.sendFeedback(
                                Text.literal("Usage: /dyetracker dye <list|remove|edit|toggle|add>")
                                    .styled(Formatting.YELLOW)
                            )
                            1
                        }
                )
                .then(
                    ClientCommandManager.literal("bounce")
                        .then(
                            ClientCommandManager.literal("on")
                                .executes { context ->
                                    handleBounceCommand(context.source, true)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("off")
                                .executes { context ->
                                    handleBounceCommand(context.source, false)
                                    1
                                }
                        )
                        .then(
                            ClientCommandManager.literal("toggle")
                                .executes { context ->
                                    handleBounceCommand(context.source, null)
                                    1
                                }
                        )
                        // Bare `/dyetracker bounce` toggles.
                        .executes { context ->
                            handleBounceCommand(context.source, null)
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
                    .styled(Formatting.RED)
            )
            return
        }

        source.sendFeedback(
            Text.literal("Downloading…")
                .styled(Formatting.YELLOW)
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
                        ).styled(Formatting.GREEN)
                    )
                    is OverlayAddPipeline.Outcome.Failure -> source.sendFeedback(
                        Text.literal(outcome.message).styled(Formatting.RED)
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
                    .styled(Formatting.YELLOW)
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
                ).styled(Formatting.GRAY)
            )
        }
    }

    private fun handleGifRemoveCommand(source: FabricClientCommandSource, id: String) {
        if (!ConfigManager.gifPlacements.remove(id)) {
            source.sendFeedback(
                Text.literal("No overlay with id '$id'.")
                    .styled(Formatting.RED)
            )
            return
        }
        ImageTextureManager.release(id)
        source.sendFeedback(
            Text.literal("Removed overlay '$id'.")
                .styled(Formatting.GREEN)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleGifEditCommand(source: FabricClientCommandSource) {
        // Brigadier client commands already execute on the render thread, so no marshal
        // needed. The screen IS the feedback; no chat output here.
        //? if >=26.2 {
        /*MinecraftClient.getInstance().gui.setScreen(WidgetEditScreen())
        *///?} else {
        MinecraftClient.getInstance().setScreen(WidgetEditScreen())
        //?}
    }

    private fun handleRotationToggleCommand(source: FabricClientCommandSource) {
        val current = ConfigManager.rotationPlacements.all().firstOrNull()?.visible
            ?: RotationWidgetConfig().visible
        val newVisible = !current
        RotationPlacementEditor.setVisible(RotationWidgetConfig.WIDGET_ID, newVisible)
        source.sendFeedback(
            Text.literal("Dye rotation widget ${if (newVisible) "shown" else "hidden"}.")
                .styled(if (newVisible) Formatting.GREEN else Formatting.YELLOW)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleRotationEditCommand(source: FabricClientCommandSource) {
        // Opens the same shared edit screen as `gif edit`; the rotation widget appears there
        // because it registered a PlacementEditor. The screen is the feedback.
        //? if >=26.2 {
        /*MinecraftClient.getInstance().gui.setScreen(WidgetEditScreen())
        *///?} else {
        MinecraftClient.getInstance().setScreen(WidgetEditScreen())
        //?}
    }

    /**
     * Enable/disable/toggle DVD-bounce mode (PBI 38). [target] is the explicit state for `on`/`off`,
     * or null for the bare/`toggle` form which flips the current live state. Persists the new flag and
     * applies it to the live [BounceController]; drift positions are never persisted.
     */
    private fun handleBounceCommand(source: FabricClientCommandSource, target: Boolean?) {
        val newState = target ?: !BounceController.isEnabled()
        BounceController.setEnabled(newState)
        ConfigManager.setBounceEnabled(newState)
        source.sendFeedback(
            Text.literal("Bounce mode: ${if (newState) "ON" else "OFF"}")
                .styled(if (newState) Formatting.GREEN else Formatting.YELLOW)
        )
    }

    private fun handleDyeListCommand(source: FabricClientCommandSource) {
        val widgets = ConfigManager.dyeProgressPlacements.all()
        if (widgets.isEmpty()) {
            source.sendFeedback(
                Text.literal("No dye widgets configured. Add one in /dyetracker dye edit (+ Add dye) or /dyetracker dye add <dyeId> <profile>")
                    .styled(Formatting.YELLOW)
            )
            return
        }
        for (widget in widgets) {
            val visibility = if (widget.visible) "visible" else "hidden"
            source.sendFeedback(
                Text.literal(
                    "${widget.id}: ${widget.dyeId} ${widget.profileName} " +
                        "@(${widget.x}, ${widget.y}) scale ${widget.scale} [$visibility]"
                ).styled(Formatting.GRAY)
            )
        }
    }

    private fun handleDyeRemoveCommand(source: FabricClientCommandSource, id: String) {
        if (!ConfigManager.dyeProgressPlacements.remove(id)) {
            source.sendFeedback(
                Text.literal("No dye widget with id '$id'.")
                    .styled(Formatting.RED)
            )
            return
        }
        source.sendFeedback(
            Text.literal("Removed dye widget '$id'.")
                .styled(Formatting.GREEN)
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleDyeEditCommand(source: FabricClientCommandSource) {
        // Opens the same shared edit screen as `gif edit`; dye widgets appear there because they
        // registered a PlacementEditor, and the "+ Add dye" panel is available. Screen = feedback.
        //? if >=26.2 {
        /*MinecraftClient.getInstance().gui.setScreen(WidgetEditScreen())
        *///?} else {
        MinecraftClient.getInstance().setScreen(WidgetEditScreen())
        //?}
    }

    /** Toggle one widget's visibility, or all of them when [id] is null. */
    private fun handleDyeToggleCommand(source: FabricClientCommandSource, id: String?) {
        val widgets = ConfigManager.dyeProgressPlacements.all()
        if (widgets.isEmpty()) {
            source.sendFeedback(
                Text.literal("No dye widgets configured.")
                    .styled(Formatting.YELLOW)
            )
            return
        }
        if (id == null) {
            for (widget in widgets) {
                DyeProgressPlacementEditor.setVisible(widget.id, !widget.visible)
            }
            source.sendFeedback(
                Text.literal("Toggled ${widgets.size} dye widget(s).")
                    .styled(Formatting.GREEN)
            )
            return
        }
        val widget = widgets.firstOrNull { it.id == id }
        if (widget == null) {
            source.sendFeedback(
                Text.literal("No dye widget with id '$id'.")
                    .styled(Formatting.RED)
            )
            return
        }
        val newVisible = !widget.visible
        DyeProgressPlacementEditor.setVisible(id, newVisible)
        source.sendFeedback(
            Text.literal("Dye widget '$id' ${if (newVisible) "shown" else "hidden"}.")
                .styled(if (newVisible) Formatting.GREEN else Formatting.YELLOW)
        )
    }

    private fun handleDyeAddCommand(source: FabricClientCommandSource, dyeId: String, profile: String) {
        if (!ConfigManager.config.isLinked()) {
            source.sendFeedback(
                Text.literal("Account not linked. Use /dyetracker link <code> first.")
                    .styled(Formatting.RED)
            )
            return
        }
        if (!DyeSprites.has(dyeId)) {
            source.sendFeedback(
                Text.literal("Unknown dye '$dyeId'. Use /dyetracker dye edit to pick from the list.")
                    .styled(Formatting.RED)
            )
            return
        }
        source.sendFeedback(
            Text.literal("Resolving profile…")
                .styled(Formatting.YELLOW)
        )
        val username = ConfigManager.config.linkedUsername
        dyeCommandScope.launch {
            // resolveAndAdd performs the blocking profiles fetch + add + immediate poll off-thread.
            val result = DyeProgressAdder.resolveAndAdd(username, dyeId, profile)
            postOnClient {
                when (result) {
                    is DyeProgressAdder.Result.Added -> source.sendFeedback(
                        Text.literal("Added dye widget '${result.id}' ($dyeId @ ${profile.trim()}).")
                            .styled(Formatting.GREEN)
                    )
                    DyeProgressAdder.Result.NotLinked -> source.sendFeedback(
                        Text.literal("Account not linked.").styled(Formatting.RED)
                    )
                    DyeProgressAdder.Result.InvalidDye -> source.sendFeedback(
                        Text.literal("Unknown dye '$dyeId'.").styled(Formatting.RED)
                    )
                    DyeProgressAdder.Result.EmptyProfile -> source.sendFeedback(
                        Text.literal("Enter a profile name.").styled(Formatting.RED)
                    )
                    is DyeProgressAdder.Result.UnknownProfile -> source.sendFeedback(
                        Text.literal("No profile '${result.typed}'. Available: ${result.available.joinToString()}")
                            .styled(Formatting.RED)
                    )
                    is DyeProgressAdder.Result.NetworkError -> source.sendFeedback(
                        Text.literal("Couldn't load profiles: ${result.message}").styled(Formatting.RED)
                    )
                }
            }
        }
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
                    .styled(Formatting.RED)
            )
            return
        }

        // Check if already linked
        if (AccountVerification.isLinked()) {
            source.sendFeedback(
                Text.literal("Account already linked as ")
                    .styled(Formatting.YELLOW)
                    .append(
                        Text.literal(ConfigManager.config.linkedUsername)
                            .styled(Formatting.AQUA)
                    )
                    .append(
                        Text.literal(". Use /dyetracker unlink first.")
                            .styled(Formatting.YELLOW)
                    )
            )
            return
        }

        source.sendFeedback(
            Text.literal("Verifying account...")
                .styled(Formatting.YELLOW)
        )

        // Run verification async
        AccountVerification.verifyAccount(code)
            .thenAccept { result ->
                // Must run on main thread to send feedback
                MinecraftClient.getInstance().execute {
                    if (result.success) {
                        source.sendFeedback(
                            Text.literal("\u2714 ")
                                .styled(Formatting.GREEN)
                                .append(
                                    Text.literal(result.message)
                                        .styled(Formatting.GREEN)
                                )
                        )
                        source.sendFeedback(
                            Text.literal("Linked as: ")
                                .styled(Formatting.GRAY)
                                .append(
                                    Text.literal(result.username ?: "")
                                        .styled(Formatting.AQUA, Formatting.BOLD)
                                )
                        )
                    } else {
                        source.sendFeedback(
                            Text.literal("\u2718 ")
                                .styled(Formatting.RED)
                                .append(
                                    Text.literal(result.message)
                                        .styled(Formatting.RED)
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
                    .styled(Formatting.GRAY)
                    .append(
                        Text.literal("Linked")
                            .styled(Formatting.GREEN, Formatting.BOLD)
                    )
            )
            source.sendFeedback(
                Text.literal("Username: ")
                    .styled(Formatting.GRAY)
                    .append(
                        Text.literal(ConfigManager.config.linkedUsername)
                            .styled(Formatting.AQUA)
                    )
            )
            source.sendFeedback(
                Text.literal("UUID: ")
                    .styled(Formatting.GRAY)
                    .append(
                        Text.literal(ConfigManager.config.linkedUuid)
                            .styled(Formatting.DARK_GRAY)
                    )
            )
            // Show token status
            val hasToken = ConfigManager.config.authToken.isNotEmpty()
            source.sendFeedback(
                Text.literal("Auth Token: ")
                    .styled(Formatting.GRAY)
                    .append(
                        if (hasToken) {
                            Text.literal("Valid")
                                .styled(Formatting.GREEN)
                        } else {
                            Text.literal("Missing")
                                .styled(Formatting.RED)
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
                    .styled(Formatting.GRAY)
                    .append(
                        Text.literal(lastSyncText)
                            .styled(if (lastSyncSuccess) Formatting.GREEN else Formatting.RED)
                    )
                    .append(
                        if (syncPending) {
                            Text.literal(" (sync pending)")
                                .styled(Formatting.YELLOW)
                        } else {
                            Text.literal("")
                        }
                    )
            )
        } else {
            source.sendFeedback(
                Text.literal("Account Status: ")
                    .styled(Formatting.GRAY)
                    .append(
                        Text.literal("Not Linked")
                            .styled(Formatting.RED)
                    )
            )
            source.sendFeedback(
                Text.literal("Use /dyetracker link <code> to link your account.")
                    .styled(Formatting.YELLOW)
            )
        }
    }

    private fun handleSyncCommand(source: FabricClientCommandSource) {
        if (!AccountVerification.isLinked()) {
            source.sendFeedback(
                Text.literal("Account not linked. Use /dyetracker link <code> first.")
                    .styled(Formatting.RED)
            )
            return
        }

        val data = RngDataStore.getData()
        if (!data.hasData()) {
            source.sendFeedback(
                Text.literal("No RNG data to sync.")
                    .styled(Formatting.YELLOW)
            )
            return
        }

        source.sendFeedback(
            Text.literal("Syncing RNG data...")
                .styled(Formatting.YELLOW)
        )

        SyncManager.syncNow(data)
            .thenAccept { result ->
                MinecraftClient.getInstance().execute {
                    if (result.success) {
                        source.sendFeedback(
                            Text.literal("\u2714 ")
                                .styled(Formatting.GREEN)
                                .append(
                                    Text.literal("RNG data synced successfully!")
                                        .styled(Formatting.GREEN)
                                )
                        )
                    } else {
                        source.sendFeedback(
                            Text.literal("\u2718 ")
                                .styled(Formatting.RED)
                                .append(
                                    Text.literal("Sync failed: ${result.message}")
                                        .styled(Formatting.RED)
                                )
                        )
                    }
                }
            }
    }

    private fun handleReloadCommand(source: FabricClientCommandSource) {
        try {
            ConfigManager.load()
            // Re-apply the persisted bounce flag to the live controller (PBI 38).
            BounceController.setEnabled(ConfigManager.isBounceEnabled())
            source.sendFeedback(
                Text.literal("\u2714 Configuration reloaded")
                    .styled(Formatting.GREEN)
            )
            source.sendFeedback(
                Text.literal("API URL: ")
                    .styled(Formatting.GRAY)
                    .append(
                        Text.literal(ConfigManager.config.apiUrl)
                            .styled(Formatting.AQUA)
                    )
            )
            val hasToken = ConfigManager.config.authToken.isNotEmpty()
            source.sendFeedback(
                Text.literal("Auth Token: ")
                    .styled(Formatting.GRAY)
                    .append(
                        if (hasToken) {
                            Text.literal("Present")
                                .styled(Formatting.GREEN)
                        } else {
                            Text.literal("Not set")
                                .styled(Formatting.YELLOW)
                        }
                    )
            )
            source.sendFeedback(
                Text.literal("Bounce mode: ")
                    .styled(Formatting.GRAY)
                    .append(
                        if (ConfigManager.isBounceEnabled()) {
                            Text.literal("ON").styled(Formatting.GREEN)
                        } else {
                            Text.literal("OFF").styled(Formatting.YELLOW)
                        }
                    )
            )
        } catch (e: Exception) {
            source.sendFeedback(
                Text.literal("\u2718 Failed to reload config: ${e.message}")
                    .styled(Formatting.RED)
            )
            DyeTrackerMod.LOGGER.error("Failed to reload config", e)
        }
    }

    private fun handleUnlinkCommand(source: FabricClientCommandSource) {
        if (!AccountVerification.isLinked()) {
            source.sendFeedback(
                Text.literal("No account is currently linked.")
                    .styled(Formatting.YELLOW)
            )
            return
        }

        val username = ConfigManager.config.linkedUsername
        AccountVerification.unlink()

        source.sendFeedback(
            Text.literal("Account ")
                .styled(Formatting.GRAY)
                .append(
                    Text.literal(username)
                        .styled(Formatting.AQUA)
                )
                .append(
                    Text.literal(" has been unlinked.")
                        .styled(Formatting.GRAY)
                )
        )
    }

    private fun handleShowCommand(source: FabricClientCommandSource) {
        val data = RngDataStore.getData()

        if (!data.hasData()) {
            source.sendFeedback(
                Text.literal("No RNG data captured yet.")
                    .styled(Formatting.YELLOW)
            )
            source.sendFeedback(
                Text.literal("Open RNG meter menus in-game to capture data.")
                    .styled(Formatting.GRAY)
            )
            return
        }

        source.sendFeedback(
            Text.literal("=== RNG Data ===")
                .styled(Formatting.GOLD, Formatting.BOLD)
        )

        // Show dungeon meters
        if (data.dungeonMeters.isNotEmpty()) {
            source.sendFeedback(
                Text.literal("Dungeon RNG Meters:")
                    .styled(Formatting.LIGHT_PURPLE)
            )
            for ((floor, meter) in data.dungeonMeters) {
                val itemInfo = if (meter.selectedItem != null) {
                    " [${meter.selectedItem}: ${formatXp(meter.goalXp ?: 0)} goal]"
                } else ""
                source.sendFeedback(
                    Text.literal("  ${floor.name}: ")
                        .styled(Formatting.GRAY)
                        .append(
                            Text.literal("${formatXp(meter.storedXp)} XP")
                                .styled(Formatting.WHITE)
                        )
                        .append(
                            Text.literal(itemInfo)
                                .styled(Formatting.DARK_GRAY)
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
                    .styled(Formatting.GREEN)
                    .append(
                        Text.literal("${formatXp(meter.storedXp)} XP")
                            .styled(Formatting.WHITE)
                    )
                    .append(
                        Text.literal(itemInfo)
                            .styled(Formatting.DARK_GRAY)
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
                    .styled(Formatting.BLUE)
                    .append(
                        Text.literal("${formatXp(meter.storedXp)} XP")
                            .styled(Formatting.WHITE)
                    )
                    .append(
                        Text.literal(itemInfo)
                            .styled(Formatting.DARK_GRAY)
                    )
            )
        }

        // Show mineshaft pity
        data.mineshaftPity?.let { pity ->
            source.sendFeedback(
                Text.literal("Mineshaft Pity: ")
                    .styled(Formatting.DARK_AQUA)
                    .append(
                        Text.literal("${pity.pityValue}/2,000")
                            .styled(Formatting.WHITE)
                    )
            )
        }

        // Show archfiend dye rolls
        data.archfiendDye?.let { dye ->
            source.sendFeedback(
                Text.literal("Archfiend Dye:")
                    .styled(Formatting.DARK_RED)
            )
            source.sendFeedback(
                Text.literal("  High Class Dice: ")
                    .styled(Formatting.GRAY)
                    .append(
                        Text.literal("${dye.highClassDiceRolls} rolls")
                            .styled(Formatting.WHITE)
                    )
                    .append(
                        Text.literal(" (1/666)")
                            .styled(Formatting.DARK_GRAY)
                    )
            )
            source.sendFeedback(
                Text.literal("  Archfiend Dice: ")
                    .styled(Formatting.GRAY)
                    .append(
                        Text.literal("${dye.archfiendDiceRolls} rolls")
                            .styled(Formatting.WHITE)
                    )
                    .append(
                        Text.literal(" (1/6,600)")
                            .styled(Formatting.DARK_GRAY)
                    )
            )
        }

        // Show nyanza dye commissions
        data.nyanzaDye?.let { dye ->
            source.sendFeedback(
                Text.literal("Nyanza Dye: ")
                    .styled(Formatting.GREEN)
                    .append(
                        Text.literal("${formatXp(dye.commissionsCompleted.toLong())} commissions")
                            .styled(Formatting.WHITE)
                    )
                    .append(
                        Text.literal(" (1/250,000)")
                            .styled(Formatting.DARK_GRAY)
                    )
            )
        }

        // Show copper dye visitors seen (per-tier snapshot from the Visitor's Logbook)
        data.copperDye?.let { dye ->
            source.sendFeedback(
                Text.literal("Copper Dye:")
                    .styled(Formatting.GOLD)
            )
            // Display drop rates for each rarity tier
            val dropRates = mapOf(
                "UNCOMMON" to "1/100,000",
                "RARE" to "1/20,000",
                "LEGENDARY" to "1/4,000",
                "MYTHIC" to "1/800",
                "SPECIAL" to "1/500"
            )
            for ((rarity, count) in dye.visitorsSeen) {
                val dropRate = dropRates[rarity.name] ?: "???"
                source.sendFeedback(
                    Text.literal("  ${rarity.name}: ")
                        .styled(Formatting.GRAY)
                        .append(
                            Text.literal("$count seen")
                                .styled(Formatting.WHITE)
                        )
                        .append(
                            Text.literal(" ($dropRate)")
                                .styled(Formatting.DARK_GRAY)
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
                .styled(Formatting.GOLD, Formatting.BOLD)
        )
        source.sendFeedback(
            Text.literal("  /dyetracker link <code>")
                .styled(Formatting.YELLOW)
                .append(
                    Text.literal(" - Link your account")
                        .styled(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker status")
                .styled(Formatting.YELLOW)
                .append(
                    Text.literal(" - Show link status")
                        .styled(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker unlink")
                .styled(Formatting.YELLOW)
                .append(
                    Text.literal(" - Unlink your account")
                        .styled(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker show")
                .styled(Formatting.YELLOW)
                .append(
                    Text.literal(" - Show captured RNG data")
                        .styled(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker sync")
                .styled(Formatting.YELLOW)
                .append(
                    Text.literal(" - Force sync RNG data to backend")
                        .styled(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker reload")
                .styled(Formatting.YELLOW)
                .append(
                    Text.literal(" - Reload config (debug)")
                        .styled(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker gif <add|list|remove|edit>")
                .styled(Formatting.YELLOW)
                .append(
                    Text.literal(" - Manage HUD image/GIF overlays")
                        .styled(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker rotation <toggle|edit>")
                .styled(Formatting.YELLOW)
                .append(
                    Text.literal(" - Toggle/position the dye rotation widget")
                        .styled(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker dye <list|remove|edit|toggle|add>")
                .styled(Formatting.YELLOW)
                .append(
                    Text.literal(" - Manage single-dye progress widgets")
                        .styled(Formatting.GRAY)
                )
        )
        source.sendFeedback(
            Text.literal("  /dyetracker bounce [on|off|toggle]")
                .styled(Formatting.YELLOW)
                .append(
                    Text.literal(" - DVD-bounce your HUD widgets around the screen")
                        .styled(Formatting.GRAY)
                )
        )
    }
}
