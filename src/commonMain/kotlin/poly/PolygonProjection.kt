/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.poly

import polyhedra.common.util.*

class PolygonProjection(
    val vs: List<Vec3>
)

infix fun PolygonProjection.approx(other: PolygonProjection) =
    vs.size == other.vs.size &&
    vs.indices.all { i -> vs[i] approx other.vs[i] }

val VertexListApproxComparator : Comparator<List<Vec3>> =
    LexicographicListComparator(Vec3ApproxComparator)