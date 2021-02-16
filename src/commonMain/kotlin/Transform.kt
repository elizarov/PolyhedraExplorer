package polyhedra.common

import kotlin.math.*

enum class Transform(val transform: (Polyhedron) -> Polyhedron) {
    None({ it }),
    Dual(Polyhedron::dual),
    Rectified(Polyhedron::rectified),
    Truncated(Polyhedron::truncated)
}

val Transforms: List<Transform> by lazy { Transform.values().toList() }

fun Polyhedron.transformed(transform: Transform) =
    transform.transform(this)

fun Polyhedron.transformed(transforms: List<Transform>) =
    transforms.fold(this) { poly, transform -> poly.transformed(transform) }

fun Polyhedron.dual() = polyhedron {
    // vertices from faces
    val r = midradius
    for (f in fs) {
        vertex(f.plane.dualPoint(r), VertexKind(f.kind.id))
    }
    // faces from vertices
    for ((v, fl) in vertexFaces) {
        face(fl.map { it.id }, FaceKind(v.kind.id), sort = true)
    }
}

fun Polyhedron.rectified() = polyhedron {
    // vertices from edges
    for (e in es) {
        vertex(e.midPoint(edgesMidPointDefault), VertexKind(edgeKindsIndex[e.kind]!!))
    }
    // faces from the original faces
    for (f in fs) {
        face(f.fvs.zipWithCycle { a, b -> vertexEdges[a]!![b]!!.id }, f.kind)
    }
    // faces from the original vertices
    for (v in vs) {
        face(vertexEdges[v]!!.map { it.value.id }, FaceKind(kindFaces.size + v.kind.id), sort = true)
    }
}

val Polyhedron.regularTruncationRatio: Double
    get() {
        val n = fs[0].size // primary face size
        return 1 / (1 + cos(PI / n))
    }

fun Polyhedron.truncated(ratio: Double = regularTruncationRatio) = when {
    ratio <= EPS -> this
    ratio >= 1 - EPS -> rectified()
    else -> polyhedron {
        // vertices from vertex pairs
        val edgeIds = directedEdges.map { e ->
            val t = ratio * e.midPointFraction(edgesMidPointDefault)
            Pair(e, vertex(t.atSegment(e.a.pt, e.b.pt), VertexKind(directedEdgeKindsIndex[e.kind]!!)))
        }.associateBy({ (e, _) -> e.a to e.b }, { (_, c) -> c.id })
        // faces from the original faces
        for (f in fs) {
            val fvIds = f.fvs.zipWithCycle { a, b ->
                listOf(edgeIds[a to b]!!, edgeIds[b to a]!!)
            }.flatten()
            face(fvIds, f.kind)
        }
        // faces from the original vertices
        for (v in vs) {
            val fvIds = vertexEdges[v]!!.map { edgeIds[v to it.key]!! }
            face(fvIds, FaceKind(kindFaces.size + v.kind.id), sort = true)
        }
    }
}