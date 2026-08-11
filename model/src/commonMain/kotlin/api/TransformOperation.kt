package polyhedra.model.api

import polyhedra.model.poly.AnyKind
import polyhedra.model.poly.Chirality
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.VertexKind

/** A transform operation independent of its serialized tag, parameters, and optional orbit target. */
enum class TransformOperation(
    val tag: String,
    val isChiral: Boolean = false,
) {
    None("n"),
    Truncated("t"),
    Rectified("a"),
    Cantellated("e"),
    Dual("d"),
    Bevelled("b"),
    Snub("s", isChiral = true),
    Propeller("p", isChiral = true),
    Whirl("w", isChiral = true),
    Quinto("q"),
    Chamfered("c"),
    Canonical("o"),
    Greatened("G"),
    Stellated("S"),
    Kis("k"),
    Join("j"),
    Needle("N"),
    Zip("z"),
    Ortho("O"),
    Meta("m"),
    Gyro("g", isChiral = true),
    Drop("x"),
}

/** Type-safe identity of a transform before continuous tweak values are applied. */
data class TransformId(
    val operation: TransformOperation,
    val chirality: Chirality? = if (operation.isChiral) Chirality.Default else null,
    val target: AnyKind? = null,
) {
    init {
        require((chirality != null) == operation.isChiral)
        require(
            when (operation) {
                TransformOperation.Drop -> target != null
                TransformOperation.Kis -> target == null || target is FaceKind
                TransformOperation.Truncated,
                TransformOperation.Rectified -> target == null || target is VertexKind
                else -> target == null
            }
        )
    }

    fun flippedChirality(): TransformId = copy(chirality = requireNotNull(chirality).flipped())
}

/** Fully typed transform configuration used after a serialized tag has been parsed. */
data class TransformSpec(
    val id: TransformId,
    val tweaks: Map<TransformTweak, Double> = emptyMap(),
)
