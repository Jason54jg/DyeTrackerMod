package com.dyetracker.ui.edit

import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.PlacementEditor
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.hud.HudWidgetEntry
import com.dyetracker.ui.hud.HudWidgetHost
import com.dyetracker.ui.hud.HudWidgetRegistry
import com.dyetracker.ui.theme.UiTheme
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Widget-agnostic edit mode for HUD widgets. Renders every editable widget registered via
 * [HudWidgetRegistry] on a dimmed backdrop and lets the player focus one (click), drag to move,
 * scroll to scale (Shift = fine), toggle visibility (`E`), and remove (`Delete`/`Backspace`).
 * `ESC` saves and exits. Mutations go through each widget's feature-supplied [PlacementEditor];
 * the screen knows nothing feature-specific.
 *
 * Per-feature affordances (e.g. the GIF "+ Add overlay" panel) are contributed as
 * [EditScreenAction]s: while no action is active the screen shows a toolbar button per action;
 * activating one hands part of the screen to it until it finishes or is dismissed.
 *
 * Positions are fractions of the scaled-window size so they survive resolution changes; drag
 * deltas are converted to fractional via the current screen size. Drag/scale use the transient
 * update path and flush once on release/close; visibility/delete persist immediately.
 */
class WidgetEditScreen : Screen(Text.literal(TITLE)) {

    private var focusedId: String? = null

    /** The editor whose transient changes are buffered and need flushing on release/close. */
    private var dirtyEditor: PlacementEditor? = null

    /** The currently-open per-feature action panel, or null in normal editing mode. */
    private var activeAction: EditScreenAction? = null

    override fun init() {
        super.init()
        val action = activeAction
        if (action == null) {
            buildToolbar()
        } else {
            action.onActivate(this)
        }
    }

    /** One toolbar button per registered action (top-right, stacked). Shown in normal mode. */
    private fun buildToolbar() {
        var buttonY = TOOLBAR_TOP_PX
        for (action in EditScreenActionRegistry.all()) {
            val button = ButtonWidget.builder(Text.literal(action.label)) { activateAction(action) }
                .dimensions(width - TOOLBAR_BUTTON_WIDTH - TOOLBAR_MARGIN_PX, buttonY, TOOLBAR_BUTTON_WIDTH, CONTROL_HEIGHT_PX)
                .build()
            addDrawableChild(button)
            buttonY += CONTROL_HEIGHT_PX + TOOLBAR_GAP_PX
        }
    }

    private fun activateAction(action: EditScreenAction) {
        activeAction = action
        clearAndInit()
    }

    /** Dismiss the active action (cancel its work) and return to normal editing. */
    private fun dismissActiveAction() {
        activeAction?.onDismiss()
        activeAction = null
        clearAndInit()
    }

    /**
     * Finish the active action successfully; optionally focus a (newly-created) widget by id.
     * Called by an action when its work completes (e.g. a new overlay was added).
     */
    fun finishActiveAction(focusWidgetId: String?) {
        if (focusWidgetId != null) focusedId = focusWidgetId
        dismissActiveAction()
    }

    /** Add a panel widget (button/text field) — public bridge to the protected Screen API. */
    fun <T> addActionWidget(widget: T): T where T : Element, T : Drawable, T : Selectable =
        addDrawableChild(widget)

    /** Set initial keyboard focus to [element] — public bridge to the protected Screen API. */
    fun focusInitial(element: Element) = setInitialFocus(element)

    private fun editableEntries(): List<HudWidgetEntry> =
        HudWidgetRegistry.entries().filter { it.editor != null }

    private fun focusedEntry(): HudWidgetEntry? =
        focusedId?.let { id -> editableEntries().find { it.id == id } }

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderBackground(context, mouseX, mouseY, delta)
        UiDraw.fillRect(context, 0, 0, width, height, UiTheme.Colors.BACKDROP)

