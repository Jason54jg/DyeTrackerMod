package com.dyetracker.dyeprogress

import com.dyetracker.config.ConfigManager
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.edit.WidgetConfigPanel
import com.dyetracker.ui.edit.WidgetEditScreen
import com.dyetracker.ui.theme.UiTheme
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor as DrawContext
import net.minecraft.client.gui.components.Button as ButtonWidget
import net.minecraft.client.gui.components.Checkbox as CheckboxWidget
import net.minecraft.network.chat.Component as Text
*///?} else {
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.CheckboxWidget
import net.minecraft.text.Text
//?}

/** A toggleable piece of the single-dye progress widget, with its checkbox label. */
enum class DyeProgressPiece(val label: String) {
    BACKGROUND("Background"),
    BORDER("Border"),
    PROGRESS_BAR("Progress bar"),
    NAME("Name"),
    SOURCE("Source line"),
    PERCENT("Percent"),
    PERCENT_IN_CORNER("Percent in icon corner"),
}

/** The current on/off value of [piece] in [cfg] (used to initialize the checkbox). Pure. */
fun pieceValue(cfg: DyeProgressWidgetConfig, piece: DyeProgressPiece): Boolean = when (piece) {
    DyeProgressPiece.BACKGROUND -> cfg.showBackground
    DyeProgressPiece.BORDER -> cfg.showBorder
    DyeProgressPiece.PROGRESS_BAR -> cfg.showProgressBar
    DyeProgressPiece.NAME -> cfg.showName
    DyeProgressPiece.SOURCE -> cfg.showSource
    DyeProgressPiece.PERCENT -> cfg.showPercent
    DyeProgressPiece.PERCENT_IN_CORNER -> cfg.percentInIconCorner
}

/** Return a copy of [cfg] with [piece] set to [checked]. Pure (the persist transform). */
fun applyPieceToggle(cfg: DyeProgressWidgetConfig, piece: DyeProgressPiece, checked: Boolean): DyeProgressWidgetConfig =
    when (piece) {
        DyeProgressPiece.BACKGROUND -> cfg.copy(showBackground = checked)
        DyeProgressPiece.BORDER -> cfg.copy(showBorder = checked)
        DyeProgressPiece.PROGRESS_BAR -> cfg.copy(showProgressBar = checked)
        DyeProgressPiece.NAME -> cfg.copy(showName = checked)
        DyeProgressPiece.SOURCE -> cfg.copy(showSource = checked)
        DyeProgressPiece.PERCENT -> cfg.copy(showPercent = checked)
        DyeProgressPiece.PERCENT_IN_CORNER -> cfg.copy(percentInIconCorner = checked)
    }

/**
 * Per-widget configuration panel for the single-dye progress widgets (PBI 36), contributed to the
 * generalized edit screen via the [WidgetConfigPanel] hook (task 36-4). Presents one vanilla
 * [CheckboxWidget] per [DyeProgressPiece] — the dye icon is intentionally NOT represented (it is
 * the always-on minimum). Each checkbox initializes from the focused widget's config and, on
 * toggle, persists that single field through [ConfigManager.dyeProgressPlacements] (immediate
 * flush — discrete clicks, not a drag burst). Because the HUD provider re-reads config every
 * frame, the on-screen widget updates live; no extra refresh wiring is needed.
 *
 * Modal (one panel active at a time), so transient activation state lives in object fields and is
 * cleared in [onDismiss], mirroring [DyeProgressAddAction]. Styling matches the add panel.
 */
object DyeProgressConfigPanel : WidgetConfigPanel {

    private var widgetId: String? = null
    private var headerText: String? = null

