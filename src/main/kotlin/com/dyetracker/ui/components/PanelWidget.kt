package com.dyetracker.ui.components

import com.dyetracker.ui.core.Insets
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.Size
import com.dyetracker.ui.core.UiDraw
import com.dyetracker.ui.core.Widget
import com.dyetracker.ui.theme.UiTheme

/**
 * A background container: a solid fill plus an optional 1px border, optionally wrapping a
 * single [child] widget drawn inside [padding].
 *
 * Design choice: the panel hosts at most one child (use a layout container from the layout
 * package as that child to host many). Intrinsic size = child size + padding, or
 * [fixedContentSize] + padding when there is no child (a plain sized backdrop). Pass
 * `border = null` for a borderless fill.
 */
class PanelWidget(
    private val child: Widget? = null,
    private val padding: Insets = Insets.all(UiTheme.Spacing.PADDING),
    private val background: Int = UiTheme.Colors.PANEL_BACKGROUND,
    private val border: Int? = UiTheme.Colors.PANEL_BORDER,
    private val borderWidth: Int = UiTheme.Sizing.BORDER_WIDTH,
    private val fixedContentSize: Size? = null,
) : Widget {

    private fun contentSize(): Size = fixedContentSize ?: child?.measure() ?: Size.ZERO

    override fun measure(): Size {
        val content = contentSize()
        return Size(content.width + padding.horizontal, content.height + padding.vertical)
    }

    override fun draw(ctx: RenderContext, x: Int, y: Int) {
        val size = measure()
        if (border != null) {
            UiDraw.filledBox(ctx.drawContext, x, y, size.width, size.height, background, border, borderWidth)
        } else {
            UiDraw.fillRect(ctx.drawContext, x, y, size.width, size.height, background)
        }
        child?.draw(ctx, x + padding.left, y + padding.top)
    }
}
