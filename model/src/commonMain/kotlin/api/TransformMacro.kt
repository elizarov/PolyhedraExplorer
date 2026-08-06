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
private val transformMacrosByDescendingExpansionSize =
    TransformMacros.sortedByDescending { it.expansionTags.size }

fun String.toTransformMacroOrNull(): TransformMacro? =
    transformMacrosByTag[this]

fun String.expandedTransformTags(): List<String> =
    toTransformMacroOrNull()?.expansionTags ?: listOf(this)

data class TransformMacroSuffix(
    val macro: TransformMacro,
    val startIndex: Int,
)

/**
 * Finds the longest known macro expansion at the applied end of a logical transform chain.
 * Existing macros in the suffix are expanded before comparison, which lets `Dual Cantellated
 * Dual` be folded to `Ortho` as well as the fully primitive spelling.
 */
fun findTransformMacroSuffix(tags: List<String>): TransformMacroSuffix? {
    if (tags.isEmpty()) return null
    for (macro in transformMacrosByDescendingExpansionSize) {
        for (startIndex in tags.indices) {
            if (startIndex == tags.lastIndex && tags[startIndex] == macro.tag) continue
            val expandedSuffix = tags.subList(startIndex, tags.size).flatMap(String::expandedTransformTags)
            if (expandedSuffix == macro.expansionTags) {
                return TransformMacroSuffix(macro, startIndex)
            }
        }
    }
    return null
}
