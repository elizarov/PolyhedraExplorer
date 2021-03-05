package polyhedra.common

fun <T> Map<Edge, T>.directedEdgeToFaceVertexMap(): Map<Face, Map<Vertex, T>> =
    entries
    .groupBy{ it.key.r }
    .mapValues { e ->
        e.value.associateBy({ it.key.a }, { it.value })
    }