package polyhedra.common

fun Polyhedron.dual() = polyhedron {
    for (f in fs) {
        val n = f.plane.n
        val d = f.plane.d
        vertex(n.x / d, n.y / d, n.z / d)
    }
    val vfl = fs
        .flatMap { f -> f.vs.map { v -> v to f.id } }
        .groupBy({ it.first }, { it.second })
    for ((v, fl) in vfl) {
        face(fl, v.kind, sort = true)
    }
}