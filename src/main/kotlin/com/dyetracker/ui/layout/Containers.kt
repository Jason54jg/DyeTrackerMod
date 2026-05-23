package com.dyetracker.ui.layout

import com.dyetracker.ui.core.Alignment
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.Insets
import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.Size
import com.dyetracker.ui.core.VerticalAlignment
import com.dyetracker.ui.core.Widget
import com.dyetracker.ui.core.offset
import kotlin.math.max

/**
 * Layout containers that compose child [Widget]s into structured widgets. All three measure
 * their children, apply [padding] and (for Row/Column) inter-child [spacing], and position
 * each child at an absolute coordinate during draw. Containers compose recursively — a
 * container may contain other containers.
 *
 * Children keep their intrinsic size (no cross-axis stretch); cross-axis placement is by
 * alignment. Measurement is pure: each container re-measures its children, so there is no
 * cached layout state to invalidate.
 */

/**
 * Horizontal container: lays children left-to-right with [spacing] between them, aligning
 * each within the row's content height via [crossAxisAlignment] (vertical).
 */
class Row(
    private val children: List<Widget>,
    private val spacing: Int = 0,
    private val padding: Insets = Insets.ZERO,
    private val crossAxisAlignment: VerticalAlignment = VerticalAlignment.CENTER,
) : Widget {

    override fun measure(): Size {
        if (children.isEmpty()) return Size(padding.horizontal, padding.vertical)
        var main = 0
        var cross = 0
        for (child in children) {
            val s = child.measure()
            main += s.width
            cross = max(cross, s.height)
        }
        main += spacing * (children.size - 1)
        return Size(main + padding.horizontal, cross + padding.vertical)
    }

    override fun draw(ctx: RenderContext, x: Int, y: Int) {
        if (children.isEmpty()) return
        val sizes = children.map { it.measure() }
        val crossExtent = sizes.maxOf { it.height }
        val contentLeft = x + padding.left
        val contentTop = y + padding.top
        var curX = contentLeft
        for (i in children.indices) {
            val s = sizes[i]
            children[i].draw(ctx, curX, contentTop + crossAxisAlignment.offset(crossExtent, s.height))
            curX += s.width + spacing
        }
    }
}

/**
 * Vertical container: lays children top-to-bottom with [spacing] between them, aligning each
 * within the column's content width via [crossAxisAlignment] (horizontal).
 */
class Column(
    private val children: List<Widget>,
    private val spacing: Int = 0,
    private val padding: Insets = Insets.ZERO,
    private val crossAxisAlignment: HorizontalAlignment = HorizontalAlignment.CENTER,
) : Widget {

    override fun measure(): Size {
        if (children.isEmpty()) return Size(padding.horizontal, padding.vertical)
        var main = 0
        var cross = 0
        for (child in children) {
            val s = child.measure()
            main += s.height
            cross = max(cross, s.width)
        }
        main += spacing * (children.size - 1)
        return Size(cross + padding.horizontal, main + padding.vertical)
    }

    override fun draw(ctx: RenderContext, x: Int, y: Int) {
        if (children.isEmpty()) return
        val sizes = children.map { it.measure() }
        val crossExtent = sizes.maxOf { it.width }
        val contentLeft = x + padding.left
        val contentTop = y + padding.top
        var curY = contentTop
        for (i in children.indices) {
            val s = sizes[i]
            children[i].draw(ctx, contentLeft + crossAxisAlignment.offset(crossExtent, s.width), curY)
            curY += s.height + spacing
        }
    }
}

/** A [Stack] child paired with how it is aligned within the stack's bounds. */
data class StackChild(val widget: Widget, val alignment: Alignment = Alignment.TOP_LEFT)

/**
 * Overlay (z-stack) container: draws children in order at a shared origin, each positioned
 * within the stack's bounds by its own [StackChild.alignment]. Later children draw on top.
 * Intrinsic size is the max of all children's sizes, plus [padding].
 */
class Stack(
    private val children: List<StackChild>,
    private val padding: Insets = Insets.ZERO,
) : Widget {

    override fun measure(): Size {
        if (children.isEmpty()) return Size(padding.horizontal, padding.vertical)
        val sizes = children.map { it.widget.measure() }
        return Size(sizes.maxOf { it.width } + padding.horizontal, sizes.maxOf { it.height } + padding.vertical)
    }

    override fun draw(ctx: RenderContext, x: Int, y: Int) {
        if (children.isEmpty()) return
        val sizes = children.map { it.widget.measure() }
        val w = sizes.maxOf { it.width }
        val h = sizes.maxOf { it.height }
        val contentLeft = x + padding.left
        val contentTop = y + padding.top
        for (i in children.indices) {
            val child = children[i]
            val s = sizes[i]
            child.widget.draw(
                ctx,
                contentLeft + child.alignment.offsetX(w, s.width),
                contentTop + child.alignment.offsetY(h, s.height),
            )
        }
    }
}
