package polyhedra.model.api

import polyhedra.model.api.TransformOperation.*
import polyhedra.model.poly.Chirality

/** A named Conway-style abbreviation for a sequence of primitive transforms. */
data class TransformMacro(
    val id: TransformId,
    val name: String,
    val expansion: List<TransformId>,
) {
    val chirality: Chirality? get() = id.chirality
    val displayName: String get() = name + chirality?.suffix.orEmpty()

    /** Serialized forms exposed at the core API boundary. */
    val tag: String get() = TransformSpec(id).tag
    val expansionTags: List<String> get() = expansion.map { TransformSpec(it).tag }
}

private fun id(operation: TransformOperation, chirality: Chirality? = null): TransformId =
    TransformId(operation, chirality ?: if (operation.isChiral) Chirality.Default else null)

val TransformMacros: List<TransformMacro> = listOf(
    TransformMacro(id(Kis), "Kis", listOf(id(Dual), id(Truncated), id(Dual))),
    TransformMacro(id(Join), "Join", listOf(id(Dual), id(Rectified), id(Dual))),
    TransformMacro(id(Needle), "Needle", listOf(id(Truncated), id(Dual))),
    TransformMacro(id(Zip), "Zip", listOf(id(Dual), id(Truncated))),
    TransformMacro(id(Cantellated), "Cantellated", listOf(id(Rectified), id(Rectified))),
    TransformMacro(id(Bevelled), "Bevelled", listOf(id(Rectified), id(Truncated))),
    TransformMacro(id(Ortho), "Ortho", listOf(id(Dual), id(Rectified), id(Rectified), id(Dual))),
    TransformMacro(id(Meta), "Meta", listOf(id(Dual), id(Rectified), id(Truncated), id(Dual))),
    TransformMacro(id(Gyro), "Gyro", listOf(id(Dual), id(Snub), id(Dual))),
)

private val allTransformMacros = TransformMacros.flatMap { macro ->
    if (macro.id.operation.isChiral) listOf(macro, macro.flipped()) else listOf(macro)
}
private val transformMacrosById = allTransformMacros.associateBy(TransformMacro::id)
private val primitiveReplacementIds = listOf(
    id(Truncated),
    id(Rectified),
    id(Dual),
    id(Snub),
    id(Snub, Chirality.Flipped),
    id(Propeller),
    id(Propeller, Chirality.Flipped),
    id(Whirl),
    id(Whirl, Chirality.Flipped),
    id(Quinto),
    id(Chamfered),
    id(Canonical),
)
private val replacementIdsByExpansion =
    (primitiveReplacementIds + allTransformMacros.map(TransformMacro::id)).associateBy { transformId ->
        listOf(transformId).normalizedExpandedTransforms()
    }

private fun TransformMacro.flipped(): TransformMacro {
    val flippedId = id.flippedChirality()
    return copy(
        id = flippedId,
        expansion = expansion.map { transformId ->
            if (transformId.operation == Snub) transformId.flippedChirality() else transformId
        },
    )
}

fun TransformId.toTransformMacroOrNull(): TransformMacro? = transformMacrosById[this]

data class TransformPrefixReplacement(
    val replacement: TransformId,
    val startIndex: Int,
) {
    val replacementTag: String get() = TransformSpec(replacement).tag
}

/**
 * Finds the longest applied-end suffix, displayed as the transform chain's left prefix, that is
 * algebraically equivalent to one primitive transform or macro. Macros are expanded and adjacent
 * Dual operations cancel before comparison, so displayed `Dual Needle` is reduced to `Truncated`.
 */
fun findTransformPrefixReplacement(transforms: List<TransformSpec>): TransformPrefixReplacement? {
    for (startIndex in transforms.indices) {
        val prefix = transforms.subList(startIndex, transforms.size)
        val replacement = replacementIdsByExpansion[
            prefix.map(TransformSpec::id).normalizedExpandedTransforms()
        ] ?: continue
        if (prefix.size == 1 && prefix.single().id == replacement) continue
        return TransformPrefixReplacement(replacement, startIndex)
    }
    return null
}

private fun List<TransformId>.normalizedExpandedTransforms(): List<TransformId> = buildList {
    for (transform in this@normalizedExpandedTransforms) {
        val expansion = transform.toTransformMacroOrNull()?.expansion ?: listOf(transform)
        for (primitive in expansion) {
            if (primitive.operation == Dual && lastOrNull()?.operation == Dual) {
                removeAt(lastIndex)
            } else {
                add(primitive)
            }
        }
    }
}
