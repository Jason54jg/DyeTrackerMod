package com.dyetracker.ui.edit

import com.dyetracker.ui.core.RenderContext
import com.dyetracker.ui.core.Size
import com.dyetracker.ui.core.Widget
import com.dyetracker.ui.core.WidgetPlacement
import com.dyetracker.ui.hud.HudWidgetEntry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit coverage for the pure [hasConfigPanel] eligibility helper (task 36-4) — the only part of
 * the focused-widget config hook testable without a live client (activation, rendering, and the
 * `C`-key trigger are exercised in-client in 36-6).
 */
class WidgetConfigPanelTest {

    private object FakeWidget : Widget {
        override fun measure(): Size = Size.ZERO
        override fun draw(ctx: RenderContext, x: Int, y: Int) {}
    }

    private object FakePlacement : WidgetPlacement {
        override val id = "w1"
        override val x = 0.5f
        override val y = 0.5f
        override val scale = 1.0f
        override val visible = true
    }

    private object FakeConfigPanel : WidgetConfigPanel {
        override fun onActivate(screen: WidgetEditScreen, widgetId: String) {}
        override fun onDismiss() {}
    }

    private fun entry(configPanel: WidgetConfigPanel?) =
        HudWidgetEntry(placement = FakePlacement, widget = FakeWidget, configPanel = configPanel)

    @Test
    fun `hasConfigPanel is true when the entry supplies a panel`() {
        assertTrue(hasConfigPanel(entry(FakeConfigPanel)))
    }

    @Test
    fun `hasConfigPanel is false when the entry has no panel`() {
        assertFalse(hasConfigPanel(entry(null)))
    }

    @Test
    fun `hasConfigPanel is false for a null entry`() {
        assertFalse(hasConfigPanel(null))
    }
}
