/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core.poly

import polyhedra.core.transform.*
import polyhedra.model.poly.*
import polyhedra.model.util.*

private object ScaledKey

fun Polyhedron.scaled(factor: Double): Polyhedron = transformedPolyhedron(ScaledKey, factor) {
    for (v in vs) vertex(factor * v, v.kind)
    for (f in fs) face(f)
    resolvedTopologyProvenance(resolvedTopologyProvenance)

}
                                                              
fun Polyhedron.scaled(scale: Scale?): Polyhedron {
    if (scale == null) return this
    val current = scaleDenominator(scale)
    if (current approx 1.0) return this // fast path, don't occupy cache slot
    return scaled(1 / current)
}
