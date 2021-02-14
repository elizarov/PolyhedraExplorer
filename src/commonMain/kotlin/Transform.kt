package polyhedra.common

enum class Transform(val transform: (Polyhedron) -> Polyhedron) {
    None({ it }),
    Dual(Polyhedron::dual),
    Rectified(Polyhedron::rectified)
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
        face(f.vs.zipWithNextCycle { a, b -> vertexEdges[a]!![b]!!.id }, f.kind)
    }
    // faces from the original vertices
    for (v in vs) {
        face(vertexEdges[v]!!.map { it.value.id }, Kind(kindFaces.size + v.kind.id), sort = true)
    }
}