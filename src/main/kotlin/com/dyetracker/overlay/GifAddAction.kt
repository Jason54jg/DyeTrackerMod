package com.dyetracker.overlay

import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.edit.EditScreenAction
import com.dyetracker.ui.edit.WidgetEditScreen
import com.dyetracker.ui.theme.UiTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

/**
 * GIF-specific "+ Add overlay" affordance for the generalized edit screen. Contributed as an
 * [EditScreenAction] so the screen carries no GIF logic. Opening it shows a centered URL field
 * + Add/Cancel buttons; submitting runs the shared [OverlayAddPipeline] and surfaces progress/
 * errors inline. On success the new overlay is auto-focused so it can be dragged immediately.
 *
 * Holds transient activation state (the edit screen is modal, one action active at a time). IO
 * callbacks are marshalled to the client thread and guarded by a monotonic [epoch] so a
 * cancelled/superseded submit can never mutate a stale panel.
 */
object GifAddAction : EditScreenAction {

    override val label: String = "+ Add overlay"

    private var urlField: TextFieldWidget? = null
    private var status: String? = null
    private var statusIsError: Boolean = false
    private var job: Job? = null
    private var epoch: Int = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onActivate(screen: WidgetEditScreen) {
        status = null
        statusIsError = false

        val panelX = (screen.width - PANEL_WIDTH_PX) / 2
        val panelY = PANEL_TOP_PX

        val field = TextFieldWidget(
            screen.textRenderer,
            panelX + PANEL_PADDING_PX,
            panelY + PANEL_PADDING_PX,
            PANEL_WIDTH_PX - 2 * PANEL_PADDING_PX,
            FIELD_HEIGHT_PX,
            Text.literal("URL"),
        )
        field.setMaxLength(URL_MAX_LENGTH)
        field.setText("")
        field.setPlaceholder(Text.literal("Paste image URL (https://…)"))
        urlField = screen.addActionWidget(field)
        screen.focusInitial(field)

        val buttonsY = panelY + PANEL_PADDING_PX + FIELD_HEIGHT_PX + INNER_GAP_PX
        screen.addActionWidget(
            ButtonWidget.builder(Text.literal("Add")) { submit(screen) }
                .dimensions(panelX + PANEL_PADDING_PX, buttonsY, INNER_BUTTON_WIDTH, CONTROL_HEIGHT_PX)
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
    }

    override fun onDismiss() {
        job?.cancel()
        job = null
        // Bump the epoch so any callbacks already queued from the cancelled job are ignored
        // when they drain onto the client thread.
        epoch++
        urlField = null
        status = null
        statusIsError = false
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
        val message = status ?: return
        val panelX = (screen.width - PANEL_WIDTH_PX) / 2
        val color = if (statusIsError) UiTheme.Colors.STATUS_ERROR else UiTheme.Colors.STATUS_OK
        UiDraw.drawText(
            context,
            screen.textRenderer,
            message,
            panelX + PANEL_PADDING_PX,
            PANEL_TOP_PX + PANEL_HEIGHT_PX - PANEL_PADDING_PX - STATUS_TEXT_HEIGHT_PX,
            color,
        )
    }

    /** Kick off the add pipeline for the current text field value. */
    private fun submit(screen: WidgetEditScreen) {
        if (job?.isActive == true) return // already submitting
        val raw = urlField?.text.orEmpty()
        val myEpoch = ++epoch
        postStatus(myEpoch, "Downloading…", isError = false)
        job = scope.launch {
            try {
                val outcome = OverlayAddPipeline.addFromUrl(raw) { stage ->
                    postStatus(myEpoch, stageLabel(stage), isError = false)
                }
                MinecraftClient.getInstance()?.execute {
                    if (myEpoch != epoch) return@execute // stale — user moved on
                    when (outcome) {
                        is OverlayAddPipeline.Outcome.Success -> screen.finishActiveAction(outcome.id)
                        is OverlayAddPipeline.Outcome.Failure -> {
                            status = outcome.message
                            statusIsError = true
                            job = null
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                postStatus(myEpoch, "Unexpected error: ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    /** Set the status line iff the calling job is still current; marshals to the client thread. */
    private fun postStatus(myEpoch: Int, message: String, isError: Boolean) {
        MinecraftClient.getInstance()?.execute {
            if (myEpoch != epoch) return@execute
            status = message
            statusIsError = isError
        }
    }

    private fun stageLabel(stage: OverlayAddPipeline.Stage): String = when (stage) {
        OverlayAddPipeline.Stage.DOWNLOADING -> "Downloading…"
        OverlayAddPipeline.Stage.DECODING -> "Decoding…"
        OverlayAddPipeline.Stage.UPLOADING -> "Uploading to GPU…"
        OverlayAddPipeline.Stage.FINALIZING -> "Saving…"
    }

    private const val PANEL_TOP_PX = 44
    private const val PANEL_WIDTH_PX = 360
    private const val PANEL_HEIGHT_PX = 80
    private const val PANEL_PADDING_PX = 8
    private const val INNER_GAP_PX = 6
    private const val FIELD_HEIGHT_PX = 20
    private const val CONTROL_HEIGHT_PX = 20
    private const val INNER_BUTTON_WIDTH = 80
    private const val URL_MAX_LENGTH = 2048
    private const val STATUS_TEXT_HEIGHT_PX = 9
}
