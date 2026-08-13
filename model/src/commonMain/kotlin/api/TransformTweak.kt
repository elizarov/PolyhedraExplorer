package polyhedra.model.api

import polyhedra.model.poly.CHIRALITY_PRIME
import polyhedra.model.poly.Chirality
import polyhedra.model.poly.toAnyKindOrNull
import polyhedra.model.util.fmt

private const val TWEAK_SEPARATOR = "~"
private const val TWEAK_ASSIGNMENT = "="

/** Typed controls stored on a transform. A value of 1 is the regular/default choice. */
enum class TransformTweak(val tag: String) {
    Depth("d"),
    Distance("c"),
    Width("w"),
    Inset("i"),
    Twist("r"),
    Height("h"),
    Radius("R"),
    StellationResult("l"),
}

data class TransformTweakRange(val min: Double, val max: Double)

/** Outer exploration bounds. The core narrows these to the choices valid for the actual input geometry. */
object TransformTweakRanges {
    val TruncationDepth = TransformTweakRange(0.1, 1.45)
    val RectificationDepth = TransformTweakRange(0.1, 1.0)
    val CantellationDistance = TransformTweakRange(0.1, 1.5)
    val ChamferWidth = TransformTweakRange(0.1, 1.0)
    val SnubInset = TransformTweakRange(0.1, 1.5)
    val SnubTwist = TransformTweakRange(0.0, 2.0)
    val KisHeight = TransformTweakRange(0.1, 1.5)
    val RadialRadius = TransformTweakRange(0.05, 20.0)
    val StellationResult = TransformTweakRange(1.0, 32767.0)
}

/** Controls supported by a transform, in their UI display order. */
fun TransformId.transformTweakRanges(): Map<TransformTweak, TransformTweakRange> {
    if (operation == TransformOperation.Kis && target != null) {
        return linkedMapOf(TransformTweak.Height to TransformTweakRanges.KisHeight)
    }
    if (operation == TransformOperation.Truncated && target != null) {
        return linkedMapOf(TransformTweak.Depth to TransformTweakRanges.TruncationDepth)
    }
    if (operation == TransformOperation.Rectified && target != null) {
        return linkedMapOf(TransformTweak.Depth to TransformTweakRanges.RectificationDepth)
    }
    if ((operation == TransformOperation.Radial || operation == TransformOperation.StellateFace) && target != null) {
        return linkedMapOf(TransformTweak.Radius to TransformTweakRanges.RadialRadius)
    }
    return when (operation) {
        TransformOperation.Truncated,
        TransformOperation.Needle,
        TransformOperation.Zip -> linkedMapOf(TransformTweak.Depth to TransformTweakRanges.TruncationDepth)
        TransformOperation.Kis -> linkedMapOf(TransformTweak.Height to TransformTweakRanges.KisHeight)
        TransformOperation.Cantellated,
        TransformOperation.Ortho -> linkedMapOf(
            TransformTweak.Distance to TransformTweakRanges.CantellationDistance
        )
        TransformOperation.Bevelled,
        TransformOperation.Meta -> linkedMapOf(
            TransformTweak.Distance to TransformTweakRanges.CantellationDistance,
            TransformTweak.Depth to TransformTweakRanges.TruncationDepth,
        )
        TransformOperation.Snub,
        TransformOperation.Gyro -> linkedMapOf(
            TransformTweak.Inset to TransformTweakRanges.SnubInset,
            TransformTweak.Twist to TransformTweakRanges.SnubTwist,
        )
        TransformOperation.Chamfered -> linkedMapOf(TransformTweak.Width to TransformTweakRanges.ChamferWidth)
        TransformOperation.Greatened,
        TransformOperation.Stellated -> linkedMapOf(
            TransformTweak.StellationResult to TransformTweakRanges.StellationResult,
        )
        else -> emptyMap()
    }
}

fun encodeTransformTag(
    id: TransformId,
    tweaks: Map<TransformTweak, Double>,
): String = buildString {
    append(id.operation.tag)
    if (id.chirality == Chirality.Flipped) append(CHIRALITY_PRIME)
    id.target?.let { target ->
        append('[')
        append(target)
        append(']')
    }
    for (tweak in TransformTweak.entries) {
        val value = tweaks[tweak]?.takeUnless { it == 1.0 } ?: continue
        append(TWEAK_SEPARATOR)
        append(tweak.tag)
        append(TWEAK_ASSIGNMENT)
        append(value.fmt(12))
    }
}

val TransformSpec.tag: String
    get() = encodeTransformTag(id, tweaks)

fun String.parseTransformTag(): TransformSpec? {
    val parts = split(TWEAK_SEPARATOR)
    val serializedId = parts.firstOrNull()?.takeIf(String::isNotEmpty) ?: return null
    val bracket = serializedId.indexOf('[')
    val serializedOperation = if (bracket < 0) {
        serializedId
    } else {
        if (!serializedId.endsWith(']') || bracket == 0) return null
        serializedId.substring(0, bracket)
    }
    val target = if (bracket < 0) null else {
        serializedId.substring(bracket + 1, serializedId.lastIndex).toAnyKindOrNull() ?: return null
    }
    val flipped = serializedOperation.endsWith(CHIRALITY_PRIME)
    val operationTag = serializedOperation.removeSuffix(CHIRALITY_PRIME)
    val operation = TransformOperation.entries.singleOrNull { it.tag == operationTag } ?: return null
    if (flipped && !operation.isChiral) return null
    val chirality = if (operation.isChiral) {
        if (flipped) Chirality.Flipped else Chirality.Default
    } else {
        null
    }
    val id = runCatching { TransformId(operation, chirality, target) }.getOrNull() ?: return null
    val tweaks = linkedMapOf<TransformTweak, Double>()
    for (part in parts.drop(1)) {
        val key = part.substringBefore(TWEAK_ASSIGNMENT, missingDelimiterValue = "")
        val valueText = part.substringAfter(TWEAK_ASSIGNMENT, missingDelimiterValue = "")
        val tweak = TransformTweak.entries.singleOrNull { it.tag == key } ?: return null
        val value = valueText.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        if (tweaks.put(tweak, value) != null) return null
    }
    return TransformSpec(id, tweaks.filterValues { it != 1.0 })
}
