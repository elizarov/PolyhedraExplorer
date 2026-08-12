package polyhedra.core.transform

import kotlinx.serialization.Serializable
import polyhedra.core.poly.*
import polyhedra.model.api.*
import polyhedra.model.poly.*
import polyhedra.model.util.*

internal data class KisAll(val height: Double = 1.0) : Transform() {
    override val id = TransformId(TransformOperation.Kis)
    override val tweaks: Map<TransformTweak, Double>
        get() = mapOf(TransformTweak.Height to height)

    override val fev = TransformFEV(
        0, 2, 0,
        0, 3, 0,
        1, 0, 1,
    )

    override fun transform(poly: Polyhedron): Polyhedron =
        poly.kisFaces(poly.faceKinds.keys, height)

    override fun toString(): String = "Kis"
}

internal interface OrbitTargetedAnimation {
    val targetKind: AnyKind

    fun targetRatio(poly: Polyhedron): Double

    fun polyAtRatio(
        poly: Polyhedron,
        ratio: Double,
        scale: Scale?,
        forceFaceKinds: List<FaceKindSource>?,
    ): Polyhedron
}

@Serializable
data class KisFace(val kind: FaceKind, val height: Double = 1.0) : Transform() {
    @kotlinx.serialization.Transient
    override val id = TransformId(TransformOperation.Kis, target = kind)
    override val tweaks: Map<TransformTweak, Double>
        get() = mapOf(TransformTweak.Height to height)

    override fun transform(poly: Polyhedron): Polyhedron = poly.kisFaces(setOf(kind), height)

    override fun isApplicable(poly: Polyhedron): Boolean =
        kind in poly.faceKinds && (poly.isConvexGeometry || poly.regularStarFormOrNull() == null)

    override fun toString(): String = "Kis $kind"
}

@Serializable
data class TruncateVertex(val kind: VertexKind, val depth: Double = 1.0) : Transform(), OrbitTargetedAnimation {
    @kotlinx.serialization.Transient
    override val id = TransformId(TransformOperation.Truncated, target = kind)
    override val tweaks: Map<TransformTweak, Double>
        get() = mapOf(TransformTweak.Depth to depth)
    override val targetKind: AnyKind
        get() = kind

    override fun transform(poly: Polyhedron): Polyhedron =
        poly.truncateVertices(setOf(kind), targetRatio(poly))

    override fun isApplicable(poly: Polyhedron): Boolean = kind in poly.vertexKinds

    override fun targetRatio(poly: Polyhedron): Double = poly.regularTruncationRatio() * depth

    override fun polyAtRatio(
        poly: Polyhedron,
        ratio: Double,
        scale: Scale?,
        forceFaceKinds: List<FaceKindSource>?,
    ): Polyhedron = poly.truncateVertices(setOf(kind), ratio, scale, forceFaceKinds)

    override fun toString(): String = "Truncate $kind"
}

@Serializable
data class RectifyVertex(val kind: VertexKind, val depth: Double = 1.0) : Transform(), OrbitTargetedAnimation {
    @kotlinx.serialization.Transient
    override val id = TransformId(TransformOperation.Rectified, target = kind)
    override val tweaks: Map<TransformTweak, Double>
        get() = mapOf(TransformTweak.Depth to depth)
    override val targetKind: AnyKind
        get() = kind

    override fun transform(poly: Polyhedron): Polyhedron =
        if (depth == 1.0) poly.rectifyVertices(setOf(kind)) else poly.truncateVertices(setOf(kind), depth)

    override fun isApplicable(poly: Polyhedron): Boolean = kind in poly.vertexKinds

    override fun targetRatio(poly: Polyhedron): Double = depth

    override fun polyAtRatio(
        poly: Polyhedron,
        ratio: Double,
        scale: Scale?,
        forceFaceKinds: List<FaceKindSource>?,
    ): Polyhedron = poly.truncateVertices(setOf(kind), ratio, scale, forceFaceKinds)

    override fun toString(): String = "Rectify $kind"
}

