package polyhedra.js.catalog

import polyhedra.common.poly.AnyKind
import polyhedra.common.poly.toAnyKindOrNull
import polyhedra.common.util.Tagged

enum class SeedType {
    Platonic,
    Archimedean,
    Catalan,
}

data class Seed(
    override val tag: String,
    val name: String,
    val type: SeedType,
) : Tagged {
    override fun toString(): String = name

    companion object {
        val Tetrahedron = Seed("T", "Tetrahedron", SeedType.Platonic)
    }
}

val Seeds: List<Seed> = listOf(
    Seed.Tetrahedron,
    Seed("C", "Cube", SeedType.Platonic),
    Seed("O", "Octahedron", SeedType.Platonic),
    Seed("D", "Dodecahedron", SeedType.Platonic),
    Seed("I", "Icosahedron", SeedType.Platonic),
    Seed("tT", "Truncated tetrahedron", SeedType.Archimedean),
    Seed("aC", "Cuboctahedron", SeedType.Archimedean),
    Seed("tC", "Truncated cube", SeedType.Archimedean),
    Seed("tO", "Truncated octahedron", SeedType.Archimedean),
    Seed("eC", "Rhombicuboctahedron", SeedType.Archimedean),
    Seed("bC", "Rhombitruncated cuboctahedron", SeedType.Archimedean),
    Seed("sC", "Snub cube", SeedType.Archimedean),
    Seed("aD", "Icosidodecahedron", SeedType.Archimedean),
    Seed("tD", "Truncated dodecahedron", SeedType.Archimedean),
    Seed("tI", "Truncated icosahedron", SeedType.Archimedean),
    Seed("eD", "Rhombicosidodecahedron", SeedType.Archimedean),
    Seed("bD", "Rhombitruncated icosidodecahedron", SeedType.Archimedean),
    Seed("sD", "Snub dodecahedron", SeedType.Archimedean),
    Seed("dtT", "Triakis tetrahedron", SeedType.Catalan),
    Seed("daC", "Rhombic dodecahedron", SeedType.Catalan),
    Seed("dtC", "Triakis octahedron", SeedType.Catalan),
    Seed("dtO", "Tetrakis hexahedron", SeedType.Catalan),
    Seed("deC", "Deltoidal icositetrahedron", SeedType.Catalan),
    Seed("dbC", "Disdyakis dodecahedron", SeedType.Catalan),
    Seed("dsC", "Pentagonal icositetrahedron", SeedType.Catalan),
    Seed("daD", "Rhombic triacontahedron", SeedType.Catalan),
    Seed("dtD", "Triakis icosahedron", SeedType.Catalan),
    Seed("dtI", "Pentakis dodecahedron", SeedType.Catalan),
    Seed("deD", "Deltoidal hexecontahedron", SeedType.Catalan),
    Seed("dbD", "Disdyakis triacontahedron", SeedType.Catalan),
    Seed("dsD", "Pentagonal hexecontahedron", SeedType.Catalan),
)

data class Transform(
    override val tag: String,
    val name: String,
) : Tagged {
    override fun toString(): String = name

    companion object {
        val None = Transform("n", "None")
        val Truncated = Transform("t", "Truncated")
        val Rectified = Transform("a", "Rectified")
        val Cantellated = Transform("e", "Cantellated")
        val Dual = Transform("d", "Dual")
        val Bevelled = Transform("b", "Bevelled")
        val Snub = Transform("s", "Snub")
        val Chamfered = Transform("c", "Chamfered")
        val Canonical = Transform("o", "Canonical")
    }
}

val Transforms: List<Transform> = listOf(
    Transform.None,
    Transform.Truncated,
    Transform.Rectified,
    Transform.Cantellated,
    Transform.Dual,
    Transform.Bevelled,
    Transform.Snub,
    Transform.Chamfered,
    Transform.Canonical,
)

private const val DROP_TAG = "x"

fun Drop(kind: AnyKind): Transform = Transform("$DROP_TAG[$kind]", "Drop $kind")

fun String.toTransformOrNull(): Transform? {
    Transforms.firstOrNull { it.tag == this }?.let { return it }
    if (!startsWith("$DROP_TAG[") || !endsWith("]")) return null
    val kind = substring(DROP_TAG.length + 1, length - 1).toAnyKindOrNull() ?: return null
    return Drop(kind)
}
