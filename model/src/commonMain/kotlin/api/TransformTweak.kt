package polyhedra.model.api

import polyhedra.model.util.fmt

private const val TWEAK_SEPARATOR = "~"
private const val TWEAK_ASSIGNMENT = "="

/** Continuous, dimensionless controls stored on a transform. A value of 1 is the regular default. */
enum class TransformTweak(val tag: String) {
    Depth("d"),
    Distance("c"),
    Width("w"),
    Inset("i"),
    Twist("r"),
    Height("h"),
}

data class TransformTweakRange(val min: Double, val max: Double)

/** Outer exploration bounds. The core narrows these to the range valid for the actual input geometry. */
object TransformTweakRanges {
    val TruncationDepth = TransformTweakRange(0.1, 1.45)
    val RectificationDepth = TransformTweakRange(0.1, 1.0)
    val CantellationDistance = TransformTweakRange(0.1, 1.5)
    val ChamferWidth = TransformTweakRange(0.1, 1.0)
    val SnubInset = TransformTweakRange(0.1, 1.5)
    val SnubTwist = TransformTweakRange(0.0, 2.0)
    val KisHeight = TransformTweakRange(0.1, 1.5)
}

/** Continuous controls supported by an operation, in their UI display order. */
fun String.transformTweakRanges(): Map<TransformTweak, TransformTweakRange> {
    val operationTag = removeSuffix("'")
    if (operationTag.startsWith("k[")) {
        return linkedMapOf(TransformTweak.Height to TransformTweakRanges.KisHeight)
    }
    if (operationTag.startsWith("t[")) {
        return linkedMapOf(TransformTweak.Depth to TransformTweakRanges.TruncationDepth)
    }
    if (operationTag.startsWith("a[")) {
        return linkedMapOf(TransformTweak.Depth to TransformTweakRanges.RectificationDepth)
    }
    return when (operationTag) {
        "t", "N", "z" -> linkedMapOf(TransformTweak.Depth to TransformTweakRanges.TruncationDepth)
        "k" -> linkedMapOf(TransformTweak.Height to TransformTweakRanges.KisHeight)
        "e", "O" -> linkedMapOf(TransformTweak.Distance to TransformTweakRanges.CantellationDistance)
        "b", "m" -> linkedMapOf(
            TransformTweak.Distance to TransformTweakRanges.CantellationDistance,
            TransformTweak.Depth to TransformTweakRanges.TruncationDepth,
        )
        "s", "g" -> linkedMapOf(
            TransformTweak.Inset to TransformTweakRanges.SnubInset,
            TransformTweak.Twist to TransformTweakRanges.SnubTwist,
        )
        "c" -> linkedMapOf(TransformTweak.Width to TransformTweakRanges.ChamferWidth)
        else -> emptyMap()
    }
}

data class ParsedTransformTag(
    val operationTag: String,
    val tweaks: Map<TransformTweak, Double>,
)

fun encodeTransformTag(
    operationTag: String,
    tweaks: Map<TransformTweak, Double>,
): String = buildString {
    append(operationTag)
    for (tweak in TransformTweak.entries) {
        val value = tweaks[tweak]?.takeUnless { it == 1.0 } ?: continue
        append(TWEAK_SEPARATOR)
        append(tweak.tag)
        append(TWEAK_ASSIGNMENT)
        append(value.fmt)
    }
}

fun String.parseTransformTag(): ParsedTransformTag? {
    val parts = split(TWEAK_SEPARATOR)
    val operationTag = parts.firstOrNull()?.takeIf(String::isNotEmpty) ?: return null
    val tweaks = linkedMapOf<TransformTweak, Double>()
    for (part in parts.drop(1)) {
        val key = part.substringBefore(TWEAK_ASSIGNMENT, missingDelimiterValue = "")
        val valueText = part.substringAfter(TWEAK_ASSIGNMENT, missingDelimiterValue = "")
        val tweak = TransformTweak.entries.singleOrNull { it.tag == key } ?: return null
        val value = valueText.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        if (tweaks.put(tweak, value) != null) return null
    }
    return ParsedTransformTag(operationTag, tweaks.filterValues { it != 1.0 })
}

fun String.withoutTransformTweaks(): String =
    substringBefore(TWEAK_SEPARATOR)