internal fun TransformSpec.toOrbitTargetedTransformOrNull(): Transform? {
    val kind = id.target ?: return null
    return when (id.operation) {
        TransformOperation.Drop -> Drop(kind).takeIf { tweaks.isEmpty() }
        TransformOperation.Kis -> if (kind is FaceKind && tweaks.keys.all { it == TransformTweak.Height }) {
            KisFace(kind, tweaks[TransformTweak.Height] ?: 1.0)
        } else null
        TransformOperation.Truncated -> if (kind is VertexKind && tweaks.keys.all { it == TransformTweak.Depth }) {
            TruncateVertex(kind, tweaks[TransformTweak.Depth] ?: 1.0)
        } else null
        TransformOperation.Rectified -> if (kind is VertexKind && tweaks.keys.all { it == TransformTweak.Depth }) {
            RectifyVertex(kind, tweaks[TransformTweak.Depth] ?: 1.0)
        } else null
        else -> null
    }
}

val Polyhedron.availableOrbitTransforms: Set<Transform>
    get() = buildSet {
        canDrop.mapTo(this, ::Drop)
        if (faceKinds.size > 1 && (isConvexGeometry || regularStarFormOrNull() == null)) {
            faceKinds.keys.mapTo(this, ::KisFace)
        }
        if (vertexKinds.size > 1) {
            vertexKinds.keys.mapTo(this, ::TruncateVertex)
            vertexKinds.keys.mapTo(this, ::RectifyVertex)
        }
    }

/** Kises the selected face orbits. Selecting every orbit is exactly the Kis macro geometry. */
internal fun Polyhedron.kisFaces(
    kinds: Set<FaceKind>,
    height: Double = 1.0,
): Polyhedron {
    require(kinds.isNotEmpty() && kinds.all { it in faceKinds })
    val fullKis = dual().truncated().dual()
    if (height == 1.0 && kinds.size == faceKinds.size && kinds.containsAll(faceKinds.keys)) return fullKis
    require(isConvexGeometry || regularStarFormOrNull() == null) {
        "Continuous or orbit-targeted Kis is not available for resolved regular-star surfaces"
    }
    require(fullKis.vs.size == vs.size + fs.size) {
        "Continuous or orbit-targeted Kis requires a topological dual; it is not available for " +
            "this resolved regular-star surface"
    }

    return transformedPolyhedron(KisFace::class, kinds to height) {
        // Full Kis retains one vertex for each input vertex, followed by one apex per input face.
        val outputVertices = HashMap<Vertex, Vertex>()
        for (vertex in vs) {
            outputVertices[fullKis.vs[vertex.id]] = vertex(fullKis.vs[vertex.id])
        }
        for (sourceFace in fs) {
            if (sourceFace.kind in kinds) {
                val apex = fullKis.vs[vs.size + sourceFace.id]
                outputVertices[apex] = if (height == 1.0) {
                    vertex(apex)
                } else {
                    val center = MutableVec3()
                    // Measure height from the actual Kis base, whose retained vertices use the
                    // dual-truncate-dual realization rather than the input face coordinates.
                    for (sourceVertex in sourceFace.fvs) center += fullKis.vs[sourceVertex.id]
                    center /= sourceFace.size.toDouble()
                    vertex(center + height * (apex - center), apex.kind)
                }
            } else {
                face(sourceFace.fvs.map { outputVertices.getValue(fullKis.vs[it.id]) }, sourceFace.kind)
            }
        }

        val newFaceKindOffset = faceKinds.size
        for (fullFace in fullKis.fs) {
            val apex = requireNotNull(fullFace.fvs.singleOrNull { it.id >= vs.size }) {
                "Kis face has no unique apex: $fullFace"
            }
            val sourceFace = fs[apex.id - vs.size]
            if (sourceFace.kind !in kinds) continue
            face(
                fullFace.fvs.map(outputVertices::getValue),
                FaceKind(newFaceKindOffset + fullFace.kind.id),
            )
        }
        mergeIndistinguishableKinds()
    }
}

/** Truncates the selected vertex orbits. Selecting every orbit is exactly full truncation. */
internal fun Polyhedron.truncateVertices(kinds: Set<VertexKind>): Polyhedron {
    return truncateVertices(kinds, regularTruncationRatio())
}

