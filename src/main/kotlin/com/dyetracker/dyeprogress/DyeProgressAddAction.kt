package com.dyetracker.dyeprogress

import com.dyetracker.config.ConfigManager
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.edit.EditScreenAction
import com.dyetracker.ui.edit.WidgetEditScreen
import com.dyetracker.ui.theme.UiTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

/**
 * "+ Add dye" affordance for the generalized edit screen (PBI 34), contributed as an
 * [EditScreenAction] so the screen carries no dye-specific logic. The panel offers a dye search
 * field over a fixed pool of selectable dye-row buttons (Minecraft has no native dropdown and
 * there are 60+ dyes, so a searchable filtered list beats a long cycling button) plus a profile
 * text field. Submitting resolves the typed profile to a `profileId` and adds a widget via the
 * shared [DyeProgressAdder], auto-focusing the new widget so it can be dragged immediately.
 *
 * Holds transient activation state (modal: one action active at a time). IO callbacks are
 * marshalled to the client thread and guarded by a monotonic [epoch] so a cancelled/superseded
 * submit can never mutate a stale panel (mirrors `GifAddAction`).
 */
object DyeProgressAddAction : EditScreenAction {

    override val label: String = "+ Add dye"

    private var searchField: TextFieldWidget? = null
    private var profileField: TextFieldWidget? = null
    private var rowButtons: List<ButtonWidget> = emptyList()

    private var visibleMatches: List<DyeOption> = emptyList()
    private var totalMatches: Int = 0
    private var selected: DyeOption? = null

    private var status: String? = null
    private var statusIsError: Boolean = false
    private var job: Job? = null
    private var epoch: Int = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onActivate(screen: WidgetEditScreen) {
        resetTransient()

        val panelX = (screen.width - PANEL_WIDTH_PX) / 2
        val contentLeft = panelX + PANEL_PADDING_PX
        val contentWidth = PANEL_WIDTH_PX - 2 * PANEL_PADDING_PX

        val search = TextFieldWidget(
            screen.textRenderer,
            contentLeft,
            PANEL_TOP_PX + SEARCH_DY,
            contentWidth,
            FIELD_HEIGHT_PX,
            Text.literal("Search dyes"),
        )
        search.setPlaceholder(Text.literal("Search dyes…"))
        search.setChangedListener { query -> refreshMatches(query) }
        searchField = screen.addActionWidget(search)
        screen.focusInitial(search)

        rowButtons = (0 until MAX_VISIBLE_ROWS).map { i ->
            val rowY = PANEL_TOP_PX + LIST_TOP_DY + i * ROW_STRIDE_PX
            screen.addActionWidget(
                ButtonWidget.builder(Text.literal(" ")) { selectRow(i) }
                    .dimensions(contentLeft, rowY, contentWidth, ROW_HEIGHT_PX)
                    .build(),
            )
        }

        val profile = TextFieldWidget(
            screen.textRenderer,
            contentLeft,
            PANEL_TOP_PX + PROFILE_DY,
            contentWidth,
            FIELD_HEIGHT_PX,
            Text.literal("Profile"),
        )
        profile.setMaxLength(PROFILE_MAX_LENGTH)
        profile.setPlaceholder(Text.literal("Profile name (e.g. Mango)"))
        profile.setText(lastUsedProfileName())
        profileField = screen.addActionWidget(profile)

        val buttonsY = PANEL_TOP_PX + BUTTONS_DY
        screen.addActionWidget(
            ButtonWidget.builder(Text.literal("Add")) { submit(screen) }
                .dimensions(contentLeft, buttonsY, INNER_BUTTON_WIDTH, CONTROL_HEIGHT_PX)
                .build(),
        )
        screen.addActionWidget(
            ButtonWidget.builder(Text.literal("Cancel")) { screen.finishActiveAction(null) }
                .dimensions(
                    panelX + PANEL_WIDTH_PX - PANEL_PADDING_PX - INNER_BUTTON_WIDTH,
                    buttonsY,
                    INNER_BUTTON_WIDTH,
                    CONTROL_HEIGHT_PX,
                )
                .build(),
        )

        refreshMatches("")
    }

