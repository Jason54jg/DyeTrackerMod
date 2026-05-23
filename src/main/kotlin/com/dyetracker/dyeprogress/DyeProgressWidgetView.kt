package com.dyetracker.dyeprogress

import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.rotation.DyeSprites
import com.dyetracker.ui.components.PanelWidget
import com.dyetracker.ui.components.ProgressBarWidget
import com.dyetracker.ui.components.SpriteWidget
import com.dyetracker.ui.components.TextWidget
import com.dyetracker.ui.core.Alignment
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.Insets
import com.dyetracker.ui.core.VerticalAlignment
import com.dyetracker.ui.core.Widget
import com.dyetracker.ui.layout.Column
import com.dyetracker.ui.layout.Row
import com.dyetracker.ui.layout.Stack
import com.dyetracker.ui.layout.StackChild
import com.dyetracker.ui.theme.UiTheme
import java.util.Locale

/**
 * Builds the single-dye progress widget tree from PBI 30 toolkit components, mirroring the
 * website single-dye overlay (`packages/web/src/pages/OverlayPage.tsx`): a [PanelWidget] card
 * hosting a [Row] of an accent-tinted icon box and an info [Column] — a name/percent header
 * row, a full-width [ProgressBarWidget], and a source/formula line (`COMPLETE` at ≥100%).
 *
 * Pure presentation: [build] selects a render state from the live store status + entry and
 * composes the matching tree. The state-selection and formatting helpers are pure (no
 * `DrawContext`/client) and unit-tested; the composed tree is verified in-client in 34-8.
 */
object DyeProgressWidgetView {

    /** Which variant of the widget to render for the current store state. */
    enum class RenderState {
        /** Cold cache — never fetched yet. */
        LOADING,

        /** Have a trackable value to show (fresh, or last-known after a failed refresh). */
        READY,

        /** Untrackable dye (`progress == null`) or the dye is absent from the response. */
        NOT_TRACKABLE,

        /** Fetch failed and there is no prior value to fall back to. */
        UNAVAILABLE,
    }

    /** Bundled dye sprites are 300×300 (see [DyeSprites]). */
    private const val SPRITE_SOURCE_PX = 300

    /** On-screen sprite size at placement scale 1.0 (sits inside the icon box, alongside text). */
    private const val SPRITE_TARGET_PX = 40

    /** Sprite scale that renders a [SPRITE_SOURCE_PX] sprite at [SPRITE_TARGET_PX]. */
    private const val SPRITE_SCALE = SPRITE_TARGET_PX.toFloat() / SPRITE_SOURCE_PX

    /** Padding between the sprite and the icon box's tinted edge. */
    private const val ICON_BOX_PADDING = 4

    /** Horizontal gap between the icon box and the info column (the card's main-axis gap). */
    private const val CARD_GAP = UiTheme.Spacing.PADDING

    /** Vertical gap between the header row, the bar, and the source line in the info column. */
    private const val ROW_GAP = 3

    /** Minimum gap reserved between the left-aligned name and the right-aligned percent. */
    private const val HEADER_GAP = 8

    /** Progress-bar height for this widget (chunkier than the toolkit default, like the web pill). */
    private const val BAR_HEIGHT = 6

    /** Minimum info-column / progress-bar width so a short name/percent still yields a usable bar. */
    private const val MIN_BAR_WIDTH = 56

    /** Scale for the small, dim source/hint line. */
    private const val SECONDARY_TEXT_SCALE = 0.75f

    /** Mask isolating the RGB channels of an ARGB color (drops the alpha byte). */
    private const val RGB_MASK = 0x00FFFFFF

    /** Alpha applied to the accent color for the icon-box tint (~15%, like the web `accent15`). */
    private const val ICON_BOX_BG_ALPHA = 0x26000000

    /** Font scale for the percent badge overlaid on the icon box (`percentInIconCorner`). */
    private const val CORNER_PERCENT_SCALE = 0.75f

    /** Corner the percent badge pins to within the icon box (bottom-right). */
    private val CORNER_PERCENT_ALIGNMENT = Alignment(HorizontalAlignment.END, VerticalAlignment.BOTTOM)

    /** Progress at/above which the dye is COMPLETE (matches the website overlay). */
    const val COMPLETE_THRESHOLD = 100.0

    private const val COMPLETE_TEXT = "COMPLETE"
    private const val NA_TEXT = "N/A"
    private const val COMPLETE_PERCENT_TEXT = "100%"
    private const val NOT_TRACKABLE_TEXT = "Not trackable"
    private const val LOADING_TEXT = "Loading…"
    private const val UNAVAILABLE_TEXT = "Unavailable"

