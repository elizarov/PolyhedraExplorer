/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core.transform

import polyhedra.core.poly.*
import polyhedra.core.util.OperationProgressContext
import polyhedra.core.util.runSynchronously
import polyhedra.model.poly.Chirality
import polyhedra.model.poly.Face
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.IsoDir
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.VertexKind
import polyhedra.model.util.*

private const val WHIRL_EDGE_FRACTION = 1.0 / 3.0
private const val WHIRL_EDGE_RADIUS_FACTOR = 0.9
private const val WHIRL_INNER_FRACTION = 1.0 / 3.0
private const val WHIRL_INNER_RADIUS_FACTOR = 0.8
private const val WHIRL_ANIMATION_INNER_FRACTION = 0.01

private fun Face.centroid(): Vec3 = MutableVec3().also { center ->
    for (vertex in fvs) center += vertex
    center /= fvs.size
}

fun Polyhedron.whirl(chirality: Chirality = Chirality.Default): Polyhedron =
    runSynchronously { whirl(chirality, null) }

suspend fun Polyhedron.whirl(
    chirality: Chirality,
    progress: OperationProgressContext?,
): Polyhedron {
    val key = if (chirality == Chirality.Default) Transform.Whirl else Transform.WhirlFlipped
    return cachedCanonicalTransform(key, progress) {
        if (chirality == Chirality.Default) rawWhirl() else reflected().rawWhirl().reflected()
    }
}

/**
 * The Whirl topology laid flat on the input surface with its inner ring nearly collapsed at each
 * source-face center. The corner hexagons still tile the input face and visibly open outward.
 */
internal fun Polyhedron.whirlAnimationStart(chirality: Chirality): Polyhedron =
    if (chirality == Chirality.Default) {
        rawWhirl(
            edgeRadiusFactor = 1.0,
            innerFraction = WHIRL_ANIMATION_INNER_FRACTION,
            innerRadiusFactor = 1.0,
        )
    } else {
        reflected().rawWhirl(
            edgeRadiusFactor = 1.0,
            innerFraction = WHIRL_ANIMATION_INNER_FRACTION,
            innerRadiusFactor = 1.0,
        ).reflected()
    }

private fun Polyhedron.rawWhirl(
    edgeFraction: Double = WHIRL_EDGE_FRACTION,
    edgeRadiusFactor: Double = WHIRL_EDGE_RADIUS_FACTOR,
    innerFraction: Double = WHIRL_INNER_FRACTION,
    innerRadiusFactor: Double = WHIRL_INNER_RADIUS_FACTOR,
): Polyhedron = polyhedron {
    val oldVertices = vs.associateWith { vertex -> vertex(vertex) }
    val edgeVertexKindOffset = vertexKinds.size
    val edgeVertices = directedEdges.associateWith { edge ->
        vertex(
            edgeFraction.atSegment(edge.a, edge.b) * edgeRadiusFactor,
            VertexKind(edgeVertexKindOffset + directedEdgeKindsIndex.getValue(edge.kind)),
        )
    }
    val faceCenters = fs.associateWith(Face::centroid)
    val innerVertexKindOffset = edgeVertexKindOffset + directedEdgeKindsIndex.size
    val innerVertices = directedEdges.associateWith { edge ->
        vertex(
            innerFraction.atSegment(faceCenters.getValue(edge.r), edgeVertices.getValue(edge)) *
                innerRadiusFactor,
            VertexKind(innerVertexKindOffset + directedEdgeKindsIndex.getValue(edge.kind)),
        )
    }

    for (face in fs) {
        face(face.directedEdges.map { edge -> innerVertices.getValue(edge) }, face.kind)
    }

    val edgeFaceKindOffset = faceKinds.size
    for (edge in directedEdges) {
        val next = edge.next(IsoDir.R)
        face(
            listOf(
                innerVertices.getValue(edge),
                edgeVertices.getValue(edge),
                edgeVertices.getValue(edge.reversed),
                oldVertices.getValue(edge.b),
                edgeVertices.getValue(next),
                innerVertices.getValue(next),
            ),
            FaceKind(edgeFaceKindOffset + directedEdgeKindsIndex.getValue(edge.kind)),
        )
    }
    for ((kind, index) in directedEdgeKindsIndex) {
        faceKindSource(FaceKind(edgeFaceKindOffset + index), kind)
    }
    mergeIndistinguishableKinds()
}
