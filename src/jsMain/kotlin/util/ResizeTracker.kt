/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

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