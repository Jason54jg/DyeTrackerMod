package com.dyetracker.dyeprogress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit coverage for the pure persist helpers behind the dye-progress config panel (task 36-5):
 * [pieceValue] (checkbox init) and [applyPieceToggle] (the `copy` transform persisted on toggle).
 * Each piece must map to exactly its own config field; the panel's checkbox/IO wiring is verified
 * in-client in 36-6.
 */
class DyeProgressConfigPanelTest {

    private val base = DyeProgressWidgetConfig(
        id = "w1",
        dyeId = "matcha",
        profileName = "Mango",
        profileId = "p1",
    )

    @Test
    fun `pieceValue reads each toggle from its own field`() {
        val cfg = base.copy(
            showBackground = false,
            showBorder = true,
            showProgressBar = false,
            showName = true,
            showSource = false,
            showPercent = true,
            percentInIconCorner = true,
        )
        assertFalse(pieceValue(cfg, DyeProgressPiece.BACKGROUND))
        assertTrue(pieceValue(cfg, DyeProgressPiece.BORDER))
        assertFalse(pieceValue(cfg, DyeProgressPiece.PROGRESS_BAR))
        assertTrue(pieceValue(cfg, DyeProgressPiece.NAME))
        assertFalse(pieceValue(cfg, DyeProgressPiece.SOURCE))
        assertTrue(pieceValue(cfg, DyeProgressPiece.PERCENT))
        assertTrue(pieceValue(cfg, DyeProgressPiece.PERCENT_IN_CORNER))
    }

    @Test
    fun `applyPieceToggle sets background only`() {
        val off = applyPieceToggle(base, DyeProgressPiece.BACKGROUND, false)
        assertFalse(off.showBackground)
        assertEquals(base.copy(showBackground = false), off)
    }

    @Test
    fun `applyPieceToggle sets border only`() {
        val off = applyPieceToggle(base, DyeProgressPiece.BORDER, false)
        assertFalse(off.showBorder)
        assertEquals(base.copy(showBorder = false), off)
    }

    @Test
    fun `applyPieceToggle sets progress bar only`() {
        val off = applyPieceToggle(base, DyeProgressPiece.PROGRESS_BAR, false)
        assertFalse(off.showProgressBar)
        assertEquals(base.copy(showProgressBar = false), off)
    }

    @Test
    fun `applyPieceToggle sets name only`() {
        val off = applyPieceToggle(base, DyeProgressPiece.NAME, false)
        assertFalse(off.showName)
        assertEquals(base.copy(showName = false), off)
    }

    @Test
    fun `applyPieceToggle sets source only`() {
        val off = applyPieceToggle(base, DyeProgressPiece.SOURCE, false)
        assertFalse(off.showSource)
        assertEquals(base.copy(showSource = false), off)
    }

    @Test
    fun `applyPieceToggle sets percent only`() {
        val off = applyPieceToggle(base, DyeProgressPiece.PERCENT, false)
        assertFalse(off.showPercent)
        assertEquals(base.copy(showPercent = false), off)
    }

    @Test
    fun `applyPieceToggle sets percent-in-corner only`() {
        val on = applyPieceToggle(base, DyeProgressPiece.PERCENT_IN_CORNER, true)
        assertTrue(on.percentInIconCorner)
        assertEquals(base.copy(percentInIconCorner = true), on)
    }

    @Test
    fun `applyPieceToggle leaves placement and identity fields untouched`() {
        val toggled = applyPieceToggle(base, DyeProgressPiece.PERCENT, false)
        assertEquals(base.id, toggled.id)
        assertEquals(base.dyeId, toggled.dyeId)
        assertEquals(base.profileId, toggled.profileId)
        assertEquals(base.x, toggled.x)
        assertEquals(base.scale, toggled.scale)
        assertEquals(base.visible, toggled.visible)
    }
}
