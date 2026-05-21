package com.dyetracker.overlay

import com.dyetracker.config.ConfigManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Edit mode for the GIF HUD overlays. Renders the live overlays on a dimmed backdrop,
 * lets the player drag the focused overlay with the mouse, scale it with the scroll
 * wheel, toggle visibility with `E`, and remove with `Delete`. `ESC` saves and exits.
 *
 * Also exposes an "Add overlay" affordance: a button at the top-right opens an inline
 * panel with a URL text field; submitting runs the same [OverlayAddPipeline] used by
 * `/dyetracker gif add` and surfaces progress/result in the panel itself. On success the
 * panel auto-closes and the newly added overlay is auto-focused so the player can
 * immediately drag/scale it.
 *
 * Position is stored as a fraction of the scaled-window size so overlays survive
 * resolution changes; drag deltas are converted to fractional via the current screen
 * size. Mutations call into [ConfigManager] which persists eagerly.
 */
class EditOverlaysScreen : Screen(Text.literal(TITLE)) {

    private var focusedOverlayId: String? = null
    private var gifsDirty: Boolean = false
    private var focusedRectCache: IntArray? = null // set during renderBackground

    // Add-panel state. All fields are touched only on the client thread (writes from IO
    // are marshalled via `MinecraftClient.execute`), so no volatility is needed.
    private var addingMode: Boolean = false
    private var addStatus: String? = null
    private var addStatusIsError: Boolean = false
    private var addJob: Job? = null
    private var addEpoch: Int = 0 // monotonic — guards stale-outcome callbacks
    private var urlField: TextFieldWidget? = null
    private val addScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun init() {
        super.init()
        if (addingMode) {
            buildAddPanel()
        } else {
            buildAddButton()
        }
    }

    /** Top-right "+ Add overlay" button (default state). Sits below the instruction banner. */
    private fun buildAddButton() {
        val button = ButtonWidget.builder(Text.literal("+ Add overlay")) { enterAddingMode() }
            .dimensions(width - ADD_BUTTON_WIDTH - PANEL_MARGIN_PX, ADD_BUTTON_TOP_PX, ADD_BUTTON_WIDTH, BUTTON_HEIGHT_PX)
            .build()
        addDrawableChild(button)
    }

    /** Centered URL input + Add/Cancel buttons + status line (adding state). */
    private fun buildAddPanel() {
        val panelX = (width - PANEL_WIDTH_PX) / 2
        val panelY = PANEL_TOP_PX

        val field = TextFieldWidget(
            textRenderer,
            panelX + PANEL_PADDING_PX,
            panelY + PANEL_PADDING_PX,
            PANEL_WIDTH_PX - 2 * PANEL_PADDING_PX,
            FIELD_HEIGHT_PX,
            Text.literal("URL"),
        )
        field.setMaxLength(URL_MAX_LENGTH)
        field.setText("")
        field.setPlaceholder(Text.literal("Paste image URL (https://…)"))
        urlField = addDrawableChild(field)
        setInitialFocus(field)

        val buttonsY = panelY + PANEL_PADDING_PX + FIELD_HEIGHT_PX + INNER_GAP_PX
        val addButton = ButtonWidget.builder(Text.literal("Add")) { submitAdd() }
            .dimensions(panelX + PANEL_PADDING_PX, buttonsY, ADD_INNER_BUTTON_WIDTH, BUTTON_HEIGHT_PX)
            .build()
        addDrawableChild(addButton)

        val cancelButton = ButtonWidget.builder(Text.literal("Cancel")) { exitAddingMode() }
            .dimensions(
                panelX + PANEL_WIDTH_PX - PANEL_PADDING_PX - CANCEL_INNER_BUTTON_WIDTH,
                buttonsY,
                CANCEL_INNER_BUTTON_WIDTH,
                BUTTON_HEIGHT_PX,
            )
            .build()
        addDrawableChild(cancelButton)
    }

    private fun enterAddingMode() {
        addingMode = true
        addStatus = null
        addStatusIsError = false
        clearAndInit()
    }

    /** Tear down the add panel state and return to default. Cancels any in-flight add. */
    private fun exitAddingMode() {
        addJob?.cancel()
        addJob = null
        // Bump the epoch so any callbacks already queued from the cancelled job are
        // ignored when they drain onto the client thread.
        addEpoch++
        urlField = null
        addingMode = false
        addStatus = null
        addStatusIsError = false
        clearAndInit()
    }

