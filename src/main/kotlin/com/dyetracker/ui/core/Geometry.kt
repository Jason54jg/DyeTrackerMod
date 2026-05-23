package com.dyetracker.ui.core

/**
 * Small, immutable geometry value types shared across the in-game UI toolkit. All units
 * are GUI pixels in the *pre-scale* (intrinsic) coordinate space — the HUD host applies
 * any placement scale via a matrix transform, so widgets never need to reason about it.
 */

/** Intrinsic widget size in GUI pixels. */
data class Size(val width: Int, val height: Int) {
    companion object {
        val ZERO = Size(0, 0)
    }
}

/** Edge insets (padding) in GUI pixels. */
data class Insets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    /** Combined left + right inset. */
    val horizontal: Int get() = left + right

    /** Combined top + bottom inset. */
    val vertical: Int get() = top + bottom

    companion object {
        val ZERO = Insets(0, 0, 0, 0)

        /** Uniform inset on all four edges. */
        fun all(value: Int) = Insets(value, value, value, value)

        /** Symmetric inset: [horizontal] on left/right, [vertical] on top/bottom. */
        fun symmetric(horizontal: Int, vertical: Int) =
            Insets(horizontal, vertical, horizontal, vertical)
    }
}

/** Horizontal placement of a child within an available width. */
enum class HorizontalAlignment { START, CENTER, END }

/** Vertical placement of a child within an available height. */
enum class VerticalAlignment { TOP, CENTER, BOTTOM }

/**
 * Combined 2D alignment with helpers that resolve the pixel offset needed to place a
 * child of a given size inside an available box.
 */
data class Alignment(
    val horizontal: HorizontalAlignment,
    val vertical: VerticalAlignment,
) {
    /** Left offset to align a [childWidth]-wide child inside [availableWidth]. */
    fun offsetX(availableWidth: Int, childWidth: Int): Int = horizontal.offset(availableWidth, childWidth)

    /** Top offset to align a [childHeight]-tall child inside [availableHeight]. */
    fun offsetY(availableHeight: Int, childHeight: Int): Int = vertical.offset(availableHeight, childHeight)

    companion object {
        val TOP_LEFT = Alignment(HorizontalAlignment.START, VerticalAlignment.TOP)
        val TOP_CENTER = Alignment(HorizontalAlignment.CENTER, VerticalAlignment.TOP)
        val CENTER = Alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER)
        val BOTTOM_CENTER = Alignment(HorizontalAlignment.CENTER, VerticalAlignment.BOTTOM)
    }
}

/** Offset for a [childExtent] placed inside [available] along this horizontal axis. */
fun HorizontalAlignment.offset(available: Int, childExtent: Int): Int = when (this) {
    HorizontalAlignment.START -> 0
    HorizontalAlignment.CENTER -> (available - childExtent) / 2
    HorizontalAlignment.END -> available - childExtent
}

/** Offset for a [childExtent] placed inside [available] along this vertical axis. */
fun VerticalAlignment.offset(available: Int, childExtent: Int): Int = when (this) {
    VerticalAlignment.TOP -> 0
    VerticalAlignment.CENTER -> (available - childExtent) / 2
    VerticalAlignment.BOTTOM -> available - childExtent
}

/**
 * An axis-aligned rectangle in GUI pixels. [contains] is inclusive of all edges, which
 * suits mouse hit-testing where the cursor is a continuous coordinate (matching the GIF
 * edit-mode hit test it replaces).
 */
data class Rect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    fun contains(px: Int, py: Int): Boolean =
        px in x..right && py in y..bottom

    fun contains(px: Double, py: Double): Boolean =
        px >= x && px <= right && py >= y && py <= bottom
}