    /** Opaque-alpha mask OR-ed onto a 6-digit hex color. */
    private const val OPAQUE_ALPHA_MASK = 0xFF000000.toInt()
    private const val HEX_RGB_LENGTH = 6
    private const val HEX_ARGB_LENGTH = 8
    private const val HEX_RADIX = 16

    /** Choose the render variant from the per-profile [status] and the per-dye [entry] (pure). */
    fun selectState(status: DyeProgressStore.Status, entry: DyeProgressEntry?): RenderState = when {
        status == DyeProgressStore.Status.LOADING -> RenderState.LOADING
        entry == null ->
            if (status == DyeProgressStore.Status.ERROR) RenderState.UNAVAILABLE else RenderState.NOT_TRACKABLE
        entry.progress == null -> RenderState.NOT_TRACKABLE
        else -> RenderState.READY
    }

    /** Format a progress value like the website overlay: `N/A` / `100%` / one decimal `42.0%`. */
    fun formatProgress(progress: Double?): String = when {
        progress == null -> NA_TEXT
        progress >= COMPLETE_THRESHOLD -> COMPLETE_PERCENT_TEXT
        else -> String.format(Locale.ROOT, "%.1f%%", progress)
    }

    /**
     * Parse a `#rrggbb` / `rrggbb` (or 8-digit `aarrggbb`) hex color to an ARGB Int, falling back
     * to [UiTheme.Colors.PROGRESS_FILL] when [hex] is null/blank/unparseable. 6-digit values get
     * full opacity.
     */
    fun parseAccentColor(hex: String?): Int {
        val cleaned = hex?.removePrefix("#")?.trim().orEmpty()
        if (cleaned.isEmpty()) return UiTheme.Colors.PROGRESS_FILL
        return try {
            when (cleaned.length) {
                HEX_RGB_LENGTH -> OPAQUE_ALPHA_MASK or cleaned.toInt(HEX_RADIX)
                HEX_ARGB_LENGTH -> cleaned.toLong(HEX_RADIX).toInt()
                else -> UiTheme.Colors.PROGRESS_FILL
            }
        } catch (e: NumberFormatException) {
            UiTheme.Colors.PROGRESS_FILL
        }
    }