internal fun Polyhedron.truncateVertices(
    kinds: Set<VertexKind>,
    ratio: Double,
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null,
): Polyhedron {
    require(kinds.isNotEmpty() && kinds.all { it in vertexKinds })
    require(ratio in 0.0..1.0)
    if (kinds.size == vertexKinds.size && kinds.containsAll(vertexKinds.keys)) {
        return truncated(ratio, scale, forceFaceKinds)
    }

    return transformedPolyhedron(TruncateVertex::class, kinds to ratio, scale, forceFaceKinds) {
        val retainedVertices = vs
            .filter { it.kind !in kinds }
            .associateWith { vertex(it) }
        val newVertexKindOffset = vertexKinds.size
        val truncatedVertices = directedEdges
            .filter { it.a.kind in kinds }
            .associateWith { edge ->
                val edgeRatio = ratio * edge.midPointFraction(edgesMidPointDefault)
                vertex(
                    edgeRatio.atSegment(edge.a, edge.b),
                    VertexKind(newVertexKindOffset + directedEdgeKindsIndex.getValue(edge.kind)),
                )
            }

        for (sourceFace in fs) {
            val vertices = ArrayList<Vertex>(sourceFace.size * 2)
            for (index in sourceFace.directedEdges.indices) {
                val outgoing = sourceFace.directedEdges[index]
                val vertex = outgoing.a
                if (vertex.kind in kinds) {
                    val incoming = sourceFace.directedEdges[(index + sourceFace.size - 1) % sourceFace.size]
                    vertices += truncatedVertices.getValue(incoming.reversed)
                    vertices += truncatedVertices.getValue(outgoing)
                } else {
                    vertices += retainedVertices.getValue(vertex)
                }
            }
            face(vertices, sourceFace.kind)
        }

        val newFaceKindOffset = faceKinds.size
        for (vertex in vs) {
            if (vertex.kind !in kinds) continue
            val kind = FaceKind(newFaceKindOffset + vertex.kind.id)
            face(vertex.directedEdges.map(truncatedVertices::getValue), kind)
            faceKindSource(kind, vertex.kind)
        }
        mergeIndistinguishableKinds()
    }
}

/** Rectifies the selected vertex orbits, sharing one midpoint when both edge ends are selected. */
internal fun Polyhedron.rectifyVertices(kinds: Set<VertexKind>): Polyhedron {
    require(kinds.isNotEmpty() && kinds.all { it in vertexKinds })
    if (kinds.size == vertexKinds.size && kinds.containsAll(vertexKinds.keys)) return rectified()

    return transformedPolyhedron(RectifyVertex::class, kinds) {
        val retainedVertices = vs
            .filter { it.kind !in kinds }
            .associateWith { vertex(it) }
        val midpointKinds = linkedMapOf<AnyKind, VertexKind>()
        val midpointVertices = linkedMapOf<Edge, Vertex>()

        fun selectedDirection(edge: Edge): Edge =
            if (edge.a.kind in kinds) edge else edge.reversed

        fun midpointKey(edge: Edge): Edge {
            val selected = selectedDirection(edge)
            return if (selected.b.kind in kinds) selected.normalizedDirection() else selected
        }

        fun midpointVertex(edge: Edge): Vertex {
            val key = midpointKey(edge)
            return midpointVertices.getOrPut(key) {
                val sourceKind: AnyKind = key.kind
                val kind = midpointKinds.getOrPut(sourceKind) {
                    VertexKind(vertexKinds.size + midpointKinds.size)
                }
                vertex(key.midPoint(edgesMidPointDefault), kind)
            }
        }

        for (edge in directedEdges) {
            if (edge.a.kind in kinds) midpointVertex(edge)
        }

        for (sourceFace in fs) {
            val output = ArrayList<Vertex>(sourceFace.size * 2)
            for (index in sourceFace.directedEdges.indices) {
                val outgoing = sourceFace.directedEdges[index]
                val sourceVertex = outgoing.a
                if (sourceVertex.kind in kinds) {
                    val incoming = sourceFace.directedEdges[(index + sourceFace.size - 1) % sourceFace.size]
                    output += midpointVertex(incoming)
                    output += midpointVertex(outgoing)
                } else {
                    output += retainedVertices.getValue(sourceVertex)
                }
            }
            val compact = output.filterIndexed { index, vertex ->
                index == 0 || vertex != output[index - 1]
            }.let { vertices ->
                if (vertices.size > 1 && vertices.first() == vertices.last()) vertices.dropLast(1) else vertices
            }
            face(compact, sourceFace.kind)
        }

        val newFaceKindOffset = faceKinds.size
        for (sourceVertex in vs) {
            if (sourceVertex.kind !in kinds) continue
            val kind = FaceKind(newFaceKindOffset + sourceVertex.kind.id)
            face(sourceVertex.directedEdges.map(::midpointVertex), kind)
            faceKindSource(kind, sourceVertex.kind)
        }
        mergeIndistinguishableKinds()
    }
}
