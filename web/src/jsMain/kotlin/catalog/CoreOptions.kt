package polyhedra.web.catalog

import polyhedra.model.api.*
import polyhedra.model.poly.AnyKind
import polyhedra.model.poly.Chirality
import polyhedra.model.poly.EdgeKind
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.withChirality
import polyhedra.model.util.Tagged

enum class SeedType(private val displayName: String) {
    Platonic("Platonic"),
    Families("Families"),
    Archimedean("Archimedean"),
    Catalan("Catalan"),
    StarFamilies("Star families"),
    RegularCompounds("Regular compounds"),
    KeplerPoinsot("Kepler-Poinsot"),
    ;

    override fun toString(): String = displayName
}

data class Seed(
    val baseTag: String,
    val name: String,
    val type: SeedType,
    val chirality: Chirality? = null,
    val familyId: FamilySeedId? = null,
    val starFamilyId: StarFamilySeedId? = null,
) : Tagged {
    init {
        require((type == SeedType.Families) == (familyId != null))
        require((type == SeedType.StarFamilies) == (starFamilyId != null))
        require(familyId == null || starFamilyId == null)
        require(familyId == null || baseTag == familyId.tag)
        require(starFamilyId == null || baseTag == starFamilyId.tag)
    }

    override val tag: String get() = baseTag.withChirality(chirality)
    val isChiral: Boolean get() = chirality != null
    val isFamily: Boolean get() = familyId != null || starFamilyId != null
    val isStarFamily: Boolean get() = starFamilyId != null
    override fun toString(): String =
        (familyId?.toString() ?: starFamilyId?.toString() ?: name) + chirality?.suffix.orEmpty()

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

val StarFamilySeeds: List<Seed> = SeedFamily.entries.flatMap { family ->
    (MIN_FAMILY_SEED_N..MAX_FAMILY_SEED_N).flatMap { n ->
        (2..MAX_STAR_FAMILY_SEED_Q).mapNotNull { q ->
            runCatching { StarFamilySeedId(family, n, q) }.getOrNull()?.let { id ->
                Seed(
                    id.tag,
                    "Star ${family.displayName.lowercase()}",
                    SeedType.StarFamilies,
                    starFamilyId = id,
                )
            }
        }
    }
}
private val StarFamilySeedsById: Map<StarFamilySeedId, Seed> =
    StarFamilySeeds.associateBy { requireNotNull(it.starFamilyId) }

private val DefaultStarFamilySeedOptions: List<Seed> = SeedFamily.entries.map { family ->
    StarFamilySeedsById.getValue(
        StarFamilySeedId(family, DEFAULT_STAR_FAMILY_SEED_N, DEFAULT_STAR_FAMILY_SEED_Q)
    )
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
) + DefaultStarFamilySeedOptions + listOf(
    Seed("C2T", "Two tetrahedra", SeedType.RegularCompounds),
    Seed("C5T", "Five tetrahedra", SeedType.RegularCompounds, Chirality.Default),
    Seed("C10T", "Ten tetrahedra", SeedType.RegularCompounds),
    Seed("C5C", "Five cubes", SeedType.RegularCompounds),
    Seed("C5O", "Five octahedra", SeedType.RegularCompounds),

    Seed("SD", "Stellated dodecahedron", SeedType.KeplerPoinsot),
    Seed("GD", "Great dodecahedron", SeedType.KeplerPoinsot),
    Seed("GSD", "Great stellated dodecahedron", SeedType.KeplerPoinsot),
    Seed("GI", "Great icosahedron", SeedType.KeplerPoinsot),
)

val Seeds: List<Seed> = SeedOptions.filterNot(Seed::isFamily).flatMap { seed ->
    if (seed.isChiral) listOf(seed, seed.copy(chirality = Chirality.Flipped)) else listOf(seed)
} + FamilySeeds + StarFamilySeeds

fun Seed.withFamilyN(n: Int): Seed {
    val family = requireNotNull(familyId).family
    return FamilySeedsById.getValue(FamilySeedId(family, n))
}

fun Seed.withStarFamilyValues(n: Int, q: Int): Seed {
    val family = requireNotNull(starFamilyId).family
    return StarFamilySeedsById.getValue(StarFamilySeedId(family, n, q))
}

fun Seed.withStarFamilyId(id: StarFamilySeedId): Seed =
    StarFamilySeedsById.getValue(id.copy(family = requireNotNull(starFamilyId).family))

val Seed.optionKey: String
    get() = familyId?.family?.tagPrefix ?: starFamilyId?.family?.starTagPrefix ?: baseTag

fun Seed.flippedChirality(): Seed {
    val flipped = requireNotNull(chirality).flipped()
    return Seeds.single { seed -> seed.baseTag == baseTag && seed.chirality == flipped }
}

data class Transform(
    val id: TransformId,
    val name: String,
    val category: TransformCategory = TransformCategory.Transform,
    val tweaks: Map<TransformTweak, Double> = emptyMap(),
) : Tagged {
    val spec: TransformSpec get() = TransformSpec(id, tweaks)
    val operation: TransformOperation get() = id.operation
    val chirality: Chirality? get() = id.chirality
    override val tag: String get() = spec.tag
    val isChiral: Boolean get() = operation.isChiral
    override fun toString(): String = name + id.chirality?.suffix.orEmpty()

    companion object {
        val None = transform(TransformOperation.None, "None")
        val Truncated = transform(TransformOperation.Truncated, "Truncated")
        val Rectified = transform(TransformOperation.Rectified, "Rectified")
        val Dual = transform(TransformOperation.Dual, "Dual")
        val Snub = transform(TransformOperation.Snub, "Snub")
        val SnubFlipped = Snub.copy(id = Snub.id.flippedChirality())
        val Propeller = transform(TransformOperation.Propeller, "Propeller")
        val PropellerFlipped = Propeller.copy(id = Propeller.id.flippedChirality())
        val Whirl = transform(TransformOperation.Whirl, "Whirl")
        val WhirlFlipped = Whirl.copy(id = Whirl.id.flippedChirality())
        val Quinto = transform(TransformOperation.Quinto, "Quinto")
        val Chamfered = transform(TransformOperation.Chamfered, "Chamfered")
        val Canonical = transform(TransformOperation.Canonical, "Canonical")
        val Greatened = transform(TransformOperation.Greatened, "Greatened", TransformCategory.Star)
        val Stellated = transform(TransformOperation.Stellated, "Stellated", TransformCategory.Star)
        val Resolved = transform(TransformOperation.Resolved, "Resolved", TransformCategory.Star)

        val Kis = macro(TransformOperation.Kis)
        val Join = macro(TransformOperation.Join)
        val Needle = macro(TransformOperation.Needle)
        val Zip = macro(TransformOperation.Zip)
        val Cantellated = macro(TransformOperation.Cantellated)
        val Bevelled = macro(TransformOperation.Bevelled)
        val Ortho = macro(TransformOperation.Ortho)
        val Meta = macro(TransformOperation.Meta)
        val Gyro = macro(TransformOperation.Gyro)
        val GyroFlipped = Gyro.copy(id = Gyro.id.flippedChirality())

        private fun transform(
            operation: TransformOperation,
            name: String,
            category: TransformCategory = TransformCategory.Transform,
        ): Transform = Transform(TransformId(operation), name, category)

        private fun macro(operation: TransformOperation): Transform {
            val macro = TransformMacros.single { it.id.operation == operation }
            return Transform(
                macro.id,
                macro.name,
                TransformCategory.Macro,
            )
        }
    }
}

enum class TransformCategory(private val displayName: String) {
    Transform("Transform"),
    Macro("Macro"),
    OrbitTargeted("Orbit-targeted"),
    Star("Star"),
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
    StellateFace("Stellate face", "fa-star"),
    TruncateVertex("Truncate vertex", "fa-scissors"),
    RectifyVertex("Rectify vertex", "fa-compress"),
    RadialVertex("Radial vertex", "fa-arrows-v");
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
    Transform.Greatened,
    Transform.Stellated,
    Transform.Resolved,
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

val TransformOptions: List<Transform> = (PrimitiveTransforms + MacroTransforms)
    .sortedBy { transform -> transform.category.ordinal }

val Transforms: List<Transform> = TransformOptions.flatMap { transform ->
    if (transform.isChiral) {
        listOf(transform, transform.copy(id = transform.id.flippedChirality()))
    } else {
        listOf(transform)
    }
}

fun Transform.flippedChirality(): Transform {
    val flippedId = id.flippedChirality()
    return Transforms.single { transform -> transform.id == flippedId }
        .copy(tweaks = tweaks)
}

data class TransformSetting(
    val tweak: TransformTweak,
    val label: String,
    val min: Double,
    val max: Double,
    val step: Double = 0.01,
)

val Transform.settings: List<TransformSetting>
    get() = id.transformTweakRanges().map { (tweak, range) ->
        TransformSetting(
            tweak,
            if (tweak == TransformTweak.StellationResult) "Result" else tweak.name,
            range.min,
            range.max,
            step = if (tweak == TransformTweak.StellationResult) 1.0 else 0.01,
        )
    }

fun Transform.withTweak(tweak: TransformTweak, value: Double): Transform =
    copy(tweaks = tweaks.toMutableMap().apply {
        val setting = this@withTweak.settings.single { it.tweak == tweak }
        val boundedValue = value.coerceIn(setting.min, setting.max)
        if (boundedValue == 1.0) remove(tweak) else put(tweak, boundedValue)
    })

fun Transform.withoutTweaks(): Transform =
    if (tweaks.isEmpty()) this else copy(tweaks = emptyMap())

fun Transform.withDefaultSettings(): Transform =
    copy(
        id = if (isChiral) id.copy(chirality = Chirality.Default) else id,
        tweaks = emptyMap(),
    )

fun Drop(kind: AnyKind): Transform =
    Transform(TransformId(TransformOperation.Drop, target = kind), "Drop $kind", TransformCategory.OrbitTargeted)

fun KisFace(kind: FaceKind): Transform =
    Transform(TransformId(TransformOperation.Kis, target = kind), "Kis $kind", TransformCategory.OrbitTargeted)

fun TruncateVertex(kind: VertexKind): Transform =
    Transform(
        TransformId(TransformOperation.Truncated, target = kind),
        "Truncate $kind",
        TransformCategory.OrbitTargeted,
    )

fun RectifyVertex(kind: VertexKind): Transform =
    Transform(
        TransformId(TransformOperation.Rectified, target = kind),
        "Rectify $kind",
        TransformCategory.OrbitTargeted,
    )

fun RadialVertex(kind: VertexKind): Transform =
    Transform(
        TransformId(TransformOperation.Radial, target = kind),
        "Radial $kind",
        TransformCategory.OrbitTargeted,
    )

fun StellateFace(kind: FaceKind): Transform =
    Transform(
        TransformId(TransformOperation.StellateFace, target = kind),
        "Stellate $kind",
        TransformCategory.OrbitTargeted,
    )

data class OrbitTarget(
    val operation: OrbitTargetedOperation,
    val kind: AnyKind,
)

fun Transform.orbitTargetOrNull(): OrbitTarget? {
    val kind = id.target ?: return null
    val operation = when {
        id.operation == TransformOperation.Drop && kind is FaceKind -> OrbitTargetedOperation.DropFace
        id.operation == TransformOperation.Kis && kind is FaceKind -> OrbitTargetedOperation.KisFace
        id.operation == TransformOperation.StellateFace && kind is FaceKind -> OrbitTargetedOperation.StellateFace
        id.operation == TransformOperation.Drop && kind is EdgeKind -> OrbitTargetedOperation.DropEdge
        id.operation == TransformOperation.Drop && kind is VertexKind -> OrbitTargetedOperation.DropVertex
        id.operation == TransformOperation.Truncated && kind is VertexKind -> OrbitTargetedOperation.TruncateVertex
        id.operation == TransformOperation.Rectified && kind is VertexKind -> OrbitTargetedOperation.RectifyVertex
        id.operation == TransformOperation.Radial && kind is VertexKind -> OrbitTargetedOperation.RadialVertex
        else -> return null
    }
    return OrbitTarget(operation, kind)
}

fun String.toTransformOrNull(): Transform? {
    val spec = parseTransformTag() ?: return null
    Transforms.firstOrNull { it.id == spec.id }?.let { transform ->
        if (transform.accepts(spec.tweaks)) {
            return transform.copy(tweaks = spec.tweaks)
        }
        return null
    }
    val kind = spec.id.target ?: return null
    val transform = when (spec.id.operation) {
        TransformOperation.Drop -> Drop(kind)
        TransformOperation.Kis -> KisFace(kind as? FaceKind ?: return null)
        TransformOperation.Truncated -> TruncateVertex(kind as? VertexKind ?: return null)
        TransformOperation.Rectified -> RectifyVertex(kind as? VertexKind ?: return null)
        TransformOperation.Radial -> RadialVertex(kind as? VertexKind ?: return null)
        TransformOperation.StellateFace -> StellateFace(kind as? FaceKind ?: return null)
        else -> return null
    }
    if (!transform.accepts(spec.tweaks)) return null
    return transform.copy(tweaks = spec.tweaks)
}

private fun Transform.accepts(tweaks: Map<TransformTweak, Double>): Boolean =
    tweaks.all { (tweak, value) ->
        settings.singleOrNull { it.tweak == tweak }?.let { value in it.min..it.max } == true
    }
