package com.dyetracker.ui.theme

/**
 * Central style tokens for the in-game UI toolkit. Components and the edit-mode screen
 * reference these named tokens instead of inlining raw ARGB / pixel literals, so the
 * toolkit shares one coherent look and values live in a single place (project
 * named-constants + DRY rules).
 *
 * Values are seeded from the GIF overlay edit screen's original constants so adopting
 * these tokens preserves the existing appearance. Tokens are intentionally distinct even
 * when two currently share a value (e.g. several whites) so each can be re-themed
 * independently later.
 */
object UiTheme {

    /** ARGB color tokens (0xAARRGGBB). */
    object Colors {
        /** Dim wash painted behind edit-mode content. */
        const val BACKDROP: Int = 0x80_00_00_00.toInt()

        /** Default panel/background fill. */
        const val PANEL_BACKGROUND: Int = 0xC8_10_10_10.toInt()

        /** Default 1px panel border. */
        const val PANEL_BORDER: Int = 0xFF_55_55_55.toInt()

        /** Border drawn around the focused widget in edit mode. */
        const val FOCUS_BORDER: Int = 0xFF_FF_FF_FF.toInt()

        /** Primary (high-emphasis) text. */
        const val TEXT_PRIMARY: Int = 0xFF_FF_FF_FF.toInt()

        /** Secondary (low-emphasis) text. */
        const val TEXT_SECONDARY: Int = 0xFF_BF_BF_BF.toInt()

        /** Success/neutral status text. */
        const val STATUS_OK: Int = 0xFF_BF_BF_BF.toInt()

        /** Error status text. */
        const val STATUS_ERROR: Int = 0xFF_FF_55_55.toInt()

        /** Translucent wash over a hidden (visibility-off) widget. */
        const val HIDDEN_DIM: Int = 0xB0_00_00_00.toInt()

        /** Strikethrough bar drawn across a hidden widget. */
        const val STRIKETHROUGH: Int = 0xCC_FF_55_55.toInt()
    }

    /** Spacing tokens in GUI pixels. */
    object Spacing {
        /** Standard inner padding for panels/controls. */
        const val PADDING: Int = 8

        /** Gap between stacked inner elements. */
        const val INNER_GAP: Int = 6

        /** Outer margin from screen edges. */
        const val MARGIN: Int = 6
    }

    /** Sizing tokens. */
    object Sizing {
        /** Default border thickness in GUI pixels. */
        const val BORDER_WIDTH: Int = 1

        /** Default font scale multiplier (1.0 = vanilla size). */
        const val FONT_SCALE: Float = 1.0f

        /** Standard interactive control (button/field) height in GUI pixels. */
        const val CONTROL_HEIGHT: Int = 20
    }
}