        // Live widgets animate in edit mode (paused = false), like the rest of the HUD.
        val renderCtx = RenderContext(context, Util.getMeasuringTimeMs(), paused = false)
        var focusedRect: com.dyetracker.ui.core.Rect? = null
        for (entry in editableEntries()) {
            val rect = HudWidgetHost.boundsOf(entry, width, height) ?: continue
            HudWidgetHost.drawEntryWidget(renderCtx, entry, rect)
            if (!entry.placement.visible) {
                UiDraw.fillRect(context, rect.x, rect.y, rect.width, rect.height, UiTheme.Colors.HIDDEN_DIM)
                UiDraw.fillRect(context, rect.x, rect.y + rect.height / 2, rect.width, 1, UiTheme.Colors.STRIKETHROUGH)
            }
            if (entry.id == focusedId) focusedRect = rect
        }

        focusedRect?.let { rect ->
            UiDraw.strokeRect(context, rect.x - 1, rect.y - 1, rect.width + 2, rect.height + 2, UiTheme.Colors.FOCUS_BORDER)
            UiDraw.drawText(
                context,
                textRenderer,
                "[$focusedId]",
                rect.x + LABEL_MARGIN_PX,
                rect.y + LABEL_MARGIN_PX,
                UiTheme.Colors.TEXT_PRIMARY,
            )
        }

