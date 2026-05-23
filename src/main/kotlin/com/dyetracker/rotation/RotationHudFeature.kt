package com.dyetracker.rotation

import com.dyetracker.config.ConfigManager
import com.dyetracker.data.DyeRotation
import com.dyetracker.ui.components.PanelWidget
import com.dyetracker.ui.components.SpriteWidget
import com.dyetracker.ui.components.TextWidget
import com.dyetracker.ui.core.HorizontalAlignment
import com.dyetracker.ui.core.Widget
import com.dyetracker.ui.hud.HudWidgetEntry
import com.dyetracker.ui.hud.HudWidgetRegistry
import com.dyetracker.ui.layout.Row
import com.dyetracker.ui.layout.Column
import com.dyetracker.ui.theme.UiTheme

/**
 * Wires the dye-rotation widget into the UI toolkit. Registers a [HudWidgetRegistry] provider that,
 * each frame, reads the current [DyeRotation] (from [ConfigManager], populated by the 31-4 capture
 * and persisted across restarts) and composes a widget tree from PBI 30 components — a [PanelWidget]
 * hosting a [Row] of dye [SpriteWidget] tiles (styled after the external dyes browser source), with
 * an optional per-tile boost badge. Before the first capture the widget is hidden entirely (the
 * provider contributes no entry), so a fresh login shows nothing until a rotation is captured.
 *
 * The single [RotationWidgetConfig] placement is supplied to the host/edit-screen via
 * [RotationPlacementEditor], so the widget gets drag-to-move, scroll-to-scale, visibility toggle,
 * and persisted placement "for free".
 */
object RotationHudFeature {

    /** Bundled dye sprites are 300×300 (see [DyeSprites] / docs/delivery/31/sprites.md). */
    private const val SPRITE_SOURCE_PX = 300

    /** On-screen size of each dye tile at placement scale 1.0 (matches the browser source default). */
    private const val TILE_TARGET_PX = 64

    /** Sprite scale that renders a [SPRITE_SOURCE_PX] sprite at [TILE_TARGET_PX]. */
    private const val TILE_SPRITE_SCALE = TILE_TARGET_PX.toFloat() / SPRITE_SOURCE_PX

    private const val BOOST_2X_MULTIPLIER = 2
    private const val BOOST_3X_MULTIPLIER = 3

    /**
     * Seed the placement (so edit mode always has a target once a rotation exists) and register the
     * HUD provider. Call once at client init. Seeding persists only on the first ever run; later
     * runs find the placement already present.
     */
    fun register() {
        if (ConfigManager.rotationPlacements.all().isEmpty()) {
            ConfigManager.rotationPlacements.add(RotationWidgetConfig())
        }
        HudWidgetRegistry.register {
            // Hide until first capture: render nothing when there is no rotation, so the widget
            // never shows a placeholder on a fresh login. (To instead show a discoverable hint,
            // return one entry with a hint widget here.)
            val rotation = ConfigManager.getDyeRotation()
            if (rotation == null || rotation.isEmpty()) {
                emptyList()
            } else {
                val placement = ConfigManager.rotationPlacements.all().firstOrNull() ?: RotationWidgetConfig()
                listOf(HudWidgetEntry(placement, rotationWidget(rotation), RotationPlacementEditor))
            }
        }
    }

    private fun rotationWidget(rotation: DyeRotation): Widget {
        val tiles = rotation.dyeIds.map { dyeId -> tile(dyeId, rotation.boosts?.get(dyeId)) }
        return PanelWidget(child = Row(children = tiles, spacing = UiTheme.Spacing.INNER_GAP))
    }

    private fun tile(dyeId: String, boost: Int?): Widget {
        val sprite = SpriteWidget(DyeSprites.spriteId(dyeId), TILE_SPRITE_SCALE)
        if (boost == null) return sprite
        val badge = TextWidget(
            text = "${boost}x",
            color = boostColor(boost),
            alignment = HorizontalAlignment.CENTER,
        )
        return Column(
            children = listOf(sprite, badge),
            spacing = UiTheme.Spacing.INNER_GAP,
            crossAxisAlignment = HorizontalAlignment.CENTER,
        )
    }

    private fun boostColor(boost: Int): Int = when (boost) {
        BOOST_2X_MULTIPLIER -> UiTheme.Colors.BOOST_2X
        BOOST_3X_MULTIPLIER -> UiTheme.Colors.BOOST_3X
        else -> UiTheme.Colors.TEXT_PRIMARY
    }
}
