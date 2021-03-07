package polyhedra.common

import polyhedra.common.util.*

enum class Scale(override val tag: String, val denominator: (Polyhedron) -> Double) : Tagged {
    Inradius("i", Polyhedron::inradius),
    Midradius("m", Polyhedron::midradius),
    Circumradius("c", Polyhedron::circumradius);

    val transform: (Polyhedron) -> Polyhedron = {
        it.scaled(1 / denominator(it))
    }
}

val Scales: List<Scale> by lazy { Scale.values().toList() }

fun Polyhedron.scaled(factor: Double): Polyhedron = polyhedron {
    for (v in vs) vertex(factor * v, v.kind)
    for (f in fs) face(f)
}

fun Polyhedron.scaled(scale: Scale): Polyhedron {
    val current = scale.denominator(this)
    if (current approx 1.0) return this // fast path, don't occupy memo slot
    return memoTransform(scale.transform)
}
