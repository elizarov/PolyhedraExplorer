package polyhedra.js.util

import kotlinx.browser.*

object ResizeTracker {
    private val listeners = ArrayList<() -> Unit>()

    fun add(listener: () -> Unit) {
        if (listeners.isEmpty()) {
            window.onresize = {
                listeners.forEach { it() }
            }
        }
        listeners += listener
    }

    fun remove(listener: () -> Unit) {
        listeners -= listener
        if (listeners.isEmpty()) {
            window.onresize = null
        }
    }
}