package polyhedra.web.catalog

import polyhedra.model.api.FamilySeedId
import polyhedra.model.api.MAX_FAMILY_SEED_N
import polyhedra.model.api.MIN_FAMILY_SEED_N
import polyhedra.model.api.SeedFamily
import polyhedra.model.api.toTransformMacroOrNull
import polyhedra.model.poly.AnyKind
import polyhedra.model.poly.Chirality
import polyhedra.model.poly.EdgeKind
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.toAnyKindOrNull
import polyhedra.model.poly.withChirality
import polyhedra.model.util.Tagged

enum class SeedType {
    Platonic,
    Families,
    Archimedean,
    Catalan,
}

data class Seed(
    val baseTag: String,
    val name: String,
    val type: SeedType,
    val chirality: Chirality? = null,
    val familyId: FamilySeedId? = null,
) : Tagged {
    init {
        require((type == SeedType.Families) == (familyId != null))
        require(familyId == null || baseTag == familyId.tag)
    }

    override val tag: String get() = baseTag.withChirality(chirality)
    val isChiral: Boolean get() = chirality != null
    val isFamily: Boolean get() = familyId != null
    override fun toString(): String = (familyId?.toString() ?: name) + chirality?.suffix.orEmpty()

    companion object {
        val Tetrahedron = Seed("T", "Tetrahedron", SeedType.Platonic)
    }
}

val FamilySeeds: List<Seed> = SeedFamily.entries.flatMap { family ->
    (MIN_FAMILY_SEED_N..MAX_FAMILY_SEED_N).map { n ->
        val id = FamilySeedId(family, n)
        Seed(id.tag, family.displayName, SeedType.Families, familyId = id)
    }
}
private val FamilySeedsById: Map<FamilySeedId, Seed> =
    FamilySeeds.associateBy { requireNotNull(it.familyId) }

private val DefaultFamilySeedOptions: List<Seed> = SeedFamily.entries.map { family ->
    FamilySeedsById.getValue(FamilySeedId(family, MIN_FAMILY_SEED_N))
}

val SeedOptions: List<Seed> = listOf(
    Seed.Tetrahedron,
    Seed("C", "Cube", SeedType.Platonic),
    Seed("O", "Octahedron", SeedType.Platonic),
    Seed("D", "Dodecahedron", SeedType.Platonic),
    Seed("I", "Icosahedron", SeedType.Platonic),
) + DefaultFamilySeedOptions + listOf(
    Seed("tT", "Truncated tetrahedron", SeedType.Archimedean),
    Seed("aC", "Cuboctahedron", SeedType.Archimedean),
    Seed("tC", "Truncated cube", SeedType.Archimedean),
    Seed("tO", "Truncated octahedron", SeedType.Archimedean),
    Seed("eC", "Rhombicuboctahedron", SeedType.Archimedean),
    Seed("bC", "Rhombitruncated cuboctahedron", SeedType.Archimedean),
    Seed("sC", "Snub cube", SeedType.Archimedean, Chirality.Default),
    Seed("aD", "Icosidodecahedron", SeedType.Archimedean),
    Seed("tD", "Truncated dodecahedron", SeedType.Archimedean),
    Seed("tI", "Truncated icosahedron", SeedType.Archimedean),
    Seed("eD", "Rhombicosidodecahedron", SeedType.Archimedean),
    Seed("bD", "Rhombitruncated icosidodecahedron", SeedType.Archimedean),
    Seed("sD", "Snub dodecahedron", SeedType.Archimedean, Chirality.Default),
    Seed("dtT", "Triakis tetrahedron", SeedType.Catalan),
    Seed("daC", "Rhombic dodecahedron", SeedType.Catalan),
    Seed("dtC", "Triakis octahedron", SeedType.Catalan),
    Seed("dtO", "Tetrakis hexahedron", SeedType.Catalan),
    Seed("deC", "Deltoidal icositetrahedron", SeedType.Catalan),
    Seed("dbC", "Disdyakis dodecahedron", SeedType.Catalan),
    Seed("dsC", "Pentagonal icositetrahedron", SeedType.Catalan, Chirality.Default),
    Seed("daD", "Rhombic triacontahedron", SeedType.Catalan),
    Seed("dtD", "Triakis icosahedron", SeedType.Catalan),
    Seed("dtI", "Pentakis dodecahedron", SeedType.Catalan),
    Seed("deD", "Deltoidal hexecontahedron", SeedType.Catalan),
    Seed("dbD", "Disdyakis triacontahedron", SeedType.Catalan),
    Seed("dsD", "Pentagonal hexecontahedron", SeedType.Catalan, Chirality.Default),
)

