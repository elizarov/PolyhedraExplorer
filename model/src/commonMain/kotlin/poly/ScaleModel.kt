package polyhedra.common.poly

import polyhedra.common.util.Tagged

enum class Scale(override val tag: String) : Tagged {
    Inradius("i"),
    Midradius("m"),
    Circumradius("c"),
}

val Scales: List<Scale> = Scale.entries
