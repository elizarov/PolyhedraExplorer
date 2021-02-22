package polyhedra.common

import polyhedra.common.util.*
import kotlin.math.*

enum class Transform(
    override val tag: String,
    val transform: (Polyhedron) -> Polyhedron,
    val isApplicable: (Polyhedron) -> String? = { null }
) : Tagged {
    None("n", { it }),
    Dual("d", Polyhedron::dual),
    Rectified("r", Polyhedron::rectified),
    Truncated("t", Polyhedron::truncated, {
        if (it.regularTruncationRatio <= EPS) "Truncation ratio is too small to keep regular faces" else null
    })
}

val Transforms: List<Transform> by lazy { Transform.values().toList() }

fun Polyhedron.transformed(transform: Transform) = memoTransform(transform.transform)

fun Polyhedron.dual() = polyhedron {
    val r = midradius
    // vertices from faces
    val fv = fs.associateWith { f ->
        vertex(f.plane.dualPoint(r), VertexKind(f.kind.id))
    }
    // faces from vertices
    for ((v, fl) in vertexFaces) {
        face(fl.map { fv[it]!! }, FaceKind(v.kind.id))
    }
}

fun Polyhedron.rectified() = polyhedron {
    // vertices from edges
    val ev = es.associateWith { e ->
        vertex(e.midPoint(edgesMidPointDefault), VertexKind(edgeKindsIndex[e.kind]!!))
    }
    // faces from the original faces
    for (f in fs) {
        face(faceDirectedEdges[f]!!.map { ev[it.normalizedDirection()]!! }, f.kind)
    }
    // faces from the original vertices
    for (v in vs) {
        face(vertexDirectedEdges[v]!!.map { ev[it.normalizedDirection()]!! }, FaceKind(faceKinds.size + v.kind.id))
    }
}

val Polyhedron.regularTruncationRatio: Double
    get() {
        val n = fs[0].size // primary face size
        return 1 / (1 + cos(PI / n))
    }

fun Polyhedron.truncated(ratio: Double = regularTruncationRatio) = polyhedron {
    // vertices from directed edges
    val ev = directedEdges.associateWith { e ->
        val t = ratio * e.midPointFraction(edgesMidPointDefault)
        vertex(t.atSegment(e.a.pt, e.b.pt), VertexKind(directedEdgeKindsIndex[e.kind]!!))
    }
    // faces from the original faces
    for (f in fs) {
        val fvIds = faceDirectedEdges[f]!!.flatMap {
            listOf(ev[it]!!, ev[it.reversed()]!!)
        }
        face(fvIds, f.kind)
    }
    // faces from the original vertices
    for (v in vs) {
        face(vertexDirectedEdges[v]!!.map { ev[it]!! }, FaceKind(faceKinds.size + v.kind.id))
    }
}