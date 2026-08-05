/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.poly

fun <T> Map<Edge, T>.directedEdgeToFaceVertexMap(): Map<Face, Map<Vertex, T>> =
    entries
    .groupBy{ it.key.r }
    .mapValues { e ->
        e.value.associateBy({ it.key.a }, { it.value })
    }