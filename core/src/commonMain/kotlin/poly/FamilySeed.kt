package polyhedra.core.poly

import polyhedra.model.api.FamilySeedId
import polyhedra.model.api.MAX_FAMILY_SEED_N
import polyhedra.model.api.MIN_FAMILY_SEED_N
import polyhedra.model.api.SeedFamily
import polyhedra.model.api.StarFamilySeedId
import polyhedra.model.api.toFamilySeedIdOrNull
import polyhedra.model.api.toStarFamilySeedIdOrNull
import polyhedra.model.poly.FEV
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.VertexKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

val FamilySeeds: List<Seed> = SeedFamily.entries.flatMap { family ->
    (MIN_FAMILY_SEED_N..MAX_FAMILY_SEED_N).map { n -> FamilySeedId(family, n).toSeed() }
}
private val familySeedsByTag = FamilySeeds.associateBy(Seed::tag)

val StarFamilySeeds: List<Seed> = SeedFamily.entries.flatMap { family ->
    (MIN_FAMILY_SEED_N..MAX_FAMILY_SEED_N).flatMap { n ->
        (2..polyhedra.model.api.MAX_STAR_FAMILY_SEED_Q).mapNotNull { q ->
            runCatching { StarFamilySeedId(family, n, q) }.getOrNull()?.toSeed()
        }
    }
}
private val starFamilySeedsByTag = StarFamilySeeds.associateBy(Seed::tag)

fun String.toSeedOrNull(): Seed? =
    Seeds.firstOrNull { it.tag == this }
        ?: toFamilySeedIdOrNull()?.let { id -> familySeedsByTag[id.tag] }
        ?: toStarFamilySeedIdOrNull()?.let { id -> starFamilySeedsByTag[id.tag] }

private fun FamilySeedId.toSeed(): Seed = Seed(
    tag = tag,
    name = toString(),
    type = SeedType.Families,
    fev = fev,
    wikiName = family.displayName,
) {
    poly
}

private fun StarFamilySeedId.toSeed(): Seed = Seed(
    tag = tag,
    name = "${family.displayName} $n/$q",
    type = SeedType.StarFamilies,
    fev = fev,
    wikiName = family.displayName,
) {
    poly
}

private val FamilySeedId.fev: FEV
    get() = when (family) {
        SeedFamily.Prism -> FEV(n + 2, 3 * n, 2 * n)
        SeedFamily.Antiprism -> FEV(2 * n + 2, 4 * n, 2 * n)
        SeedFamily.Pyramid -> FEV(n + 1, 2 * n, n + 1)
        SeedFamily.Bipyramid -> FEV(2 * n, 3 * n, n + 2)
    }

private val StarFamilySeedId.fev: FEV
    get() = when (family) {
        SeedFamily.Prism -> FEV(n + 2, 3 * n, 2 * n)
        SeedFamily.Antiprism -> FEV(2 * n + 2, 4 * n, 2 * n)
        SeedFamily.Pyramid -> FEV(n + 1, 2 * n, n + 1)
        SeedFamily.Bipyramid -> FEV(2 * n, 3 * n, n + 2)
    }

private val FamilySeedId.poly: Polyhedron
    get() = when {
        family == SeedFamily.Prism && n == 4 -> Seed.Cube.poly
        family == SeedFamily.Antiprism && n == 3 -> Seed.Octahedron.poly
        family == SeedFamily.Pyramid && n == 3 -> Seed.Tetrahedron.poly
        family == SeedFamily.Bipyramid && n == 4 -> Seed.Octahedron.poly
        else -> when (family) {
            SeedFamily.Prism -> prism(n, 1)
            SeedFamily.Antiprism -> antiprism(n, 1)
            SeedFamily.Pyramid -> pyramid(n, 1)
            SeedFamily.Bipyramid -> bipyramid(n, 1)
        }
    }

private val StarFamilySeedId.poly: Polyhedron
    get() = when (family) {
        SeedFamily.Prism -> prism(n, q)
        SeedFamily.Antiprism -> antiprism(n, q)
        SeedFamily.Pyramid -> pyramid(n, q)
        SeedFamily.Bipyramid -> bipyramid(n, q)
    }

private fun prism(n: Int, q: Int): Polyhedron = polyhedron(mergeIndistinguishableKinds = true) {
    val s = sin(PI * q / n)
    val radius = 1.0 / sqrt(1.0 + s * s)
    val halfHeight = radius * s
    for (i in 0 until n) {
        val angle = 2.0 * PI * i / n
        vertex(radius * cos(angle), radius * sin(angle), -halfHeight)
    }
    for (i in 0 until n) {
        val angle = 2.0 * PI * i / n
        vertex(radius * cos(angle), radius * sin(angle), halfHeight)
    }

    face(starCycle(n, q), FaceKind(0))
    face(starCycle(n, -q).map { n + it }, FaceKind(0))
    for (i in 0 until n) {
        val next = (i + q) % n
        face(listOf(i, n + i, n + next, next), FaceKind(1))
    }
}

private fun antiprism(n: Int, q: Int): Polyhedron = polyhedron(mergeIndistinguishableKinds = true) {
    val ringSin = sin(PI * q / n)
    val crossSin = sin(PI * q / (2 * n))
    val heightRatio = sqrt(ringSin * ringSin - crossSin * crossSin)
    val radius = 1.0 / sqrt(1.0 + heightRatio * heightRatio)
    val halfHeight = radius * heightRatio
    for (i in 0 until n) {
        val angle = 2.0 * PI * i / n
        vertex(radius * cos(angle), radius * sin(angle), -halfHeight)
    }
    for (i in 0 until n) {
        val angle = 2.0 * PI * (i + q / 2.0) / n
        vertex(radius * cos(angle), radius * sin(angle), halfHeight)
    }

    face(starCycle(n, q), FaceKind(0))
    face(starCycle(n, -q).map { n + it }, FaceKind(0))
    for (i in 0 until n) {
        val next = (i + q) % n
        face(listOf(i, n + i, next), FaceKind(1))
        face(listOf(n + i, n + next, next), FaceKind(1))
    }
}

private fun pyramid(n: Int, q: Int): Polyhedron = polyhedron(mergeIndistinguishableKinds = true) {
    val baseZ = -1.0 / n
    val radius = sqrt(1.0 - baseZ * baseZ)
    for (i in 0 until n) {
        val angle = 2.0 * PI * i / n
        vertex(radius * cos(angle), radius * sin(angle), baseZ, VertexKind(0))
    }
    val apex = vertex(0.0, 0.0, 1.0, VertexKind(1))

    face(starCycle(n, q), FaceKind(0))
    for (i in 0 until n) {
        face(listOf(i, apex.id, (i + q) % n), FaceKind(1))
    }
}

private fun bipyramid(n: Int, q: Int): Polyhedron = polyhedron(mergeIndistinguishableKinds = true) {
    for (i in 0 until n) {
        val angle = 2.0 * PI * i / n
        vertex(cos(angle), sin(angle), 0.0, VertexKind(0))
    }
    val top = vertex(0.0, 0.0, 1.0, VertexKind(1))
    val bottom = vertex(0.0, 0.0, -1.0, VertexKind(1))
    for (i in 0 until n) {
        val next = (i + q) % n
        face(listOf(i, top.id, next), FaceKind(0))
        face(listOf(i, next, bottom.id), FaceKind(0))
    }
}

private fun starCycle(n: Int, q: Int): List<Int> =
    List(n) { index -> ((index * q) % n + n) % n }