val Seeds: List<Seed> = SeedOptions.filterNot(Seed::isFamily).flatMap { seed ->
    if (seed.isChiral) listOf(seed, seed.copy(chirality = Chirality.Flipped)) else listOf(seed)
} + FamilySeeds

fun Seed.withFamilyN(n: Int): Seed {
    val family = requireNotNull(familyId).family
    return FamilySeedsById.getValue(FamilySeedId(family, n))
}

val Seed.optionKey: String
    get() = familyId?.family?.tagPrefix ?: baseTag

fun Seed.flippedChirality(): Seed {
    val flipped = requireNotNull(chirality).flipped()
    return Seeds.single { seed -> seed.baseTag == baseTag && seed.chirality == flipped }
}

data class Transform(
    val baseTag: String,
    val name: String,
    val category: TransformCategory = TransformCategory.Transform,
    val chirality: Chirality? = null,
) : Tagged {
    override val tag: String get() = baseTag.withChirality(chirality)
    val isChiral: Boolean get() = chirality != null
    override fun toString(): String = name + chirality?.suffix.orEmpty()

    companion object {
        val None = Transform("n", "None")
        val Truncated = Transform("t", "Truncated")
        val Rectified = Transform("a", "Rectified")
        val Dual = Transform("d", "Dual")
        val Snub = Transform("s", "Snub", chirality = Chirality.Default)
        val SnubFlipped = Snub.copy(chirality = Chirality.Flipped)
        val Propeller = Transform("p", "Propeller", chirality = Chirality.Default)
        val PropellerFlipped = Propeller.copy(chirality = Chirality.Flipped)
        val Whirl = Transform("w", "Whirl", chirality = Chirality.Default)
        val WhirlFlipped = Whirl.copy(chirality = Chirality.Flipped)
        val Quinto = Transform("q", "Quinto")
        val Chamfered = Transform("c", "Chamfered")
        val Canonical = Transform("o", "Canonical")

        val Kis = macro("k")
        val Join = macro("j")
        val Needle = macro("N")
        val Zip = macro("z")
        val Cantellated = macro("e")
        val Bevelled = macro("b")
        val Ortho = macro("O")
        val Meta = macro("m")
        val Gyro = macro("g")
        val GyroFlipped = macro("g'")

        private fun macro(tag: String): Transform {
            val macro = requireNotNull(tag.toTransformMacroOrNull())
            return Transform(
                macro.tag.removeSuffix("'"),
                macro.name,
                TransformCategory.Macro,
                macro.chirality,
            )
        }
    }
}

enum class TransformCategory(private val displayName: String) {
    Transform("Transform"),
    Macro("Macro"),
    OrbitTargeted("Orbit-targeted"),
    ;

    override fun toString(): String = displayName
}

enum class OrbitTargetedOperation(
    val optionName: String,
    val iconClass: String,
) {
    DropFace("Drop face", "fa-remove"),
    DropEdge("Drop edge", "fa-remove"),
    DropVertex("Drop vertex", "fa-remove"),
    KisFace("Kis face", "fa-caret-up"),
    TruncateVertex("Truncate vertex", "fa-scissors"),
    RectifyVertex("Rectify vertex", "fa-compress");
}