    /** Kick off the add pipeline for the current text field value. */
    private fun submitAdd() {
        if (addJob?.isActive == true) return // already submitting
        val raw = urlField?.text.orEmpty()
        // Pipeline trims + validates; do not pre-empty-check here to keep a single
        // source of truth for the "Invalid URL" wording.
        val myEpoch = ++addEpoch
        postStatus(myEpoch, "Downloading…", isError = false)
        addJob = addScope.launch {
            try {
                val outcome = OverlayAddPipeline.addFromUrl(raw) { stage ->
                    val label = when (stage) {
                        OverlayAddPipeline.Stage.DOWNLOADING -> "Downloading…"
                        OverlayAddPipeline.Stage.DECODING -> "Decoding…"
                        OverlayAddPipeline.Stage.UPLOADING -> "Uploading to GPU…"
                        OverlayAddPipeline.Stage.FINALIZING -> "Saving…"
                    }
                    postStatus(myEpoch, label, isError = false)
                }
                MinecraftClient.getInstance()?.execute {
                    if (myEpoch != addEpoch) return@execute // stale — user moved on
                    when (outcome) {
                        is OverlayAddPipeline.Outcome.Success -> {
                            focusedOverlayId = outcome.id
                            exitAddingMode()
                        }
                        is OverlayAddPipeline.Outcome.Failure -> {
                            addStatus = outcome.message
                            addStatusIsError = true
                            addJob = null
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
            if (myEpoch != addEpoch) return@execute
            addStatus = message
            addStatusIsError = isError
        }
    }

    /**
     * Paints the dim backdrop, the live overlays, the focus marker, and the add-panel
     * background — everything that needs to render UNDERNEATH the widgets. `Screen` calls
     * this first (on its own root layer) before [render] runs the widget list, so painting
     * here keeps widgets visible on top.
     */
    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderBackground(context, mouseX, mouseY, delta)

        context.fill(0, 0, width, height, BACKDROP_ARGB)

        val now = Util.getMeasuringTimeMs()
        val gifs = ConfigManager.config.gifs
        focusedRectCache = null
        for (i in gifs.indices) {
            val cfg = gifs[i]
            val id = OverlayTextureManager.currentFrameIdentifier(cfg.id, now, paused = false) ?: continue
            val srcW = OverlayTextureManager.widthOf(cfg.id)
            val srcH = OverlayTextureManager.heightOf(cfg.id)
            if (srcW <= 0 || srcH <= 0) continue

            val drawW = max(1, (srcW * cfg.scale).roundToInt())
            val drawH = max(1, (srcH * cfg.scale).roundToInt())
            val centerX = (cfg.x * width).roundToInt()
            val centerY = (cfg.y * height).roundToInt()
            val x = centerX - drawW / 2
            val y = centerY - drawH / 2

            val isFocused = cfg.id == focusedOverlayId
            val isHidden = !cfg.visible

            // 12-arg overload: separate regionW/regionH from destW/destH so scaling
            // stretches the texture instead of tiling it.
            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                id,
                x, y,
                0f, 0f,
                drawW, drawH,
                srcW, srcH,
                srcW, srcH,
            )
            if (isHidden) {
                context.fill(x, y, x + drawW, y + drawH, HIDDEN_DIM_ARGB)
                val midY = y + drawH / 2
                context.fill(x, midY, x + drawW, midY + 1, STRIKETHROUGH_ARGB)
            }

            if (isFocused) {
                focusedRectCache = intArrayOf(x, y, drawW, drawH)
            }
        }

        focusedRectCache?.let { rect ->
            drawFocusBorder(context, rect[0], rect[1], rect[2], rect[3])
            val label = "[${focusedOverlayId}]"
            context.drawTextWithShadow(
                textRenderer,
                label,
                rect[0] + LABEL_MARGIN_PX,
                rect[1] + LABEL_MARGIN_PX,
                FOCUS_LABEL_ARGB,
            )
        }

        if (addingMode) {
            renderAddPanelBackground(context)
        }
    }

    /**
     * Paints things ON TOP of the widgets: the instruction banner across the screen top
     * (sits behind the "+ Add overlay" button because of its higher y position — both are
     * fine since the button text is in the right portion) and, in adding mode, the status
     * line text (positioned at the bottom of the panel so it does not overlap the text
     * field or buttons).
     */
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal(INSTRUCTION_BANNER),
            width / 2,
            BANNER_TOP_PX,
            BANNER_TEXT_ARGB,
        )

        if (addingMode) {
            renderAddPanelStatusLine(context)
        }
    }

