/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core.poly

import polyhedra.core.transform.*
import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.math.*

enum class SeedType {
    Platonic,
    Families,
    KeplerPoinsot,
    Archimedean,
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
    val chirality: Chirality? = null,
    private val producer: SC.() -> Polyhedron
) : Tagged {
    val poly: Polyhedron by lazy { producer().scaled(seedScale) }
    internal val geometryFingerprint: PolyhedronGeometryFingerprint by lazy { poly.geometryFingerprint() }
    fun wikiURL(): String = "https://en.wikipedia.org/wiki/${wikiName.replace(' ', '_')}"
    override fun toString(): String = name + chirality?.suffix.orEmpty()
    companion object
}

fun Polyhedron.recognizedSeedOrNull(): Seed? {
    val candidates = Seeds.filter { seed -> seed.fev == fev() }
    if (candidates.isEmpty()) return null
    val geometryFingerprint = geometryFingerprint()
    return candidates.firstOrNull { seed -> geometryFingerprint.matches(seed.geometryFingerprint) }
}

private val registeredSeeds = mutableListOf<Seed>()

private fun seed(
    tag: String,
    type: SeedType,
    fev: FEV,
    wikiName: String? = null,
    chirality: Chirality? = null,
    producer: SC.() -> Polyhedron,
) =
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
        val seed = Seed(tag, name, type, fev, wikiName ?: name, chirality, producer)
        registeredSeeds += seed
        ValueDelegate(seed)
    }

private fun seed(tag: String, type: SeedType, poly: Polyhedron) =
    seed(tag, type, poly.fev()) { poly }

private fun seed(
    tag: String,
    type: SeedType,
    transform: Transform,
    base: Seed,
    wikiName: String? = null,
    chirality: Chirality? = null,
) = seed(tag, type, transform.fev!! * base.fev, wikiName, chirality) {
    base.poly.transformed(transform)
}

private fun seed(
    type: SeedType,
    transform: Transform,
    base: Seed,
    wikiName: String? = null,
    chirality: Chirality? = null,
) = seed(transform.tag + base.tag, type, transform, base, wikiName, chirality)

// --------------------- Basic platonic geometry ---------------------

