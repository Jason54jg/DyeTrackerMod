package com.dyetracker.ui.components

import com.dyetracker.ui.core.Size
import com.dyetracker.ui.theme.UiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure-JVM coverage for [ProgressBarWidget]'s measurement and the value→fill-width math.
 * Draw output itself needs a live `DrawContext` and is verified in the 34-8 E2E task.
 */
class ProgressBarWidgetTest {

    @Test
    fun `measure returns the configured size regardless of value`() {
        val width = 80
        val height = 5
        for (value in listOf(-10f, 0f, 37f, 100f, 250f)) {
            val bar = ProgressBarWidget(value = value, width = width, height = height)
            assertEquals(Size(width, height), bar.measure())
        }
    }

    @Test
    fun `measure uses the default theme height when none is given`() {
        val bar = ProgressBarWidget(value = 50f, width = 80)
        assertEquals(Size(80, UiTheme.Sizing.PROGRESS_BAR_HEIGHT), bar.measure())
    }

    @Test
    fun `fill width is zero at or below zero percent`() {
        assertEquals(0, ProgressBarWidget.fillWidth(0f, 80))
        assertEquals(0, ProgressBarWidget.fillWidth(-10f, 80))
    }

    @Test
    fun `fill width is half the track at fifty percent`() {
        assertEquals(40, ProgressBarWidget.fillWidth(50f, 80))
    }

    @Test
    fun `fill width is the full track at one hundred percent`() {
        assertEquals(80, ProgressBarWidget.fillWidth(100f, 80))
    }

    @Test
    fun `fill width clamps over-range values to the full track`() {
        assertEquals(80, ProgressBarWidget.fillWidth(150f, 80))
    }

    @Test
    fun `fill width rounds to the nearest pixel`() {
        // 33% of 80 = 26.4 -> 26
        assertEquals(26, ProgressBarWidget.fillWidth(33f, 80))
        // 34% of 80 = 27.2 -> 27
        assertEquals(27, ProgressBarWidget.fillWidth(34f, 80))
    }

    @Test
    fun `fill width is zero for a non-positive track width`() {
        assertEquals(0, ProgressBarWidget.fillWidth(50f, 0))
        assertEquals(0, ProgressBarWidget.fillWidth(50f, -20))
    }
}
