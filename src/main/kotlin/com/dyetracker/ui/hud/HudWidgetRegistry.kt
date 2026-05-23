package com.dyetracker.ui.hud

import com.dyetracker.ui.core.PlacementEditor
import com.dyetracker.ui.core.Widget
import com.dyetracker.ui.core.WidgetPlacement
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One HUD widget to draw this frame: its live [placement] (center/scale/visibility) and the
 * [widget] to render. The entry id comes from the placement so it stays in sync with the
 * widget's texture/persistence id.
 *
 * [editor] is the optional write side: when non-null, the widget can be moved/scaled/hidden/
 * removed in the generalized edit screen. Render-only widgets leave it null. The HUD host
 * ignores it; only the edit screen uses it.
 */
class HudWidgetEntry(
    val placement: WidgetPlacement,
    val widget: Widget,
    val editor: PlacementEditor? = null,
) {
    val id: String get() = placement.id
}

/**
 * A feature's contribution of HUD widgets. Called once per frame by [HudWidgetHost], so a
 * provider backed by a mutable collection (e.g. the GIF overlay list) reflects adds/removes
 * immediately without re-registering.
 */
fun interface HudWidgetProvider {
    fun entries(): List<HudWidgetEntry>
}

/**
 * Registry of HUD widget providers. Features register a [HudWidgetProvider]; the host pulls
 * the flattened entry list each frame. Registration is thread-safe (providers are typically
 * registered at client init).
 */
object HudWidgetRegistry {

    private val providers = CopyOnWriteArrayList<HudWidgetProvider>()

    /** Register a [provider] whose entries are drawn by the host every frame. */
    fun register(provider: HudWidgetProvider) {
        providers.add(provider)
    }

    /** All HUD widget entries contributed by every registered provider this frame. */
    fun entries(): List<HudWidgetEntry> = providers.flatMap { it.entries() }
}
