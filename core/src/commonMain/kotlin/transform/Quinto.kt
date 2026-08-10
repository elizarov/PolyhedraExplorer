/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core.transform

import polyhedra.core.poly.*
import polyhedra.core.util.OperationProgressContext
import polyhedra.core.util.runSynchronously
import polyhedra.model.poly.Face
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.IsoDir
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.normalizedDirection
import polyhedra.model.util.*

private const val QUINTO_EDGE_FRACTION = 0.5
private const val QUINTO_EDGE_RADIUS_FACTOR = 0.9
private const val QUINTO_INNER_FRACTION = 0.5
private const val QUINTO_INNER_RADIUS_FACTOR = 0.8
private const val QUINTO_ANIMATION_INNER_FRACTION = 0.99

private fun Face.centroid(): Vec3 = MutableVec3().also { center ->
    for (vertex in fvs) center += vertex
    center /= fvs.size
}

fun Polyhedron.quinto(): Polyhedron = runSynchronously { quinto(null) }

suspend fun Polyhedron.quinto(progress: OperationProgressContext?): Polyhedron =
    cachedCanonicalTransform(Transform.Quinto, progress) { rawQuinto() }

/**
 * The Quinto topology laid flat on the input surface with its central face nearly collapsed to the
 * source-face center. Its surrounding corner pentagons retain the visible input surface.
 */
internal fun Polyhedron.quintoAnimationStart(): Polyhedron = rawQuinto(
    edgeRadiusFactor = 1.0,
    innerFraction = QUINTO_ANIMATION_INNER_FRACTION,
    innerRadiusFactor = 1.0,
)

private fun Polyhedron.rawQuinto(
    edgeFraction: Double = QUINTO_EDGE_FRACTION,
    edgeRadiusFactor: Double = QUINTO_EDGE_RADIUS_FACTOR,
    innerFraction: Double = QUINTO_INNER_FRACTION,
    innerRadiusFactor: Double = QUINTO_INNER_RADIUS_FACTOR,
): Polyhedron = polyhedron {
    val oldVertices = vs.associateWith { vertex -> vertex(vertex) }
    val edgeVertexKindOffset = vertexKinds.size
    val edgeVertices = es.associateWith { edge ->
        vertex(
            edgeFraction.atSegment(edge.a, edge.b) * edgeRadiusFactor,
            VertexKind(edgeVertexKindOffset + edgeKindsIndex.getValue(edge.kind)),
        )
    }
    val faceCenters = fs.associateWith(Face::centroid)
    val innerVertexKindOffset = edgeVertexKindOffset + edgeKindsIndex.size
    val innerVertices = directedEdges.associateWith { edge ->
        vertex(
            innerFraction.atSegment(
                edgeVertices.getValue(edge.normalizedDirection()),
                faceCenters.getValue(edge.r),
            ) * innerRadiusFactor,
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
                edgeVertices.getValue(edge.normalizedDirection()),
                oldVertices.getValue(edge.b),
                edgeVertices.getValue(next.normalizedDirection()),
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
