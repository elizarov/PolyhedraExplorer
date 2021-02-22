package polyhedra.common

import kotlin.math.*

enum class Seed(override val tag: String, val poly: Polyhedron) : Tagged {
    Tetrahedron("T", tetrahedron),
    Cube("C", cube),
    Octahedron("O", octahedron),
    Dodecahedron("D", dodecahedron),
    Icosahedron("I", icosahedron)
}

val Seeds: List<Seed> by lazy { Seed.values().toList() }

val tetrahedron = polyhedron {
    val t = 1 / sqrt(2.0)
    vertex(-1.0, 0.0, -t) // 0
    vertex(1.0, 0.0, -t) // 1
    vertex(0.0, -1.0, t) // 2
    vertex(0.0, 1.0, t) // 3
    face(0, 1, 3)
    face(0, 2, 1)
    face(0, 3, 2)
    face(1, 2, 3)
}

val cube = polyhedron {
    vertex(1.0, 1.0, -1.0) // 0
    vertex(-1.0, 1.0, -1.0) // 1
    vertex(-1.0, -1.0, -1.0) // 2
    vertex(1.0, -1.0, -1.0) // 3
    vertex(1.0, 1.0, 1.0) // 4
    vertex(-1.0, 1.0, 1.0) // 5
    vertex(-1.0, -1.0, 1.0) // 6
    vertex(1.0, -1.0, 1.0) // 7
    face(0, 1, 2, 3)
    face(0, 4, 5, 1)
    face(1, 5, 6, 2)
    face(2, 6, 7, 3)
    face(3, 7, 4, 0)
    face(4, 7, 6, 5)
}

val octahedron = cube.dual()

val icosahedron = polyhedron {
    val phi = (sqrt(5.0) + 1) / 2
    vertex(0.0, -1.0, -phi) // 0
    vertex(0.0, 1.0, -phi) // 1
    vertex(-phi, 0.0, -1.0) // 2
    vertex(phi, 0.0, -1.0) // 3
    vertex(-1.0, -phi, 0.0) // 4
    vertex(-1.0, phi, 0.0) // 5
    vertex(1.0, -phi, 0.0) // 6
    vertex(1.0, phi, 0.0) // 7
    vertex(-phi, 0.0, 1.0) // 8
    vertex(phi, 0.0, 1.0) // 9
    vertex(0.0, -1.0, phi) // 10
    vertex(0.0, 1.0, phi) // 11
    face(0, 1, 2)
    face(1, 0, 3)
    face(0, 2, 4)
    face(2, 1, 5)
    face(1, 3, 7)
    face(3, 0, 6)
    face(1, 7, 5)
    face(0, 4, 6)
    face(2, 8, 4)
    face(2, 5, 8)
    face(3, 6, 9)
    face(3, 9, 7)
    face(4, 10, 6)
    face(5, 7, 11)
    face(8, 10, 4)
    face(5, 11, 8)
    face(9, 11, 7)
    face(6, 10, 9)
    face(8, 11, 10)
    face(9, 10, 11)
}

val dodecahedron= icosahedron.dual()