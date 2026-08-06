package polyhedra.core.transform

import kotlinx.serialization.Serializable
import polyhedra.core.poly.*
import polyhedra.model.poly.*
import polyhedra.model.util.*

private const val KIS_FACE_TAG = "k"
private const val TRUNCATE_VERTEX_TAG = "t"
private const val RECTIFY_VERTEX_TAG = "a"

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
data class KisFace(val kind: FaceKind) : Transform() {
    override val tag: String
        get() = "$KIS_FACE_TAG[$kind]"

    override fun transform(poly: Polyhedron): Polyhedron = poly.kisFaces(setOf(kind))

    override fun isApplicable(poly: Polyhedron): Boolean = kind in poly.faceKinds

    override fun toString(): String = "Kis $kind"
}

@Serializable
data class TruncateVertex(val kind: VertexKind) : Transform(), OrbitTargetedAnimation {
    override val targetKind: AnyKind
        get() = kind

    override val tag: String
        get() = "$TRUNCATE_VERTEX_TAG[$kind]"

    override fun transform(poly: Polyhedron): Polyhedron = poly.truncateVertices(setOf(kind))

    override fun isApplicable(poly: Polyhedron): Boolean = kind in poly.vertexKinds

    override fun targetRatio(poly: Polyhedron): Double = poly.regularTruncationRatio()

    override fun polyAtRatio(
        poly: Polyhedron,
        ratio: Double,
        scale: Scale?,
        forceFaceKinds: List<FaceKindSource>?,
    ): Polyhedron = poly.truncateVertices(setOf(kind), ratio, scale, forceFaceKinds)

    override fun toString(): String = "Truncate $kind"
}

@Serializable
data class RectifyVertex(val kind: VertexKind) : Transform(), OrbitTargetedAnimation {
    override val targetKind: AnyKind
        get() = kind

    override val tag: String
        get() = "$RECTIFY_VERTEX_TAG[$kind]"

    override fun transform(poly: Polyhedron): Polyhedron = poly.rectifyVertices(setOf(kind))

    override fun isApplicable(poly: Polyhedron): Boolean = kind in poly.vertexKinds

    override fun targetRatio(poly: Polyhedron): Double = 1.0

    override fun polyAtRatio(
        poly: Polyhedron,
        ratio: Double,
        scale: Scale?,
        forceFaceKinds: List<FaceKindSource>?,
    ): Polyhedron = poly.truncateVertices(setOf(kind), ratio, scale, forceFaceKinds)

    override fun toString(): String = "Rectify $kind"
}

internal fun String.toOrbitTargetedTransformOrNull(): Transform? {
    if (!endsWith("]")) return null
    val bracket = indexOf('[')
    if (bracket <= 0) return null
    val prefix = substring(0, bracket)
    val kind = substring(bracket + 1, length - 1).toAnyKindOrNull() ?: return null
    return when {
        prefix == DROP_TAG -> Drop(kind)
        prefix == KIS_FACE_TAG && kind is FaceKind -> KisFace(kind)
        prefix == TRUNCATE_VERTEX_TAG && kind is VertexKind -> TruncateVertex(kind)
        prefix == RECTIFY_VERTEX_TAG && kind is VertexKind -> RectifyVertex(kind)
        else -> null
    }
}

val Polyhedron.availableOrbitTransforms: Set<Transform>
    get() = buildSet {
        canDrop.mapTo(this, ::Drop)
        if (faceKinds.size > 1) faceKinds.keys.mapTo(this, ::KisFace)
        if (vertexKinds.size > 1) {
            vertexKinds.keys.mapTo(this, ::TruncateVertex)
            vertexKinds.keys.mapTo(this, ::RectifyVertex)
        }
    }

/** Kises the selected face orbits. Selecting every orbit is exactly the Kis macro geometry. */
internal fun Polyhedron.kisFaces(
    kinds: Set<FaceKind>,
): Polyhedron {
    require(kinds.isNotEmpty() && kinds.all { it in faceKinds })
    val fullKis = dual().truncated().dual()
    if (kinds.size == faceKinds.size && kinds.containsAll(faceKinds.keys)) return fullKis

    return transformedPolyhedron(KisFace::class, kinds) {
        // Full Kis retains one vertex for each input vertex, followed by one apex per input face.
        check(fullKis.vs.size == vs.size + fs.size)
        val outputVertices = HashMap<Vertex, Vertex>()
        for (vertex in vs) {
            outputVertices[fullKis.vs[vertex.id]] = vertex(fullKis.vs[vertex.id])
        }
        for (sourceFace in fs) {
            if (sourceFace.kind in kinds) {
                val apex = fullKis.vs[vs.size + sourceFace.id]
                outputVertices[apex] = vertex(apex)
            } else {
                face(sourceFace.fvs.map { outputVertices.getValue(fullKis.vs[it.id]) }, sourceFace.kind)
            }
        }

        val newFaceKindOffset = faceKinds.size
        for (fullFace in fullKis.fs) {
            val apex = fullFace.fvs.singleOrNull { it.id >= vs.size }
                ?: error("Kis face has no unique apex: $fullFace")
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
