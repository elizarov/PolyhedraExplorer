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
private const val PROPELLER_ANIMATION_START_FRACTION = 0.01
private const val WHIRL_ANIMATION_INNER_FRACTION = 0.01
private const val QUINTO_ANIMATION_INNER_FRACTION = 0.99

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
    edgeFraction: Double = ONE_THIRD,
    edgeRadiusFactor: Double = EDGE_RADIUS_FACTOR,
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
    edgeFraction: Double = ONE_THIRD,
    edgeRadiusFactor: Double = EDGE_RADIUS_FACTOR,
    innerFraction: Double = ONE_THIRD,
    innerRadiusFactor: Double = INNER_RADIUS_FACTOR,
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

fun Polyhedron.quinto(): Polyhedron = runSynchronously { quinto(null) }

suspend fun Polyhedron.quinto(progress: OperationProgressContext?): Polyhedron =
    canonicalConwayTransform(Transform.Quinto, progress) { rawQuinto() }

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
    edgeFraction: Double = 0.5,
    edgeRadiusFactor: Double = EDGE_RADIUS_FACTOR,
    innerFraction: Double = 0.5,
    innerRadiusFactor: Double = INNER_RADIUS_FACTOR,
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
