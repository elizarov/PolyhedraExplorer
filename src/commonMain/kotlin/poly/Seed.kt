/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.poly

import polyhedra.common.transform.*
import polyhedra.common.util.*
import kotlin.math.*

enum class SeedType {
    Platonic,
    Arhimedean,
    Catalan
}

private val seedScale = Scale.Circumradius

private typealias SC = Seed.Companion

class Seed(
    override val tag: String,
    val name: String,
    val type: SeedType,
    val fev: FEV,
    val wikiName: String,
    private val producer: SC.() -> Polyhedron
) : Tagged {
    val poly: Polyhedron by lazy { producer().scaled(seedScale) }
    fun wikiURL(): String = "https://en.wikipedia.org/wiki/${wikiName.replace(' ', '_')}"
    override fun toString(): String = name
    companion object
}

val Seeds: List<Seed>
    get() = _seeds

@Suppress("ObjectPropertyName")
private val _seeds = ArrayList<Seed>()

private fun seed(tag: String, type: SeedType, fev: FEV, wikiName: String? = null, producer: SC.() -> Polyhedron) =
    DelegateProvider { propertyName ->
        val name = buildString {
            for ((i, c) in propertyName.withIndex()) {
                if (i > 0 && c in 'A'..'Z') {
                    append(' ')
                    append(c.lowercase())
                } else {
                    append(c)
                }
            }
        }
        val seed = Seed(tag, name, type,fev, wikiName ?: name, producer)
        _seeds += seed
        ValueDelegate(seed)
    }

private fun seed(tag: String, type: SeedType, poly: Polyhedron) =
    seed(tag, type, poly.fev()) { poly }

private fun seed(tag: String, type: SeedType, transform: Transform, base: Seed, wikiName: String? = null) =
    seed(tag, type, transform.fev * base.fev, wikiName) {
        base.poly.transformed(transform)
    }

// --------------------- Basic platonic geometry ---------------------

@Suppress("ObjectPropertyName")
private val _tetrahedron = polyhedron {
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

private val _cube = polyhedron {
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

private val _icosahedron = polyhedron {
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

// --------------------- 5 Platonic Solids ---------------------

val SC.Tetrahedron by seed("T", SeedType.Platonic, _tetrahedron)
val SC.Cube by seed("C", SeedType.Platonic, _cube)
val SC.Octahedron by seed("O", SeedType.Platonic, _cube.dual())
val SC.Dodecahedron by seed("D", SeedType.Platonic, _icosahedron.dual())
val SC.Icosahedron by seed("I", SeedType.Platonic, _icosahedron)

// --------------------- 13 Arhimedean Solids ---------------------

val SC.TruncatedTetrahedron by seed("tT", SeedType.Arhimedean, Transform.Truncated, SC.Tetrahedron)
val SC.Cuboctahedron by seed("aC", SeedType.Arhimedean, Transform.Rectified, SC.Cube)
val SC.TruncatedCube by seed("tC", SeedType.Arhimedean, Transform.Truncated, SC.Cube)
val SC.TruncatedOctahedron by seed("tO", SeedType.Arhimedean, Transform.Truncated, SC.Octahedron)
val SC.Rhombicuboctahedron by seed("eC", SeedType.Arhimedean, Transform.Cantellated, SC.Cube)
val SC.RhombitruncatedCuboctahedron by seed("bC", SeedType.Arhimedean, Transform.Bevelled, SC.Cube, "Truncated cuboctahedron")
val SC.SnubCube by seed("sC", SeedType.Arhimedean, Transform.Snub, SC.Cube)
val SC.Icosidodecahedron by seed("aD", SeedType.Arhimedean, Transform.Rectified, SC.Dodecahedron)
val SC.TruncatedDodecahedron by seed("tD", SeedType.Arhimedean, Transform.Truncated, SC.Dodecahedron)
val SC.TruncatedIcosahedron by seed("tI", SeedType.Arhimedean, Transform.Truncated, SC.Icosahedron)
val SC.Rhombicosidodecahedron by seed("eD", SeedType.Arhimedean, Transform.Cantellated, SC.Dodecahedron)
val SC.RhombitruncatedIcosidodecahedron by seed("bD", SeedType.Arhimedean, Transform.Bevelled, SC.Dodecahedron, "Truncated icosidodecahedron")
val SC.SnubDodecahedron by seed("sD", SeedType.Arhimedean, Transform.Snub, SC.Dodecahedron)