private val tetrahedronGeometry = polyhedron {
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

private val cubeGeometry = polyhedron {
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

internal val icosahedronGeometry = polyhedron {
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

internal val dodecahedronGeometry = icosahedronGeometry.dual()

// --------------------- 5 Platonic Solids ---------------------

val SC.Tetrahedron by seed("T", SeedType.Platonic, tetrahedronGeometry)
val SC.Cube by seed("C", SeedType.Platonic, cubeGeometry)
val SC.Octahedron by seed("O", SeedType.Platonic, cubeGeometry.dual())
val SC.Dodecahedron by seed("D", SeedType.Platonic, dodecahedronGeometry)
val SC.Icosahedron by seed("I", SeedType.Platonic, icosahedronGeometry)

// --------------------- 4 Kepler-Poinsot Solids ---------------------

// Uppercase storage tags avoid the historical sD collision with Snub dodecahedron. Their Conway
// notations (sD, gD, sgD/gsD, and gI) are shown in the catalog and documented as the public names.
val SC.SmallStellatedDodecahedron by seed(
    "SD",
    SeedType.KeplerPoinsot,
    FEV(60, 90, 32),
    wikiName = "Small stellated dodecahedron",
) { KeplerPoinsotGeometry.smallStellatedDodecahedron }
val SC.GreatDodecahedron by seed(
    "GD",
    SeedType.KeplerPoinsot,
    FEV(60, 90, 32),
    wikiName = "Great dodecahedron",
) { KeplerPoinsotGeometry.greatDodecahedron }
val SC.GreatStellatedDodecahedron by seed(
    "GSD",
    SeedType.KeplerPoinsot,
    FEV(60, 90, 32),
    wikiName = "Great stellated dodecahedron",
) { KeplerPoinsotGeometry.greatStellatedDodecahedron }
val SC.GreatIcosahedron by seed(
    "GI",
    SeedType.KeplerPoinsot,
    FEV(180, 270, 92),
    wikiName = "Great icosahedron",
) { KeplerPoinsotGeometry.greatIcosahedron }

// --------------------- 13 Archimedean Solids ---------------------

val SC.TruncatedTetrahedron by seed("tT", SeedType.Archimedean, Transform.Truncated, SC.Tetrahedron)
val SC.Cuboctahedron by seed("aC", SeedType.Archimedean, Transform.Rectified, SC.Cube)
val SC.TruncatedCube by seed("tC", SeedType.Archimedean, Transform.Truncated, SC.Cube)
val SC.TruncatedOctahedron by seed("tO", SeedType.Archimedean, Transform.Truncated, SC.Octahedron)
val SC.Rhombicuboctahedron by seed("eC", SeedType.Archimedean, Transform.Cantellated, SC.Cube)
val SC.RhombitruncatedCuboctahedron by seed("bC", SeedType.Archimedean, Transform.Bevelled, SC.Cube, "Truncated cuboctahedron")
val SC.SnubCube by seed("sC", SeedType.Archimedean, Transform.Snub, SC.Cube, chirality = Chirality.Default)
val SC.Icosidodecahedron by seed("aD", SeedType.Archimedean, Transform.Rectified, SC.Dodecahedron)
val SC.TruncatedDodecahedron by seed("tD", SeedType.Archimedean, Transform.Truncated, SC.Dodecahedron)
val SC.TruncatedIcosahedron by seed("tI", SeedType.Archimedean, Transform.Truncated, SC.Icosahedron)
val SC.Rhombicosidodecahedron by seed("eD", SeedType.Archimedean, Transform.Cantellated, SC.Dodecahedron)
val SC.RhombitruncatedIcosidodecahedron by seed("bD", SeedType.Archimedean, Transform.Bevelled, SC.Dodecahedron, "Truncated icosidodecahedron")
val SC.SnubDodecahedron by seed(
    "sD",
    SeedType.Archimedean,
    Transform.Snub,
    SC.Dodecahedron,
    chirality = Chirality.Default,
)

// --------------------- 13 Catalan Solids ---------------------

val SC.TriakisTetrahedron by seed(SeedType.Catalan, Transform.Dual, SC.TruncatedTetrahedron)
val SC.RhombicDodecahedron by seed(SeedType.Catalan, Transform.Dual, SC.Cuboctahedron)
val SC.TriakisOctahedron by seed(SeedType.Catalan, Transform.Dual, SC.TruncatedCube)
val SC.TetrakisHexahedron by seed(SeedType.Catalan, Transform.Dual, SC.TruncatedOctahedron)
val SC.DeltoidalIcositetrahedron by seed(SeedType.Catalan, Transform.Dual, SC.Rhombicuboctahedron)
val SC.DisdyakisDodecahedron by seed(SeedType.Catalan, Transform.Dual, SC.RhombitruncatedCuboctahedron)
val SC.PentagonalIcositetrahedron by seed(
    SeedType.Catalan,
    Transform.Dual,
    SC.SnubCube,
    chirality = Chirality.Default,
)
val SC.RhombicTriacontahedron by seed(SeedType.Catalan, Transform.Dual, SC.Icosidodecahedron)
val SC.TriakisIcosahedron by seed(SeedType.Catalan, Transform.Dual, SC.TruncatedDodecahedron)
val SC.PentakisDodecahedron by seed(SeedType.Catalan, Transform.Dual, SC.TruncatedIcosahedron)
val SC.DeltoidalHexecontahedron by seed(SeedType.Catalan, Transform.Dual, SC.Rhombicosidodecahedron)
val SC.DisdyakisTriacontahedron by seed(SeedType.Catalan, Transform.Dual, SC.RhombitruncatedIcosidodecahedron)
val SC.PentagonalHexecontahedron by seed(
    SeedType.Catalan,
    Transform.Dual,
    SC.SnubDodecahedron,
    chirality = Chirality.Default,
)

val Seeds: List<Seed> = registeredSeeds + listOf(
    SC.SnubCube,
    SC.SnubDodecahedron,
    SC.PentagonalIcositetrahedron,
    SC.PentagonalHexecontahedron,
).map { seed ->
    Seed(
        tag = seed.tag.withChirality(Chirality.Flipped),
        name = seed.name,
        type = seed.type,
        fev = seed.fev,
        wikiName = seed.wikiName,
        chirality = Chirality.Flipped,
    ) {
        seed.poly.reflected()
    }
}
