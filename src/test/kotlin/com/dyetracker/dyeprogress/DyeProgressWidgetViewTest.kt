package com.dyetracker.dyeprogress

import com.dyetracker.api.DyeProgressEntry
import com.dyetracker.dyeprogress.DyeProgressWidgetView.RenderState
import com.dyetracker.ui.theme.UiTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for the pure helpers in [DyeProgressWidgetView] (task 34-5): percent
 * formatting, render-state selection, accent-color parsing, and dye-id humanizing. The composed
 * widget tree needs a live `DrawContext`/`TextRenderer` and is verified in-client in 34-8.
 */
class DyeProgressWidgetViewTest {

    private fun entry(progress: Double?, color: String = "#e225f4") = DyeProgressEntry(
        dyeId = "matcha",
        name = "Matcha Dye",
        color = color,
        category = "Slayer",
        progress = progress,
        trackable = progress != null,
    )

    // --- formatProgress -----------------------------------------------------

    @Test
    fun `formatProgress shows N slash A for null`() {
        assertEquals("N/A", DyeProgressWidgetView.formatProgress(null))
    }

    @Test
    fun `formatProgress shows 100 percent at or above 100`() {
        assertEquals("100%", DyeProgressWidgetView.formatProgress(100.0))
        assertEquals("100%", DyeProgressWidgetView.formatProgress(137.5))
    }

    @Test
    fun `formatProgress shows one decimal below 100`() {
        assertEquals("42.0%", DyeProgressWidgetView.formatProgress(42.0))
        assertEquals("3.1%", DyeProgressWidgetView.formatProgress(3.14159))
        assertEquals("0.0%", DyeProgressWidgetView.formatProgress(0.0))
    }

    // --- selectState --------------------------------------------------------

    @Test
    fun `loading status selects LOADING`() {
        assertEquals(RenderState.LOADING, DyeProgressWidgetView.selectState(DyeProgressStore.Status.LOADING, null))
    }

    @Test
    fun `ok with a trackable entry selects READY`() {
        assertEquals(RenderState.READY, DyeProgressWidgetView.selectState(DyeProgressStore.Status.OK, entry(42.0)))
    }

    @Test
    fun `ok with a null-progress entry selects NOT_TRACKABLE`() {
        assertEquals(
            RenderState.NOT_TRACKABLE,
            DyeProgressWidgetView.selectState(DyeProgressStore.Status.OK, entry(null)),
        )
    }

    @Test
    fun `ok with a missing entry selects NOT_TRACKABLE`() {
        assertEquals(RenderState.NOT_TRACKABLE, DyeProgressWidgetView.selectState(DyeProgressStore.Status.OK, null))
    }

    @Test
    fun `error with prior trackable data selects READY (last known)`() {
        assertEquals(RenderState.READY, DyeProgressWidgetView.selectState(DyeProgressStore.Status.ERROR, entry(42.0)))
    }

    @Test
    fun `error with no prior data selects UNAVAILABLE`() {
        assertEquals(RenderState.UNAVAILABLE, DyeProgressWidgetView.selectState(DyeProgressStore.Status.ERROR, null))
    }

    @Test
    fun `error with prior null-progress entry selects NOT_TRACKABLE`() {
        assertEquals(
            RenderState.NOT_TRACKABLE,
            DyeProgressWidgetView.selectState(DyeProgressStore.Status.ERROR, entry(null)),
        )
    }

    // --- parseAccentColor ---------------------------------------------------

    @Test
    fun `parseAccentColor parses a 6-digit hex with full opacity`() {
        assertEquals(0xFFE225F4.toInt(), DyeProgressWidgetView.parseAccentColor("#e225f4"))
        assertEquals(0xFFE225F4.toInt(), DyeProgressWidgetView.parseAccentColor("e225f4"))
    }

    @Test
    fun `parseAccentColor parses an 8-digit hex`() {
        assertEquals(0x80FF0000.toInt(), DyeProgressWidgetView.parseAccentColor("80ff0000"))
    }

    @Test
    fun `parseAccentColor is case-insensitive and trims whitespace`() {
        assertEquals(0xFFE225F4.toInt(), DyeProgressWidgetView.parseAccentColor("#E225F4"))
        assertEquals(0xFFE225F4.toInt(), DyeProgressWidgetView.parseAccentColor("# e225f4 "))
    }

    @Test
    fun `parseAccentColor falls back on null or bad input`() {
        assertEquals(UiTheme.Colors.PROGRESS_FILL, DyeProgressWidgetView.parseAccentColor(null))
        assertEquals(UiTheme.Colors.PROGRESS_FILL, DyeProgressWidgetView.parseAccentColor(""))
        assertEquals(UiTheme.Colors.PROGRESS_FILL, DyeProgressWidgetView.parseAccentColor("not-a-color"))
        assertEquals(UiTheme.Colors.PROGRESS_FILL, DyeProgressWidgetView.parseAccentColor("#abc"))
    }

    // --- humanizeDyeId ------------------------------------------------------

    @Test
    fun `humanizeDyeId title-cases snake_case ids`() {
        assertEquals("Wild Strawberry", DyeProgressWidgetView.humanizeDyeId("wild_strawberry"))
        assertEquals("Matcha", DyeProgressWidgetView.humanizeDyeId("matcha"))
        assertEquals("Pure Black", DyeProgressWidgetView.humanizeDyeId("pure_black"))
    }
}