    override fun onActivate(screen: WidgetEditScreen, widgetId: String) {
        this.widgetId = widgetId
        val cfg = ConfigManager.dyeProgressPlacements.all().find { it.id == widgetId }
        headerText = cfg?.let { "Configure  ·  ${DyeProgressWidgetView.humanizeDyeId(it.dyeId)}" } ?: HEADER_FALLBACK

        val panelX = (screen.width - PANEL_WIDTH_PX) / 2
        val contentLeft = panelX + PANEL_PADDING_PX
        val contentWidth = PANEL_WIDTH_PX - 2 * PANEL_PADDING_PX

        if (cfg != null) {
            var first: CheckboxWidget? = null
            DyeProgressPiece.entries.forEachIndexed { i, piece ->
                val y = PANEL_TOP_PX + LIST_TOP_DY + i * CHECKBOX_ROW_STRIDE_PX
                //? if >=26.1 {
                /*val checkbox = CheckboxWidget.builder(Text.literal(piece.label), screen.font)
                    .pos(contentLeft, y)
                    .selected(pieceValue(cfg, piece))
                    .onValueChange { _, checked -> persist(piece, checked) }
                    .maxWidth(contentWidth)
                    .build()
                *///?} else {
                val checkbox = CheckboxWidget.builder(Text.literal(piece.label), screen.textRenderer)
                    .pos(contentLeft, y)
                    .checked(pieceValue(cfg, piece))
                    .callback { _, checked -> persist(piece, checked) }
                    .maxWidth(contentWidth)
                    .build()
                //?}
                screen.addActionWidget(checkbox)
                if (first == null) first = checkbox
            }
            first?.let { screen.focusInitial(it) }
        }

        val closeX = panelX + (PANEL_WIDTH_PX - CLOSE_BUTTON_WIDTH_PX) / 2
        screen.addActionWidget(
            ButtonWidget.builder(Text.literal("Close")) { screen.finishActiveAction(null) }
                //? if >=26.1 {
                /*.bounds(closeX, PANEL_TOP_PX + buttonsDy(), CLOSE_BUTTON_WIDTH_PX, CONTROL_HEIGHT_PX)
                *///?} else {
                .dimensions(closeX, PANEL_TOP_PX + buttonsDy(), CLOSE_BUTTON_WIDTH_PX, CONTROL_HEIGHT_PX)
                //?}
                .build(),
        )
    }

    override fun onDismiss() {
        widgetId = null
        headerText = null
    }

    override fun renderBackground(screen: WidgetEditScreen, context: DrawContext) {
        val panelX = (screen.width - PANEL_WIDTH_PX) / 2
        UiDraw.filledBox(
            context,
            panelX,
            PANEL_TOP_PX,
            PANEL_WIDTH_PX,
            panelHeight(),
            UiTheme.Colors.PANEL_BACKGROUND,
            UiTheme.Colors.PANEL_BORDER,
        )
    }

    override fun renderForeground(screen: WidgetEditScreen, context: DrawContext) {
        val panelX = (screen.width - PANEL_WIDTH_PX) / 2
        UiDraw.drawText(
            context,
            //? if >=26.1 {
            /*screen.font,
            *///?} else {
            screen.textRenderer,
            //?}
            headerText ?: HEADER_FALLBACK,
            panelX + PANEL_PADDING_PX,
            PANEL_TOP_PX + HEADER_DY,
            UiTheme.Colors.TEXT_SECONDARY,
        )
    }

    /** Persist a single piece toggle to the focused widget's config (immediate flush). */
    private fun persist(piece: DyeProgressPiece, checked: Boolean) {
        val id = widgetId ?: return
        ConfigManager.dyeProgressPlacements.update(id) { applyPieceToggle(it, piece, checked) }
    }

    private fun buttonsDy(): Int =
        LIST_TOP_DY + DyeProgressPiece.entries.size * CHECKBOX_ROW_STRIDE_PX + INNER_GAP_PX

    private fun panelHeight(): Int = buttonsDy() + CONTROL_HEIGHT_PX + PANEL_PADDING_PX

    private const val HEADER_FALLBACK = "Configure widget"

    private const val PANEL_TOP_PX = 24
    private const val PANEL_WIDTH_PX = 220
    private const val PANEL_PADDING_PX = 8
    private const val INNER_GAP_PX = 4
    private const val HEADER_TEXT_HEIGHT_PX = 9
    private const val CHECKBOX_ROW_STRIDE_PX = 18
    private const val CONTROL_HEIGHT_PX = 20
    private const val CLOSE_BUTTON_WIDTH_PX = 80

    // Vertical offsets relative to PANEL_TOP_PX.
    private const val HEADER_DY = PANEL_PADDING_PX
    private const val LIST_TOP_DY = HEADER_DY + HEADER_TEXT_HEIGHT_PX + INNER_GAP_PX
}
