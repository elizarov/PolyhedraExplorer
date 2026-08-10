/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core.transform

import polyhedra.core.poly.*
import polyhedra.core.util.OperationProgressContext
import polyhedra.core.util.runSynchronously
import polyhedra.model.poly.Chirality
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.IsoDir
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.VertexKind
import polyhedra.model.util.*

private const val PROPELLER_EDGE_FRACTION = 1.0 / 3.0
private const val PROPELLER_EDGE_RADIUS_FACTOR = 0.9
private const val PROPELLER_ANIMATION_START_FRACTION = 0.01

fun Polyhedron.propeller(chirality: Chirality = Chirality.Default): Polyhedron =
    runSynchronously { propeller(chirality, null) }

suspend fun Polyhedron.propeller(
    chirality: Chirality,
    progress: OperationProgressContext?,
): Polyhedron {
    val key = if (chirality == Chirality.Default) Transform.Propeller else Transform.PropellerFlipped
    return cachedCanonicalTransform(key, progress) {
        if (chirality == Chirality.Default) rawPropeller() else reflected().rawPropeller().reflected()
    }
}

/**
 * The Propeller topology nearly collapsed onto the input polyhedron. The central face retains the
 * source face while its corner quadrilaterals start as narrow slivers, making the opening twist
 * visible even during the default short animation without evaluating exactly degenerate faces.
 */
internal fun Polyhedron.propellerAnimationStart(chirality: Chirality): Polyhedron =
    if (chirality == Chirality.Default) {
        rawPropeller(PROPELLER_ANIMATION_START_FRACTION, 1.0)
    } else {
        reflected().rawPropeller(PROPELLER_ANIMATION_START_FRACTION, 1.0).reflected()
    }

private fun Polyhedron.rawPropeller(
    edgeFraction: Double = PROPELLER_EDGE_FRACTION,
    edgeRadiusFactor: Double = PROPELLER_EDGE_RADIUS_FACTOR,
): Polyhedron = polyhedron {
    val oldVertices = vs.associateWith { vertex -> vertex(vertex) }
    val edgeVertexKindOffset = vertexKinds.size
    val edgeVertices = directedEdges.associateWith { edge ->
        vertex(
            edgeFraction.atSegment(edge.a, edge.b) * edgeRadiusFactor,
            VertexKind(edgeVertexKindOffset + directedEdgeKindsIndex.getValue(edge.kind)),
        )
    }

    for (face in fs) {
        face(face.directedEdges.map { edge -> edgeVertices.getValue(edge) }, face.kind)
    }

    val edgeFaceKindOffset = faceKinds.size
    for (edge in directedEdges) {
        val next = edge.next(IsoDir.R)
        face(
            listOf(
                edgeVertices.getValue(edge),
                edgeVertices.getValue(edge.reversed),
                oldVertices.getValue(edge.b),
                edgeVertices.getValue(next),
            ),
            FaceKind(edgeFaceKindOffset + directedEdgeKindsIndex.getValue(edge.kind)),
        )
    }
    for ((kind, index) in directedEdgeKindsIndex) {
        faceKindSource(FaceKind(edgeFaceKindOffset + index), kind)
    }
    mergeIndistinguishableKinds()
}
