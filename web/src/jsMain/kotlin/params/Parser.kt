/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.params

import polyhedra.model.serialization.ParsedParameter
import polyhedra.model.serialization.parseCompactParameters

typealias ParsedParam = ParsedParameter

fun Param.loadFromString(str: String) {
    val parsed = parseCompactParameters(str)
    val updated = ArrayList<Param>()
    loadFrom(parsed) { updated += it }
    updated.forEach {
        // mark loaded values for repaint in the next animation frame
        it.notifyUpdated(Param.LoadedValue)
        // eagerly recompute derived values just like on TargetValue change
        it.computeDerivedTargetValues()
    }
}
