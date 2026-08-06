package polyhedra.model.poly

import polyhedra.model.util.Tagged

enum class Scale(override val tag: String) : Tagged {
    Inradius("i"),
    Midradius("m"),
    Circumradius("c"),
}

val Scales: List<Scale> = Scale.entries
