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
        vertex(f.plane.dualPoint(r), f.kind)
    }
    // faces from vertices
    for ((v, fl) in vertexFaces) {
        face(fl.map { it.id }, v.kind, sort = true)
    }
}

fun Polyhedron.rectified() = polyhedron {
    // vertices from edges
    for (e in es) {
        vertex(e.tangentPoint, edgeKinds[e.kind]!!)
    }
    // faces from the original faces
    for (f in fs) {
        face(f.fvs.zipWithCycle { a, b -> vertexEdges[a]!![b]!!.id }, f.kind)
    }
    // faces from the original vertices
    for (v in vs) {
        face(vertexEdges[v]!!.map { it.value.id }, Kind(kindFaces.size + v.kind.id), sort = true)
    }
}

val Polyhedron.regularTruncationRatio: Double
    get() {
        val n = fs[0].size // primary face size
        return 1 / (1 + cos(PI / n))
    }

fun Polyhedron.truncated(ratio: Double = regularTruncationRatio) =
    if (ratio <= EPS) this else
    if (ratio >= 1 - EPS) rectified() else
    polyhedron {
        // vertices from vertex pairs
        val vertexPairIds = fs.flatMap { f ->
            f.fvs.zipWithCycle { a, b ->
                val vec = b.pt - a.pt
                val t = ratio * tangentFraction(a.pt, vec)
                val kind = directedEdgeKinds[EdgeKind(a.kind, b.kind)]!!
                Triple(a, b, vertex(a.pt + t * vec, kind))
            }
        }.associateBy({ (a, b, _) -> a to b }, { (_, _, c) -> c.id })
        // faces from the original faces
        for (f in fs) {
            val fvIds = f.fvs.zipWithCycle { a, b ->
                listOf(vertexPairIds[a to b]!!, vertexPairIds[b to a]!!)
            }.flatten()
            face(fvIds, f.kind)
        }
        // faces from the original vertices
        for (v in vs) {
            val fvIds = vertexEdges[v]!!.map { vertexPairIds[v to it.key]!! }
            face(fvIds, Kind(kindFaces.size + v.kind.id), sort = true)
    }
}