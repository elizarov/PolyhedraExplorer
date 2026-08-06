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
import polyhedra.model.poly.normalizedDirection
import polyhedra.model.util.*

private const val ONE_THIRD = 1.0 / 3.0
private const val EDGE_RADIUS_FACTOR = 0.9
private const val INNER_RADIUS_FACTOR = 0.8

private fun Face.centroid(): Vec3 = MutableVec3().also { center ->
    for (vertex in fvs) center += vertex
    center /= fvs.size
}

private suspend fun Polyhedron.canonicalConwayTransform(
    key: Transform,
    progress: OperationProgressContext?,
    topology: Polyhedron.() -> Polyhedron,
): Polyhedron {
    TransformCache[this, key]?.let { return it }
    val result = runCatching {
        val raw = topology()
        val canonical = raw.canonical(progress)
        polyhedronCopy(canonical.vs, canonical.fs, raw.faceKindSources)
    }
    TransformCache[this, key] = result
    return result.getOrThrow()
}

fun Polyhedron.propeller(chirality: Chirality = Chirality.Default): Polyhedron =
    runSynchronously { propeller(chirality, null) }

suspend fun Polyhedron.propeller(
    chirality: Chirality,
    progress: OperationProgressContext?,
): Polyhedron {
    val key = if (chirality == Chirality.Default) Transform.Propeller else Transform.PropellerFlipped
    return canonicalConwayTransform(key, progress) {
        if (chirality == Chirality.Default) rawPropeller() else reflected().rawPropeller().reflected()
    }
}

private fun Polyhedron.rawPropeller(): Polyhedron = polyhedron {
    val oldVertices = vs.associateWith { vertex -> vertex(vertex) }
    val edgeVertexKindOffset = vertexKinds.size
    val edgeVertices = directedEdges.associateWith { edge ->
        vertex(
            ONE_THIRD.atSegment(edge.a, edge.b) * EDGE_RADIUS_FACTOR,
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

fun Polyhedron.whirl(chirality: Chirality = Chirality.Default): Polyhedron =
    runSynchronously { whirl(chirality, null) }

suspend fun Polyhedron.whirl(
    chirality: Chirality,
    progress: OperationProgressContext?,
): Polyhedron {
    val key = if (chirality == Chirality.Default) Transform.Whirl else Transform.WhirlFlipped
    return canonicalConwayTransform(key, progress) {
        if (chirality == Chirality.Default) rawWhirl() else reflected().rawWhirl().reflected()
    }
}

private fun Polyhedron.rawWhirl(): Polyhedron = polyhedron {
    val oldVertices = vs.associateWith { vertex -> vertex(vertex) }
    val edgeVertexKindOffset = vertexKinds.size
    val edgeVertices = directedEdges.associateWith { edge ->
        vertex(
            ONE_THIRD.atSegment(edge.a, edge.b) * EDGE_RADIUS_FACTOR,
            VertexKind(edgeVertexKindOffset + directedEdgeKindsIndex.getValue(edge.kind)),
        )
    }
    val faceCenters = fs.associateWith(Face::centroid)
    val innerVertexKindOffset = edgeVertexKindOffset + directedEdgeKindsIndex.size
    val innerVertices = directedEdges.associateWith { edge ->
        vertex(
            ONE_THIRD.atSegment(faceCenters.getValue(edge.r), edgeVertices.getValue(edge)) *
                INNER_RADIUS_FACTOR,
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

fun Polyhedron.quinto(): Polyhedron = runSynchronously { quinto(null) }

suspend fun Polyhedron.quinto(progress: OperationProgressContext?): Polyhedron =
    canonicalConwayTransform(Transform.Quinto, progress) { rawQuinto() }

private fun Polyhedron.rawQuinto(): Polyhedron = polyhedron {
    val oldVertices = vs.associateWith { vertex -> vertex(vertex) }
    val edgeVertexKindOffset = vertexKinds.size
    val edgeVertices = es.associateWith { edge ->
        vertex(
            0.5.atSegment(edge.a, edge.b) * EDGE_RADIUS_FACTOR,
            VertexKind(edgeVertexKindOffset + edgeKindsIndex.getValue(edge.kind)),
        )
    }
    val faceCenters = fs.associateWith(Face::centroid)
    val innerVertexKindOffset = edgeVertexKindOffset + edgeKindsIndex.size
    val innerVertices = directedEdges.associateWith { edge ->
        vertex(
            0.5.atSegment(
                edgeVertices.getValue(edge.normalizedDirection()),
                faceCenters.getValue(edge.r),
            ) * INNER_RADIUS_FACTOR,
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