    /** Panel background + 1px border — drawn UNDER the text field and buttons. */
    private fun renderAddPanelBackground(context: DrawContext) {
        val panelX = (width - PANEL_WIDTH_PX) / 2
        val panelY = PANEL_TOP_PX
        context.fill(
            panelX,
            panelY,
            panelX + PANEL_WIDTH_PX,
            panelY + PANEL_HEIGHT_PX,
            PANEL_BACKGROUND_ARGB,
        )
        context.fill(panelX, panelY, panelX + PANEL_WIDTH_PX, panelY + 1, PANEL_BORDER_ARGB)
        context.fill(panelX, panelY + PANEL_HEIGHT_PX - 1, panelX + PANEL_WIDTH_PX, panelY + PANEL_HEIGHT_PX, PANEL_BORDER_ARGB)
        context.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT_PX, PANEL_BORDER_ARGB)
        context.fill(panelX + PANEL_WIDTH_PX - 1, panelY, panelX + PANEL_WIDTH_PX, panelY + PANEL_HEIGHT_PX, PANEL_BORDER_ARGB)
    }

    /** Status text below the buttons — rendered on top of widgets so it stays visible. */
    private fun renderAddPanelStatusLine(context: DrawContext) {
        val panelX = (width - PANEL_WIDTH_PX) / 2
        val panelY = PANEL_TOP_PX
        val status = addStatus ?: return
        val color = if (addStatusIsError) STATUS_ERROR_ARGB else STATUS_OK_ARGB
        context.drawTextWithShadow(
            textRenderer,
            status,
            panelX + PANEL_PADDING_PX,
            panelY + PANEL_HEIGHT_PX - PANEL_PADDING_PX - STATUS_TEXT_HEIGHT_PX,
            color,
        )
    }

    /** Draw a 1-pixel border using four [DrawContext.fill] calls. */
    private fun drawFocusBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        context.fill(x - 1, y - 1, x + w + 1, y, FOCUS_BORDER_ARGB) // top
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, FOCUS_BORDER_ARGB) // bottom
        context.fill(x - 1, y, x, y + h, FOCUS_BORDER_ARGB) // left
        context.fill(x + w, y, x + w + 1, y + h, FOCUS_BORDER_ARGB) // right
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        // Widgets (the "+ Add overlay" button, text field, Add/Cancel) get first crack.
        if (super.mouseClicked(click, doubled)) return true
        if (addingMode) {
            // Click outside the panel just defocuses widgets — leave overlay focus alone.
            return false
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false
        }
        val mx = click.x()
        val my = click.y()
        // Iterate top-down: the last entry renders last (on top in z-order in render()),
        // so reversing matches what the player sees.
        val gifs = ConfigManager.config.gifs
        for (i in gifs.indices.reversed()) {
            val cfg = gifs[i]
            // Hidden overlays remain clickable so the player can re-enable them.
            val srcW = OverlayTextureManager.widthOf(cfg.id)
            val srcH = OverlayTextureManager.heightOf(cfg.id)
            if (srcW <= 0 || srcH <= 0) continue
            val drawW = max(1, (srcW * cfg.scale).roundToInt())
            val drawH = max(1, (srcH * cfg.scale).roundToInt())
            val centerX = cfg.x * width
            val centerY = cfg.y * height
            val x0 = centerX - drawW / 2.0
            val y0 = centerY - drawH / 2.0
            if (mx in x0..(x0 + drawW) && my in y0..(y0 + drawH)) {
                focusedOverlayId = cfg.id
                return true
            }
        }
        focusedOverlayId = null
        return true
    }

    override fun mouseDragged(click: Click, deltaX: Double, deltaY: Double): Boolean {
        if (addingMode) return super.mouseDragged(click, deltaX, deltaY)
        val id = focusedOverlayId ?: return super.mouseDragged(click, deltaX, deltaY)
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(click, deltaX, deltaY)
        }
        val dxFrac = (deltaX / width).toFloat()
        val dyFrac = (deltaY / height).toFloat()
        // Transient update — flushed on mouseReleased / close() to avoid a disk write per
        // drag tick (60+/sec during a fast drag).
        ConfigManager.updateGifTransient(id) { cfg ->
            cfg.copy(
                x = clamp(cfg.x + dxFrac, POSITION_MIN, POSITION_MAX),
                y = clamp(cfg.y + dyFrac, POSITION_MIN, POSITION_MAX),
            )
        }
        gifsDirty = true
        return true
    }

    override fun mouseReleased(click: Click): Boolean {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && gifsDirty) {
            ConfigManager.flushGifs()
            gifsDirty = false
        }
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (addingMode) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        val id = focusedOverlayId ?: return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        if (vertical == 0.0) return false
        val base = if (isShiftHeld()) SCALE_STEP_FINE else SCALE_STEP_COARSE
        val factor = base.pow(vertical.toFloat())
        // Transient + flush so a fast scroll burst hits disk once, not once per tick.
        ConfigManager.updateGifTransient(id) { cfg ->
            cfg.copy(scale = clamp(cfg.scale * factor, SCALE_MIN, SCALE_MAX))
        }
        gifsDirty = true
        ConfigManager.flushGifs()
        gifsDirty = false
        return true
    }

    override fun keyPressed(input: KeyInput): Boolean {
        val key = input.key()

        // ESC has scoped meaning: in add mode it dismisses the panel; otherwise it closes
        // the whole screen.
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (addingMode) {
                exitAddingMode()
                return true
            }
            close()
            return true
        }

        // While the URL text field is focused, hand typing keys (including Enter, Delete,
        // letters like 'E') to widgets first so they don't fire overlay-edit shortcuts.
        if (addingMode) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                submitAdd()
                return true
            }
            return super.keyPressed(input)
        }

        when (key) {
            GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                val id = focusedOverlayId ?: return super.keyPressed(input)
                if (ConfigManager.removeGif(id)) {
                    OverlayTextureManager.release(id)
                }
                focusedOverlayId = null
                return true
            }
            GLFW.GLFW_KEY_E -> {
                val id = focusedOverlayId ?: return super.keyPressed(input)
                ConfigManager.updateGif(id) { it.copy(visible = !it.visible) }
                return true
            }
        }
        return super.keyPressed(input)
    }

    override fun shouldPause(): Boolean = false

    override fun close() {
        // Cancel any in-flight add so the worker thread does not outlive the screen.
        addScope.cancel()
        // Flush any in-flight drag/scale changes that were buffered via updateGifTransient.
        if (gifsDirty) {
            ConfigManager.flushGifs()
            gifsDirty = false
        }
        MinecraftClient.getInstance()?.setScreen(null)
    }

    private fun clamp(value: Float, lo: Float, hi: Float): Float = max(lo, min(hi, value))

    /** Poll GLFW directly because `Screen.hasShiftDown()` is no longer present in 1.21.10. */
    private fun isShiftHeld(): Boolean {
        val handle = MinecraftClient.getInstance().window.handle
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
    }

    companion object {
        private const val TITLE = "DyeTracker — Edit Overlays"
        private const val INSTRUCTION_BANNER =
            "Drag to move • Scroll to scale • Shift+Scroll = fine • Del to remove • E to toggle visibility • ESC to save"
        private const val BACKDROP_ARGB: Int = 0x80_00_00_00.toInt()
        private const val HIDDEN_DIM_ARGB: Int = 0xB0_00_00_00.toInt()
        private const val STRIKETHROUGH_ARGB: Int = 0xCC_FF_55_55.toInt()
        private const val FOCUS_BORDER_ARGB: Int = 0xFF_FF_FF_FF.toInt()
        private const val FOCUS_LABEL_ARGB: Int = 0xFF_FF_FF_FF.toInt()
        private const val BANNER_TEXT_ARGB: Int = 0xFF_FF_FF_FF.toInt()
        private const val BANNER_TOP_PX = 6
        private const val LABEL_MARGIN_PX = 2
        private const val POSITION_MIN = 0.05f
        private const val POSITION_MAX = 0.95f
        private const val SCALE_MIN = 0.1f
        private const val SCALE_MAX = 5.0f
        private const val SCALE_STEP_COARSE = 1.1f
        private const val SCALE_STEP_FINE = 1.02f

        // Add-panel layout constants.
        private const val PANEL_MARGIN_PX = 6
        // Sits below the instruction banner (banner text is ~9px tall at y=BANNER_TOP_PX=6).
        private const val ADD_BUTTON_TOP_PX = 20
        private const val PANEL_TOP_PX = 44
        private const val PANEL_WIDTH_PX = 360
        private const val PANEL_HEIGHT_PX = 80
        private const val PANEL_PADDING_PX = 8
        private const val INNER_GAP_PX = 6
        private const val FIELD_HEIGHT_PX = 20
        private const val BUTTON_HEIGHT_PX = 20
        private const val ADD_BUTTON_WIDTH = 100
        private const val ADD_INNER_BUTTON_WIDTH = 80
        private const val CANCEL_INNER_BUTTON_WIDTH = 80
        private const val URL_MAX_LENGTH = 2048
        private const val STATUS_TEXT_HEIGHT_PX = 9
        private const val PANEL_BACKGROUND_ARGB: Int = 0xC8_10_10_10.toInt()
        private const val PANEL_BORDER_ARGB: Int = 0xFF_55_55_55.toInt()
        private const val STATUS_OK_ARGB: Int = 0xFF_BF_BF_BF.toInt()
        private const val STATUS_ERROR_ARGB: Int = 0xFF_FF_55_55.toInt()
    }
}
