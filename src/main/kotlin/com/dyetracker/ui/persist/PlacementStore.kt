package com.dyetracker.ui.persist

import com.dyetracker.ui.core.WidgetPlacement

/**
 * Generic, thread-safe, id-keyed collection helper for a feature's list of persisted widget
 * placements (each element a [WidgetPlacement] — e.g. the GIF overlay config). Implements the
 * transient-update-then-flush pattern so a fast drag/scale burst mutates only in memory and
 * hits disk once when the interaction ends, instead of once per frame.
 *
 * The store does not own the data: [read]/[write] view a slice of the mod config (copy-on-
 * write — [write] replaces the whole list in memory), and [persist] flushes the current
 * config to disk. All operations run under [lock] (the feature's collection lock).
 *
 * Each feature constructs one of these over its own config slice; nothing here is GIF-specific.
 */
class PlacementStore<T : WidgetPlacement>(
    private val read: () -> List<T>,
    private val write: (List<T>) -> Unit,
    private val persist: () -> Unit,
    private val lock: Any,
) {

    /** Current items (snapshot). */
    fun all(): List<T> = synchronized(lock) { read() }

    /** Append [item] and persist. */
    fun add(item: T) {
        synchronized(lock) {
            write(read() + item)
            persist()
        }
    }

    /** Remove the item with [id] and persist. Returns true if one was removed. */
    fun remove(id: String): Boolean = synchronized(lock) {
        val current = read()
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return@synchronized false
        write(next)
        persist()
        true
    }

    /** Apply [transform] to the item with [id] and persist. Returns true if found. */
    fun update(id: String, transform: (T) -> T): Boolean = synchronized(lock) {
        val changed = apply(id, transform)
        if (changed) persist()
        changed
    }

    /**
     * In-memory variant of [update] that does NOT persist — use during interactive drag/scale
     * to avoid a disk write per frame, then call [flush] once the interaction ends. Returns
     * true if found.
     */
    fun updateTransient(id: String, transform: (T) -> T): Boolean = synchronized(lock) {
        apply(id, transform)
    }

    /** Persist the current state to disk (flush buffered transient updates). */
    fun flush() {
        synchronized(lock) { persist() }
    }

    private fun apply(id: String, transform: (T) -> T): Boolean {
        var found = false
        val next = read().map { item ->
            if (item.id == id) {
                found = true
                transform(item)
            } else {
                item
            }
        }
        if (!found) return false
        write(next)
        return true
    }
}
