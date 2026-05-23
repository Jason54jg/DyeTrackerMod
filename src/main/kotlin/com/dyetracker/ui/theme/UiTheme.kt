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

        /** Dye-rotation 2x boost badge (green), seeded from the external dyes browser source. */
        const val BOOST_2X: Int = 0xFF_43_B5_81.toInt()

        /** Dye-rotation 3x boost badge (gold), seeded from the external dyes browser source. */
        const val BOOST_3X: Int = 0xFF_FA_A6_1A.toInt()

        /** Translucent wash over a hidden (visibility-off) widget. */
        const val HIDDEN_DIM: Int = 0xB0_00_00_00.toInt()

        /** Strikethrough bar drawn across a hidden widget. */
        const val STRIKETHROUGH: Int = 0xCC_FF_55_55.toInt()

        /** Progress-bar track (unfilled portion); a subtle translucent white, seeded from the website overlay's `white/5` track. */
        const val PROGRESS_TRACK: Int = 0x33_FF_FF_FF.toInt()

        /** Default progress-bar fill; seeded from the website single-dye overlay's default accent (`#e225f4`). Callers may override per dye color. */
        const val PROGRESS_FILL: Int = 0xFF_E2_25_F4.toInt()

        /** Translucent dark-navy card fill, mirroring the website single-dye overlay's card. */
        const val CARD_BACKGROUND: Int = 0xD8_14_16_22.toInt()

        /** Subtle bluish card border, mirroring the website overlay card's faint edge. */
        const val CARD_BORDER: Int = 0x40_3A_40_5A.toInt()

        /** Faint border around an icon box, mirroring the website overlay icon's `border-white/10`. */
        const val ICON_BOX_BORDER: Int = 0x1A_FF_FF_FF.toInt()

        /** Neutral (no-accent) icon-box tint, used when no dye accent color is available. */
        const val ICON_BOX_TINT: Int = 0x14_FF_FF_FF.toInt()
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

        /** Default progress-bar track/fill height in GUI pixels (pre-scale). */
        const val PROGRESS_BAR_HEIGHT: Int = 4
    }
}