    override fun onDismiss() {
        job?.cancel()
        job = null
        // Bump epoch so queued callbacks from a cancelled job are ignored when they drain.
        epoch++
        searchField = null
        profileField = null
        rowButtons = emptyList()
        resetTransient()
    }

    override fun keyPressed(screen: WidgetEditScreen, keyCode: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit(screen)
            return true
        }
        return false
    }

    override fun renderBackground(screen: WidgetEditScreen, context: DrawContext) {
        val panelX = (screen.width - PANEL_WIDTH_PX) / 2
        UiDraw.filledBox(
            context,
            panelX,
            PANEL_TOP_PX,
            PANEL_WIDTH_PX,
            PANEL_HEIGHT_PX,
            UiTheme.Colors.PANEL_BACKGROUND,
            UiTheme.Colors.PANEL_BORDER,
        )
    }

    override fun renderForeground(screen: WidgetEditScreen, context: DrawContext) {
        val panelX = (screen.width - PANEL_WIDTH_PX) / 2
        val left = panelX + PANEL_PADDING_PX

        // Header + match count.
        val countText = if (totalMatches > visibleMatches.size) {
            "Add dye  ·  ${visibleMatches.size} of $totalMatches (refine search)"
        } else {
            "Add dye  ·  $totalMatches match(es)"
        }
        UiDraw.drawText(context, screen.textRenderer, countText, left, PANEL_TOP_PX + HEADER_DY, UiTheme.Colors.TEXT_SECONDARY)

        // Status / hint line.
        val message = status ?: DEFAULT_HINT
        val color = if (status != null && statusIsError) UiTheme.Colors.STATUS_ERROR else UiTheme.Colors.STATUS_OK
        UiDraw.drawText(context, screen.textRenderer, message, left, PANEL_TOP_PX + STATUS_DY, color)
    }

    /** Recompute the filtered dye list and repaint the fixed row-button pool. */
    private fun refreshMatches(query: String) {
        val matches = DyeCatalog.filter(query)
        totalMatches = matches.size
        visibleMatches = matches.take(MAX_VISIBLE_ROWS)
        // Keep an explicit selection if it survives the new filter; otherwise default to the first.
        val priorId = selected?.dyeId
        selected = visibleMatches.firstOrNull { it.dyeId == priorId } ?: visibleMatches.firstOrNull()
        for (i in rowButtons.indices) {
            val button = rowButtons[i]
            val option = visibleMatches.getOrNull(i)
            if (option == null) {
                button.visible = false
                button.active = false
            } else {
                button.visible = true
                button.active = true
            }
        }
        updateRowLabels()
    }

    private fun selectRow(index: Int) {
        selected = visibleMatches.getOrNull(index) ?: return
        updateRowLabels()
    }

    private fun updateRowLabels() {
        for (i in rowButtons.indices) {
            val option = visibleMatches.getOrNull(i) ?: continue
            val marker = if (option.dyeId == selected?.dyeId) SELECTED_PREFIX else UNSELECTED_PREFIX
            rowButtons[i].message = Text.literal(marker + option.displayName)
        }
    }

    private fun submit(screen: WidgetEditScreen) {
        if (job?.isActive == true) return
        val dye = selected
        val profileName = profileField?.text?.trim().orEmpty()
        val myEpoch = ++epoch

        if (!ConfigManager.config.isLinked()) {
            postStatus(myEpoch, MSG_NOT_LINKED, isError = true)
            return
        }
        if (dye == null) {
            postStatus(myEpoch, MSG_PICK_DYE, isError = true)
            return
        }
        if (profileName.isEmpty()) {
            postStatus(myEpoch, MSG_ENTER_PROFILE, isError = true)
            return
        }

        val username = ConfigManager.config.linkedUsername
        postStatus(myEpoch, MSG_RESOLVING, isError = false)
        job = scope.launch {
            try {
                val result = DyeProgressAdder.resolveAndAdd(username, dye.dyeId, profileName)
                MinecraftClient.getInstance()?.execute {
                    if (myEpoch != epoch) return@execute // stale — user moved on
                    handleResult(screen, result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                postStatus(myEpoch, "Unexpected error: ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    private fun handleResult(screen: WidgetEditScreen, result: DyeProgressAdder.Result) {
        when (result) {
            is DyeProgressAdder.Result.Added -> screen.finishActiveAction(result.id)
            DyeProgressAdder.Result.NotLinked -> setError(MSG_NOT_LINKED)
            DyeProgressAdder.Result.InvalidDye -> setError(MSG_INVALID_DYE)
            DyeProgressAdder.Result.EmptyProfile -> setError(MSG_ENTER_PROFILE)
            is DyeProgressAdder.Result.UnknownProfile ->
                setError("No profile '${result.typed}'. Available: ${result.available.joinToString()}")
            is DyeProgressAdder.Result.NetworkError -> setError("Couldn't load profiles: ${result.message}")
        }
    }

    private fun setError(message: String) {
        status = message
        statusIsError = true
        job = null
    }

    /** Set the status line iff the calling job is still current; marshals to the client thread. */
    private fun postStatus(myEpoch: Int, message: String, isError: Boolean) {
        MinecraftClient.getInstance()?.execute {
            if (myEpoch != epoch) return@execute
            status = message
            statusIsError = isError
        }
    }

    private fun resetTransient() {
        visibleMatches = emptyList()
        totalMatches = 0
        selected = null
        status = null
        statusIsError = false
    }

    /** Pre-fill the profile field with the most recently configured widget's profile, for convenience. */
    private fun lastUsedProfileName(): String =
        ConfigManager.dyeProgressPlacements.all().lastOrNull()?.profileName.orEmpty()

    private const val SELECTED_PREFIX = "» "
    private const val UNSELECTED_PREFIX = "  "
    private const val DEFAULT_HINT = "Pick a dye • type your profile • Add"
    private const val MSG_NOT_LINKED = "Link your account first (/dyetracker link)"
    private const val MSG_PICK_DYE = "Pick a dye from the list"
    private const val MSG_ENTER_PROFILE = "Enter a profile name"
    private const val MSG_INVALID_DYE = "That dye isn't recognized"
    private const val MSG_RESOLVING = "Resolving profile…"

    private const val PROFILE_MAX_LENGTH = 64
    private const val MAX_VISIBLE_ROWS = 6

    private const val PANEL_TOP_PX = 24
    private const val PANEL_WIDTH_PX = 360
    private const val PANEL_PADDING_PX = 8
    private const val INNER_GAP_PX = 4
    private const val FIELD_HEIGHT_PX = 20
    private const val ROW_HEIGHT_PX = 16
    private const val ROW_GAP_PX = 2
    private const val ROW_STRIDE_PX = ROW_HEIGHT_PX + ROW_GAP_PX
    private const val CONTROL_HEIGHT_PX = 20
    private const val INNER_BUTTON_WIDTH = 80
    private const val STATUS_TEXT_HEIGHT_PX = 9

    // Vertical offsets relative to PANEL_TOP_PX.
    private const val HEADER_DY = PANEL_PADDING_PX
    private const val SEARCH_DY = HEADER_DY + STATUS_TEXT_HEIGHT_PX + INNER_GAP_PX
    private const val LIST_TOP_DY = SEARCH_DY + FIELD_HEIGHT_PX + INNER_GAP_PX
    private const val LIST_HEIGHT_PX = MAX_VISIBLE_ROWS * ROW_STRIDE_PX
    private const val PROFILE_DY = LIST_TOP_DY + LIST_HEIGHT_PX + INNER_GAP_PX
    private const val BUTTONS_DY = PROFILE_DY + FIELD_HEIGHT_PX + INNER_GAP_PX
    private const val STATUS_DY = BUTTONS_DY + CONTROL_HEIGHT_PX + INNER_GAP_PX
    private const val PANEL_HEIGHT_PX = STATUS_DY + STATUS_TEXT_HEIGHT_PX + PANEL_PADDING_PX
}
