package com.dyetracker.dyeprogress

import com.dyetracker.events.InventoryUtils

/** One selectable dye in the picker: its snake_case [dyeId] and a Title-Case [displayName]. */
data class DyeOption(val dyeId: String, val displayName: String)

/**
 * Offline catalog of pickable dyes for the in-screen add panel (PBI 34). Derived by inverting
 * [InventoryUtils.dyeDisplayNameToId] (the single source of truth for dye names, kept in
 * lock-step with `DyeSprites.DYE_IDS`), so it automatically covers every dye that has a bundled
 * sprite — no duplicated list. Needs no network.
 */
object DyeCatalog {

    /** All dye options, Title-Cased and sorted by display name. */
    val all: List<DyeOption> by lazy {
        InventoryUtils.dyeDisplayNameToId()
            .map { (name, id) -> DyeOption(dyeId = id, displayName = titleCase(name)) }
            .sortedBy { it.displayName }
    }

    /**
     * Options matching [query] (case-insensitive substring of the display name or id). An empty
     * query returns [all]; no match returns an empty list.
     */
    fun filter(query: String): List<DyeOption> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { it.displayName.lowercase().contains(q) || it.dyeId.lowercase().contains(q) }
    }

    private fun titleCase(name: String): String =
        name.split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}
