package polyhedra.common

import polyhedra.common.util.*

enum class Scale(val denominator: (Polyhedron) -> Double) {
    Inradius(Polyhedron::inradius),
    Midradius(Polyhedron::midradius),
    Circumradius(Polyhedron::circumradius);

    val transform: (Polyhedron) -> Polyhedron = {
        it.scaled(1 / denominator(it))
    }
}

val Scales: List<Scale> by lazy { Scale.values().toList() }

fun Polyhedron.scaled(factor: Double): Polyhedron = polyhedron {
    for (v in vs) vertex(factor * v.pt, v.kind)
    for (f in fs) face(f)
}

fun Polyhedron.scaled(scale: Scale): Polyhedron {
    val current = scale.denominator(this)
    if (current approx 1.0) return this // fast path, don't occupy memo slot
    return memoTransform(scale.transform)
}
