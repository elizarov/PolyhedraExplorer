package polyhedra.common

enum class Scale(val denominator: (Polyhedron) -> Double) {
    Inradius(Polyhedron::inradius),
    Midradius(Polyhedron::midradius),
    Circumradis(Polyhedron::circumradius)
}

fun Polyhedron.scaled(factor: Double): Polyhedron = polyhedron {
    for (v in vs) vertex(factor * v.pt, v.kind)
    for (f in fs) face(f)
}

fun Polyhedron.scaled(scale: Scale) =
    scaled(1 / scale.denominator(this))
