package polyhedra.web

import polyhedra.core.poly.polyhedron
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron

internal fun concavePrismFixture(): Polyhedron = polyhedron {
    val boundary = listOf(
        0.0 to 0.0,
        0.0 to 3.0,
        3.0 to 3.0,
        3.0 to 2.0,
        1.0 to 2.0,
        1.0 to 1.0,
        3.0 to 1.0,
        3.0 to 0.0,
    )
    boundary.forEach { (x, y) -> vertex(x - 1.5, y - 1.5, 1.0) }
    boundary.forEach { (x, y) -> vertex(x - 1.5, y - 1.5, -1.0) }
    val n = boundary.size
    face((0 until n).toList(), FaceKind(0))
    face((0 until n).map { index -> n + index }.asReversed(), FaceKind(1))
    repeat(n) { index ->
        val next = (index + 1) % n
        face(listOf(next, index, n + index, n + next), FaceKind(2 + index))
    }
}