    /** Turn a snake_case dye id into a Title-Case label, e.g. `wild_strawberry` → `Wild Strawberry`. */
    fun humanizeDyeId(dyeId: String): String =
        dyeId.split('_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    /** Resolved card fill + border for the background/border toggles (pure; see [cardChrome]). */
    data class CardChrome(val background: Int, val border: Int?)

    /**
     * The card fill/border for the [showBackground]/[showBorder] flags: the neutral
     * `PANEL_BACKGROUND`/`PANEL_BORDER` when on, a transparent fill / no border when off.
     */
    fun cardChrome(showBackground: Boolean, showBorder: Boolean): CardChrome = CardChrome(
        background = if (showBackground) UiTheme.Colors.PANEL_BACKGROUND else UiTheme.Colors.TRANSPARENT,
        border = if (showBorder) UiTheme.Colors.PANEL_BORDER else null,
    )

    /**
     * Which pieces the READY widget's header/info-column include and where the percent renders,
     * derived purely from the config flags (and whether a source line has text). The dye icon is
     * always present and is not represented here. Drives [readyPanel]'s tree composition and is
     * unit-tested in lieu of the live-renderer tree.
     */
    data class ReadyLayout(
        val nameInHeader: Boolean,
        val percentInHeader: Boolean,
        val percentInCorner: Boolean,
        val showBar: Boolean,
        val showSource: Boolean,
    ) {
        /** True when the header row has at least one of name/percent and should be rendered. */
        val hasHeader: Boolean get() = nameInHeader || percentInHeader
    }

    /** Resolve the READY-state piece layout for [cfg]; [hasSource] gates the source line. */
    fun readyLayout(cfg: DyeProgressWidgetConfig, hasSource: Boolean): ReadyLayout = ReadyLayout(
        nameInHeader = cfg.showName,
        percentInHeader = cfg.showPercent && !cfg.percentInIconCorner,
        percentInCorner = cfg.showPercent && cfg.percentInIconCorner,
        showBar = cfg.showProgressBar,
        showSource = cfg.showSource && hasSource,
    )

    /** Compose the widget tree for [cfg] given the current store [status] and per-dye [entry]. */
    fun build(cfg: DyeProgressWidgetConfig, status: DyeProgressStore.Status, entry: DyeProgressEntry?): Widget =
        when (selectState(status, entry)) {
            RenderState.LOADING -> hintPanel(cfg, LOADING_TEXT, UiTheme.Colors.TEXT_SECONDARY)
            RenderState.UNAVAILABLE -> hintPanel(cfg, UNAVAILABLE_TEXT, UiTheme.Colors.STATUS_ERROR)
            RenderState.NOT_TRACKABLE -> notTrackablePanel(cfg, entry)
            RenderState.READY -> readyPanel(cfg, entry!!)
        }

    private fun dyeSprite(dyeId: String): SpriteWidget = SpriteWidget(DyeSprites.spriteId(dyeId), SPRITE_SCALE)

    private fun nameOf(cfg: DyeProgressWidgetConfig, entry: DyeProgressEntry?): String =
        entry?.name?.takeIf { it.isNotBlank() } ?: humanizeDyeId(cfg.dyeId)

    /** Derive the icon-box background tint for [accent] (the accent color at [ICON_BOX_BG_ALPHA]). */
    private fun iconTint(accent: Int): Int = (accent and RGB_MASK) or ICON_BOX_BG_ALPHA

    /** The icon-box tint for [entry]'s color, or the neutral tint when no usable color is present. */
    private fun iconTintFor(entry: DyeProgressEntry?): Int =
        entry?.color?.takeIf { it.isNotBlank() }?.let { iconTint(parseAccentColor(it)) }
            ?: UiTheme.Colors.ICON_BOX_TINT

    /** The dye sprite inside a tinted, subtly bordered box (mirrors the web overlay icon box). */
    private fun iconBox(dyeId: String, bgTint: Int): Widget = PanelWidget(
        child = dyeSprite(dyeId),
        padding = Insets.all(ICON_BOX_PADDING),
        background = bgTint,
        border = UiTheme.Colors.ICON_BOX_BORDER,
    )

    /**
     * The card: a [Row] of [rowChildren] (the icon, optionally followed by an info column),
     * vertically centered, wrapped in a panel painted with [chrome]'s fill/border. When the info
     * column is empty the caller passes just the icon, so the card collapses to the icon box.
     */
    private fun card(rowChildren: List<Widget>, chrome: CardChrome): Widget = PanelWidget(
        child = Row(
            children = rowChildren,
            spacing = CARD_GAP,
            crossAxisAlignment = VerticalAlignment.CENTER,
        ),
        background = chrome.background,
        border = chrome.border,
    )

    /**
     * The card's left cell: the [iconBox], optionally overlaid with a [cornerBadge] (the
     * percent) pinned to [CORNER_PERCENT_ALIGNMENT] via a [Stack]. The icon is always present.
     */
    private fun iconCell(dyeId: String, bgTint: Int, cornerBadge: Widget?): Widget {
        val box = iconBox(dyeId, bgTint)
        if (cornerBadge == null) return box
        return Stack(
            listOf(
                StackChild(box, Alignment.TOP_LEFT),
                StackChild(cornerBadge, CORNER_PERCENT_ALIGNMENT),
            ),
        )
    }

    /** The small percent badge overlaid on the icon box when `percentInIconCorner` is set. */
    private fun cornerPercentBadge(percent: String, accent: Int): Widget =
        TextWidget(percent, color = accent, scale = CORNER_PERCENT_SCALE, alignment = HorizontalAlignment.END)

    /** Assemble the card row from the always-present [icon] and a (possibly empty) [infoChildren]. */
    private fun cardRow(icon: Widget, infoChildren: List<Widget>): List<Widget> =
        if (infoChildren.isEmpty()) listOf(icon) else listOf(icon, infoColumn(infoChildren))

    /** A left-aligned info column with [ROW_GAP] spacing (the right-hand side of the card). */
    private fun infoColumn(children: List<Widget>): Widget =
        Column(children = children, spacing = ROW_GAP, crossAxisAlignment = HorizontalAlignment.START)

    private fun hintPanel(cfg: DyeProgressWidgetConfig, hint: String, hintColor: Int): Widget {
        val infoChildren = buildList {
            if (cfg.showName) {
                add(TextWidget(humanizeDyeId(cfg.dyeId), alignment = HorizontalAlignment.START))
            }
            if (cfg.showSource) {
                add(TextWidget(hint, color = hintColor, scale = SECONDARY_TEXT_SCALE, alignment = HorizontalAlignment.START))
            }
        }
        val icon = iconCell(cfg.dyeId, UiTheme.Colors.ICON_BOX_TINT, cornerBadge = null)
        return card(cardRow(icon, infoChildren), cardChrome(cfg.showBackground, cfg.showBorder))
    }

    private fun notTrackablePanel(cfg: DyeProgressWidgetConfig, entry: DyeProgressEntry?): Widget {
        val infoChildren = buildList {
            if (cfg.showName) {
                add(TextWidget(nameOf(cfg, entry), alignment = HorizontalAlignment.START))
            }
            if (cfg.showPercent) {
                add(TextWidget(NA_TEXT, color = UiTheme.Colors.TEXT_SECONDARY, alignment = HorizontalAlignment.START))
            }
            if (cfg.showSource) {
                add(
                    TextWidget(
                        NOT_TRACKABLE_TEXT,
                        color = UiTheme.Colors.TEXT_SECONDARY,
                        scale = SECONDARY_TEXT_SCALE,
                        alignment = HorizontalAlignment.START,
                    ),
                )
            }
        }
        val icon = iconCell(cfg.dyeId, iconTintFor(entry), cornerBadge = null)
        return card(cardRow(icon, infoChildren), cardChrome(cfg.showBackground, cfg.showBorder))
    }

    private fun readyPanel(cfg: DyeProgressWidgetConfig, entry: DyeProgressEntry): Widget {
        val accent = parseAccentColor(entry.color)
        val progress = entry.progress ?: 0.0
        val isComplete = progress >= COMPLETE_THRESHOLD

        val name = nameOf(cfg, entry)
        val percent = formatProgress(entry.progress)
        val sourceText = if (isComplete) COMPLETE_TEXT else (entry.source ?: entry.formula).orEmpty()

        val layout = readyLayout(cfg, hasSource = sourceText.isNotBlank())

        // Column width: spans the widest present piece so the header pins to the bar's edges.
        val nameWidth = if (layout.nameInHeader) TextWidget(name).measure().width else 0
        val percentWidth = if (layout.percentInHeader) TextWidget(percent).measure().width else 0
        val headerWidth = if (layout.nameInHeader && layout.percentInHeader) {
            nameWidth + HEADER_GAP + percentWidth
        } else {
            maxOf(nameWidth, percentWidth)
        }
        val sourceWidth = if (layout.showSource) TextWidget(sourceText, scale = SECONDARY_TEXT_SCALE).measure().width else 0
        val colWidth = buildList {
            if (layout.hasHeader) add(headerWidth)
            if (layout.showSource) add(sourceWidth)
            if (layout.showBar) add(MIN_BAR_WIDTH)
        }.maxOrNull() ?: 0

        val infoChildren = buildList {
            if (layout.hasHeader) add(header(layout, name, percent, accent, colWidth))
            if (layout.showBar) {
                add(ProgressBarWidget(value = progress.toFloat(), width = colWidth, height = BAR_HEIGHT, fillColor = accent))
            }
            if (layout.showSource) {
                add(
                    TextWidget(
                        sourceText,
                        color = if (isComplete) accent else UiTheme.Colors.TEXT_SECONDARY,
                        scale = SECONDARY_TEXT_SCALE,
                        alignment = HorizontalAlignment.START,
                    ),
                )
            }
        }

        val cornerBadge = if (layout.percentInCorner) cornerPercentBadge(percent, accent) else null
        val icon = iconCell(cfg.dyeId, iconTint(accent), cornerBadge)
        return card(cardRow(icon, infoChildren), cardChrome(cfg.showBackground, cfg.showBorder))
    }

    /**
     * The header row for the READY state: name pinned left and percent pinned right across
     * [colWidth] when both are present (a [Stack]); just the present one (aligned to its edge)
     * when only one is. Caller guarantees at least one of name/percent is in the header.
     */
    private fun header(layout: ReadyLayout, name: String, percent: String, accent: Int, colWidth: Int): Widget = when {
        layout.nameInHeader && layout.percentInHeader -> Stack(
            listOf(
                StackChild(TextWidget(name, minWidth = colWidth, alignment = HorizontalAlignment.START)),
                StackChild(TextWidget(percent, color = accent, minWidth = colWidth, alignment = HorizontalAlignment.END)),
            ),
        )

        layout.nameInHeader -> TextWidget(name, minWidth = colWidth, alignment = HorizontalAlignment.START)
        else -> TextWidget(percent, color = accent, minWidth = colWidth, alignment = HorizontalAlignment.END)
    }
}
