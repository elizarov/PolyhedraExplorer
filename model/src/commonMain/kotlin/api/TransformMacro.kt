package polyhedra.core.api

/** A named Conway-style abbreviation for a sequence of primitive transforms. */
data class TransformMacro(
    val tag: String,
    val name: String,
    val expansionTags: List<String>,
)

val TransformMacros: List<TransformMacro> = listOf(
    TransformMacro("k", "Kis", listOf("d", "t", "d")),
    TransformMacro("j", "Join", listOf("d", "a", "d")),
    TransformMacro("N", "Needle", listOf("t", "d")),
    TransformMacro("z", "Zip", listOf("d", "t")),
    TransformMacro("e", "Cantellated", listOf("a", "a")),
    TransformMacro("b", "Bevelled", listOf("a", "t")),
    TransformMacro("O", "Ortho", listOf("d", "a", "a", "d")),
    TransformMacro("m", "Meta", listOf("d", "a", "t", "d")),
    TransformMacro("g", "Gyro", listOf("d", "s", "d")),
)

private val transformMacrosByTag = TransformMacros.associateBy(TransformMacro::tag)
private val primitiveReplacementTags = listOf("t", "a", "d", "s", "c", "o")
private val replacementTagsByExpansion =
    (primitiveReplacementTags + TransformMacros.map(TransformMacro::tag)).associateBy { tag ->
        listOf(tag).normalizedExpandedTransformTags()
    }

fun String.toTransformMacroOrNull(): TransformMacro? =
    transformMacrosByTag[this]

fun String.expandedTransformTags(): List<String> =
    toTransformMacroOrNull()?.expansionTags ?: listOf(this)

data class TransformPrefixReplacement(
    val replacementTag: String,
    val startIndex: Int,
)

/**
 * Finds the longest applied-end suffix, displayed as the transform chain's left prefix, that is
 * algebraically equivalent to one primitive transform or macro. Macros are expanded and adjacent
 * Dual operations cancel before comparison, so displayed `Dual Needle` is reduced to `Truncated`.
 */
fun findTransformPrefixReplacement(tags: List<String>): TransformPrefixReplacement? {
    for (startIndex in tags.indices) {
        val prefix = tags.subList(startIndex, tags.size)
        val replacementTag = replacementTagsByExpansion[prefix.normalizedExpandedTransformTags()] ?: continue
        if (prefix.size == 1 && prefix.single() == replacementTag) continue
        return TransformPrefixReplacement(replacementTag, startIndex)
    }
    return null
}

private fun List<String>.normalizedExpandedTransformTags(): List<String> = buildList {
    for (tag in this@normalizedExpandedTransformTags) {
        for (primitiveTag in tag.expandedTransformTags()) {
            if (primitiveTag == "d" && lastOrNull() == "d") {
                removeAt(lastIndex)
            } else {
                add(primitiveTag)
            }
        }
    }
}
