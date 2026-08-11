package polyhedra.core.transform

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import polyhedra.core.poly.KeplerPoinsotGeometry
import polyhedra.core.poly.RegularStarForm
import polyhedra.core.poly.regularStarFormOrNull
import polyhedra.core.poly.scaled
import polyhedra.model.api.TransformId
import polyhedra.model.api.TransformOperation
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.Scale

/** Conway greatening: retain the regular face type while moving to its great realization. */
@Serializable
class Greatened : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Greatened)

    override fun transform(poly: Polyhedron): Polyhedron = poly.greatened()
}

/** Conway stellation: replace the regular pentagons by their pentagram realization. */
@Serializable
class Stellated : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Stellated)

    override fun transform(poly: Polyhedron): Polyhedron = poly.stellated()
}

fun Polyhedron.greatened(): Polyhedron = when (regularStarFormOrNull()) {
    RegularStarForm.Dodecahedron -> KeplerPoinsotGeometry.greatDodecahedron
    RegularStarForm.Icosahedron -> KeplerPoinsotGeometry.greatIcosahedron
    RegularStarForm.SmallStellatedDodecahedron -> KeplerPoinsotGeometry.greatStellatedDodecahedron
    else -> throw IllegalArgumentException(
        "Greatening currently requires a regular dodecahedron, icosahedron, or small stellated dodecahedron",
    )
}.scaled(Scale.Circumradius)

fun Polyhedron.stellated(): Polyhedron = when (regularStarFormOrNull()) {
    RegularStarForm.Dodecahedron -> KeplerPoinsotGeometry.smallStellatedDodecahedron
    RegularStarForm.GreatDodecahedron -> KeplerPoinsotGeometry.greatStellatedDodecahedron
    else -> throw IllegalArgumentException(
        "Stellation currently requires a regular dodecahedron or great dodecahedron",
    )
}.scaled(Scale.Circumradius)