        activeAction?.renderBackground(this, context)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        UiDraw.drawText(
            context,
            textRenderer,
            INSTRUCTION_BANNER,
            0,
            BANNER_TOP_PX,
            UiTheme.Colors.TEXT_PRIMARY,
            shadow = true,
            availableWidth = width,
            alignment = HorizontalAlignment.CENTER,
        )
        activeAction?.renderForeground(this, context)
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        if (super.mouseClicked(click, doubled)) return true
        if (activeAction != null) return false
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        val mx = click.x()
        val my = click.y()
        // Top-down z-order: later entries draw last (on top), so reverse to match what's seen.
        val entries = editableEntries()
        for (i in entries.indices.reversed()) {
            val entry = entries[i]
            // Hidden widgets stay clickable so they can be re-enabled.
            val rect = HudWidgetHost.boundsOf(entry, width, height) ?: continue
            if (rect.contains(mx, my)) {
                focusedId = entry.id
                return true
            }
        }
        focusedId = null
        return true
    }

    override fun mouseDragged(click: Click, deltaX: Double, deltaY: Double): Boolean {
        if (activeAction != null) return super.mouseDragged(click, deltaX, deltaY)
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(click, deltaX, deltaY)
        val entry = focusedEntry() ?: return super.mouseDragged(click, deltaX, deltaY)
        val editor = entry.editor ?: return super.mouseDragged(click, deltaX, deltaY)
        val p = entry.placement
        val newX = clamp(p.x + (deltaX / width).toFloat(), POSITION_MIN, POSITION_MAX)
        val newY = clamp(p.y + (deltaY / height).toFloat(), POSITION_MIN, POSITION_MAX)
        // Transient — flushed on mouseReleased / close() to avoid a disk write per drag tick.
        editor.setPlacementTransient(entry.id, newX, newY, p.scale)
        dirtyEditor = editor
        return true
    }

    override fun mouseReleased(click: Click): Boolean {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) flushDirty()
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (activeAction != null) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        if (vertical == 0.0) return false
        val entry = focusedEntry() ?: return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        val editor = entry.editor ?: return super.mouseScrolled(mouseX, mouseY, horizontal, vertical)
        val base = if (isShiftHeld()) SCALE_STEP_FINE else SCALE_STEP_COARSE
        val factor = base.pow(vertical.toFloat())
        val p = entry.placement
        val newScale = clamp(p.scale * factor, SCALE_MIN, SCALE_MAX)
        // Transient + flush so a fast scroll burst hits disk once, not once per tick.
        editor.setPlacementTransient(entry.id, p.x, p.y, newScale)
        editor.flush()
        dirtyEditor = null
        return true
    }

    override fun keyPressed(input: KeyInput): Boolean {
        val key = input.key()

        // ESC is scoped: in an action it dismisses the panel; otherwise it closes the screen.
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (activeAction != null) {
                dismissActiveAction()
                return true
            }
            close()
            return true
        }

        val action = activeAction
        if (action != null) {
            if (action.keyPressed(this, key)) return true
            return super.keyPressed(input)
        }

        when (key) {
            GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                val entry = focusedEntry() ?: return super.keyPressed(input)
                entry.editor?.remove(entry.id)
                focusedId = null
                return true
            }
            GLFW.GLFW_KEY_E -> {
                val entry = focusedEntry() ?: return super.keyPressed(input)
                entry.editor?.setVisible(entry.id, !entry.placement.visible)
                return true
            }
            GLFW.GLFW_KEY_C -> {
                val entry = focusedEntry() ?: return super.keyPressed(input)
                val panel = entry.configPanel ?: return super.keyPressed(input)
                activateAction(ConfigPanelAction(panel, entry.id))
                return true
            }
        }
        return super.keyPressed(input)
    }

    override fun shouldPause(): Boolean = false

    override fun close() {
        // Dismiss any active action so its worker thread does not outlive the screen, then
        // flush buffered drag/scale changes.
        activeAction?.onDismiss()
        activeAction = null
        flushDirty()
        MinecraftClient.getInstance()?.setScreen(null)
    }

    private fun flushDirty() {
        dirtyEditor?.flush()
        dirtyEditor = null
    }

    private fun clamp(value: Float, lo: Float, hi: Float): Float = max(lo, min(hi, value))

    /** Poll GLFW directly because `Screen.hasShiftDown()` is no longer present in 1.21.10. */
    private fun isShiftHeld(): Boolean {
        val handle = MinecraftClient.getInstance().window.handle
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
    }

    /**
     * Adapts a focused widget's [WidgetConfigPanel] (bound to [widgetId]) onto the existing modal
     * [EditScreenAction] path, so the per-widget config panel reuses the same activate / render /
     * key / ESC-dismiss plumbing as the toolbar actions. Never registered in
     * [EditScreenActionRegistry], so its empty [label] is never shown.
     */
    private class ConfigPanelAction(
        private val panel: WidgetConfigPanel,
        private val widgetId: String,
    ) : EditScreenAction {
        // Never registered as a toolbar action, so this label is only a sensible fallback.
        override val label: String = "Configure"

        override fun onActivate(screen: WidgetEditScreen) = panel.onActivate(screen, widgetId)

        override fun onDismiss() = panel.onDismiss()

        override fun renderBackground(screen: WidgetEditScreen, context: DrawContext) =
            panel.renderBackground(screen, context)

        override fun renderForeground(screen: WidgetEditScreen, context: DrawContext) =
            panel.renderForeground(screen, context)

        override fun keyPressed(screen: WidgetEditScreen, keyCode: Int): Boolean =
            panel.keyPressed(screen, keyCode)
    }

    companion object {
        private const val TITLE = "DyeTracker — Edit HUD"
        private const val INSTRUCTION_BANNER =
            "Drag to move • Scroll to scale • Shift+Scroll = fine • Del to remove • E to toggle visibility • C to configure • ESC to save"
        private const val BANNER_TOP_PX = 6
        private const val LABEL_MARGIN_PX = 2
        private const val POSITION_MIN = 0.05f
        private const val POSITION_MAX = 0.95f
        private const val SCALE_MIN = 0.1f
        private const val SCALE_MAX = 5.0f
        private const val SCALE_STEP_COARSE = 1.1f
        private const val SCALE_STEP_FINE = 1.02f

        // Toolbar layout (top-right, below the instruction banner).
        private const val TOOLBAR_TOP_PX = 20
        private const val TOOLBAR_MARGIN_PX = 6
        private const val TOOLBAR_GAP_PX = 4
        private const val TOOLBAR_BUTTON_WIDTH = 100
        private const val CONTROL_HEIGHT_PX = 20
    }
}