val PrimitiveTransforms: List<Transform> = listOf(
    Transform.None,
    Transform.Truncated,
    Transform.Rectified,
    Transform.Dual,
    Transform.Snub,
    Transform.Propeller,
    Transform.Whirl,
    Transform.Quinto,
    Transform.Chamfered,
    Transform.Canonical,
)

val MacroTransforms: List<Transform> = listOf(
    Transform.Kis,
    Transform.Join,
    Transform.Needle,
    Transform.Zip,
    Transform.Cantellated,
    Transform.Bevelled,
    Transform.Ortho,
    Transform.Meta,
    Transform.Gyro,
)

val TransformOptions: List<Transform> = PrimitiveTransforms + MacroTransforms

val Transforms: List<Transform> = TransformOptions.flatMap { transform ->
    if (transform.isChiral) {
        listOf(transform, transform.copy(chirality = Chirality.Flipped))
    } else {
        listOf(transform)
    }
}

fun Transform.flippedChirality(): Transform {
    val flipped = requireNotNull(chirality).flipped()
    return Transforms.single { transform -> transform.baseTag == baseTag && transform.chirality == flipped }
}

private const val DROP_TAG = "x"
private const val KIS_FACE_TAG = "k"
private const val TRUNCATE_VERTEX_TAG = "t"
private const val RECTIFY_VERTEX_TAG = "a"

fun Drop(kind: AnyKind): Transform =
    Transform("$DROP_TAG[$kind]", "Drop $kind", TransformCategory.OrbitTargeted)

fun KisFace(kind: FaceKind): Transform =
    Transform("$KIS_FACE_TAG[$kind]", "Kis $kind", TransformCategory.OrbitTargeted)

fun TruncateVertex(kind: VertexKind): Transform =
    Transform("$TRUNCATE_VERTEX_TAG[$kind]", "Truncate $kind", TransformCategory.OrbitTargeted)

fun RectifyVertex(kind: VertexKind): Transform =
    Transform("$RECTIFY_VERTEX_TAG[$kind]", "Rectify $kind", TransformCategory.OrbitTargeted)

data class OrbitTarget(
    val operation: OrbitTargetedOperation,
    val kind: AnyKind,
)

fun Transform.orbitTargetOrNull(): OrbitTarget? {
    if (!baseTag.endsWith("]")) return null
    val bracket = baseTag.indexOf('[')
    if (bracket <= 0) return null
    val prefix = baseTag.substring(0, bracket)
    val kind = baseTag.substring(bracket + 1, baseTag.length - 1).toAnyKindOrNull() ?: return null
    val operation = when {
        prefix == DROP_TAG && kind is FaceKind -> OrbitTargetedOperation.DropFace
        prefix == KIS_FACE_TAG && kind is FaceKind -> OrbitTargetedOperation.KisFace
        prefix == DROP_TAG && kind is EdgeKind -> OrbitTargetedOperation.DropEdge
        prefix == DROP_TAG && kind is VertexKind -> OrbitTargetedOperation.DropVertex
        prefix == TRUNCATE_VERTEX_TAG && kind is VertexKind -> OrbitTargetedOperation.TruncateVertex
        prefix == RECTIFY_VERTEX_TAG && kind is VertexKind -> OrbitTargetedOperation.RectifyVertex
        else -> return null
    }
    return OrbitTarget(operation, kind)
}

fun String.toTransformOrNull(): Transform? {
    Transforms.firstOrNull { it.tag == this }?.let { return it }
    val placeholder = Transform(this, "", TransformCategory.OrbitTargeted)
    val target = placeholder.orbitTargetOrNull() ?: return null
    return when (target.operation) {
        OrbitTargetedOperation.DropFace,
        OrbitTargetedOperation.DropEdge,
        OrbitTargetedOperation.DropVertex -> Drop(target.kind)
        OrbitTargetedOperation.KisFace -> KisFace(target.kind as FaceKind)
        OrbitTargetedOperation.TruncateVertex -> TruncateVertex(target.kind as VertexKind)
        OrbitTargetedOperation.RectifyVertex -> RectifyVertex(target.kind as VertexKind)
    }
}
