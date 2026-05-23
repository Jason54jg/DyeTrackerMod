package com.dyetracker.dyeprogress

import com.dyetracker.ui.core.WidgetPlacement
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Persisted configuration for one single-dye progress HUD widget (PBI 34). Supports many
 * independent instances (one per chosen dye + profile), mirroring the GIF overlay's
 * multi-instance model rather than the rotation widget's singleton.
 *
 * Implements [WidgetPlacement] so the generic HUD host, edit screen, and placement store can
 * position/scale/hide it. [x] and [y] are fractional center coordinates (0.0–1.0) so the
 * widget survives resolution changes; [scale] is a draw multiplier.
 *
 * The profile is stored twice on purpose: [profileName] is the player-entered `cute_name`
 * (kept for display/editing) and [profileId] is the resolved id the dyes endpoint requires.
 * Volatile progress data is NOT stored here — that lives in the in-memory store (task 34-4).
 *
 * The `show*` flags and [percentInIconCorner] (PBI 36) let each instance toggle individual
 * pieces and choose where the percent renders. Defaults reproduce today's full layout exactly,
 * so configs serialized before PBI 36 deserialize unchanged (kotlinx-serialization fills the
 * missing keys with these defaults). The dye icon has no flag — it is the always-on minimum.
 */
@Serializable
data class DyeProgressWidgetConfig(
    override val id: String,
    val dyeId: String,
    val profileName: String,
    val profileId: String,
    override val x: Float = DEFAULT_X,
    override val y: Float = DEFAULT_Y,
    override val scale: Float = DEFAULT_SCALE,
    override val visible: Boolean = DEFAULT_VISIBLE,
    val showBackground: Boolean = DEFAULT_SHOW_BACKGROUND,
    val showBorder: Boolean = DEFAULT_SHOW_BORDER,
    val showProgressBar: Boolean = DEFAULT_SHOW_PROGRESS_BAR,
    val showName: Boolean = DEFAULT_SHOW_NAME,
    val showSource: Boolean = DEFAULT_SHOW_SOURCE,
    val showPercent: Boolean = DEFAULT_SHOW_PERCENT,
    val percentInIconCorner: Boolean = DEFAULT_PERCENT_IN_ICON_CORNER,
) : WidgetPlacement {
    companion object {
        /** Default fractional center X (screen center). */
        const val DEFAULT_X = 0.5f

        /** Default fractional center Y (screen center). */
        const val DEFAULT_Y = 0.5f

        /** Default draw scale multiplier. */
        const val DEFAULT_SCALE = 1.0f

        /** Newly added widgets are visible by default. */
        const val DEFAULT_VISIBLE = true

        /** Card background fill shown by default (today's appearance). */
        const val DEFAULT_SHOW_BACKGROUND = true

        /** Card border shown by default. */
        const val DEFAULT_SHOW_BORDER = true

        /** Progress bar shown by default. */
        const val DEFAULT_SHOW_PROGRESS_BAR = true

        /** Dye name text shown by default. */
        const val DEFAULT_SHOW_NAME = true

        /** Source/formula line shown by default. */
        const val DEFAULT_SHOW_SOURCE = true

        /** Percent value shown by default. */
        const val DEFAULT_SHOW_PERCENT = true

        /** Percent renders in the header (not the icon corner) by default. */
        const val DEFAULT_PERCENT_IN_ICON_CORNER = false

        private const val ID_LENGTH = 8

        /** Generate a short opaque ID for a new widget (same scheme as the GIF overlay's `newId`). */
        fun newId(): String = UUID.randomUUID().toString().take(ID_LENGTH)
    }
}
