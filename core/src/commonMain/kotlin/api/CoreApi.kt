package polyhedra.core.api

import polyhedra.common.poly.*
import polyhedra.common.transform.*
import polyhedra.common.util.OperationProgressContext

private const val MAX_DISPLAY_EDGES = (1 shl 15) - 1
private const val ANIMATION_GAP = 1e-4

suspend fun evaluateCore(
    request: CoreRequest,
    reportProgress: (Int) -> Unit = {},
): CoreResponse {
    reportProgress(1)
    val current = evaluateState(request.state, reportProgress)
    val duration = request.animationDuration?.takeIf { it > 0.0 }
    val previousState = request.previousState
    if (duration == null || previousState == null || previousState == request.state) {
        reportProgress(100)
        return current.response
    }

    val previous = evaluateState(previousState) {}
    val animation = computeAnimation(previous, current, duration)
    reportProgress(100)
    return current.response.copy(animation = animation)
}

private data class Evaluation(
    val state: CoreState,
    val seed: Seed,
    val scale: Scale,
    val validTransforms: List<Transform>,
    val rawPoly: Polyhedron,
    val response: CoreResponse,
)

private suspend fun evaluateState(
    state: CoreState,
    reportProgress: (Int) -> Unit,
): Evaluation {
    val seed = Seeds.firstOrNull { it.tag == state.seedTag }
        ?: error("Unknown seed tag: ${state.seedTag}")
    val scale = Scales.firstOrNull { it.tag == state.scaleTag }
        ?: error("Unknown scale tag: ${state.scaleTag}")
    val transforms = state.transformTags.map { tag ->
        tag.toTransformOrNull() ?: error("Unknown transform tag: $tag")
    }

    var poly = seed.poly
    var polyName = seed.toString()
    val transformedPolys = ArrayList<Polyhedron>()
    val validTransforms = ArrayList<Transform>()
    val availableDrops = ArrayList<List<String>>()
    val warnings = ArrayList<CoreIssue?>()
    var errorIndex: Int? = null
    var errorIssue: CoreIssue? = null

    availableDrops += poly.canDrop.map(Any::toString).sorted()
    for ((index, transform) in transforms.withIndex()) {
        if (!transform.isApplicable(poly)) {
            errorIndex = index
            errorIssue = CoreIssue(CoreIssueCode.TransformNotApplicable, transform.tag)
            break
        }
        val expectedFev = transform.fev?.let { it * poly.fev() }
        if (expectedFev != null && expectedFev.e > MAX_DISPLAY_EDGES) {
            errorIndex = index
            errorIssue = CoreIssue(CoreIssueCode.TooLarge, transform.tag, expectedFev)
            break
        }

        var warning: CoreIssue? = null
        try {
            poly = if (transform.isIdentityTransform(poly)) {
                warning = CoreIssue(CoreIssueCode.TransformIsIdentity, transform.tag)
                poly
            } else {
                transform.asyncTransform?.invoke(poly, OperationProgressContext(reportProgress))
                    ?: transform.transform(poly)
            }
        } catch (cause: Throwable) {
            cause.printStackTrace()
            errorIndex = index
            errorIssue = CoreIssue(CoreIssueCode.TransformFailed, transform.tag)
            break
        }

        if (poly.fs.any { !it.isPlanar } && index == transforms.lastIndex) {
            warning = CoreIssue(CoreIssueCode.SomeFacesNotPlanar, transform.tag)
        }
        polyName = "$transform $polyName"
        transformedPolys += poly
        validTransforms += transform
        warnings += warning
        availableDrops += poly.canDrop.map(Any::toString).sorted()
    }

    val response = CoreResponse(
        poly = poly.scaled(scale),
        polyName = polyName,
        transformedPolys = transformedPolys,
        validTransformTags = validTransforms.map(Transform::tag),
        availableDrops = availableDrops,
        warnings = warnings,
        errorIndex = errorIndex,
        error = errorIssue,
    )
    return Evaluation(state, seed, scale, validTransforms, poly, response)
}

private fun computeAnimation(
    previous: Evaluation,
    current: Evaluation,
    duration: Double,
): List<CoreAnimationStep> {
    if (current.seed != previous.seed || current.response.poly == previous.response.poly) return emptyList()

    val commonSize = current.validTransforms.indices
        .takeWhile { it < previous.validTransforms.size && current.validTransforms[it] == previous.validTransforms[it] }
        .count()
    if (current.validTransforms.size <= commonSize + 1 && previous.validTransforms.size <= commonSize + 1) {
        val basePoly = if (commonSize == 0) current.seed.poly else current.response.transformedPolys[commonSize - 1]
        val previousPoly = previous.response.transformedPolys.getOrNull(commonSize) ?: basePoly
        val currentPoly = current.response.transformedPolys.getOrNull(commonSize) ?: basePoly
        val previousTransform = previous.validTransforms.getOrNull(commonSize)
            ?.takeIf { !it.isIdentityTransform(basePoly) } ?: Transform.None
        val currentTransform = current.validTransforms.getOrNull(commonSize)
            ?.takeIf { !it.isIdentityTransform(basePoly) } ?: Transform.None
        return transformAnimation(
            basePoly,
            current.scale,
            previousPoly,
            currentPoly,
            previousTransform,
            currentTransform,
            duration,
        )
    }
    return if (current.response.poly.hasSameTopology(previous.response.poly)) {
        listOf(CoreAnimationStep(duration, previous.response.poly, 0.0, current.response.poly, 1.0))
    } else {
        emptyList()
    }
}

private fun transformAnimation(
    basePoly: Polyhedron,
    scale: Scale,
    previousPoly: Polyhedron,
    currentPoly: Polyhedron,
    previousTransform: Transform,
    currentTransform: Transform,
    duration: Double,
): List<CoreAnimationStep> {
    if (previousTransform == Transform.None && currentTransform == Transform.None) return emptyList()
    if (currentPoly.hasSameTopology(previousPoly)) {
        return listOf(
            CoreAnimationStep(duration, previousPoly.scaled(scale), 0.0, currentPoly.scaled(scale), 1.0)
        )
    }

    val previousTruncation = previousTransform.truncationRatio(basePoly)
    val currentTruncation = currentTransform.truncationRatio(basePoly)
    val previousCantellation = previousTransform.cantellationRatio(basePoly)
    val currentCantellation = currentTransform.cantellationRatio(basePoly)
    val previousBevelling = previousTransform.bevellingRatio(basePoly)
    val currentBevelling = currentTransform.bevellingRatio(basePoly)
    val previousSnubbing = previousTransform.snubbingRatio(basePoly)
    val currentSnubbing = currentTransform.snubbingRatio(basePoly)
    val previousChamfering = previousTransform.chamferingRatio(basePoly)
    val currentChamfering = currentTransform.chamferingRatio(basePoly)

    return when {
        previousTruncation != null && currentTruncation != null -> {
            val previousFraction = previousFractionGap(previousTruncation)
            val targetFraction = targetFractionGap(currentTruncation)
            listOf(
                CoreAnimationStep(
                    duration,
                    basePoly.truncated(
                        previousFraction.interpolate(previousTruncation, currentTruncation),
                        scale,
                        previousPoly.faceKindSources,
                    ),
                    previousFraction,
                    basePoly.truncated(
                        targetFraction.interpolate(previousTruncation, currentTruncation),
                        scale,
                        currentPoly.faceKindSources,
                    ),
                    targetFraction,
                )
            )
        }

        previousCantellation != null && currentCantellation != null -> {
            val previousFraction = previousFractionGap(previousCantellation)
            val targetFraction = targetFractionGap(currentCantellation)
            listOf(
                CoreAnimationStep(
                    duration,
                    basePoly.cantellated(
                        previousFraction.interpolate(previousCantellation, currentCantellation),
                        scale,
                        previousPoly.faceKindSources,
                    ),
                    previousFraction,
                    basePoly.cantellated(
                        targetFraction.interpolate(previousCantellation, currentCantellation),
                        scale,
                        currentPoly.faceKindSources,
                    ),
                    targetFraction,
                )
            )
        }

        previousBevelling != null && currentBevelling != null -> {
            val previousFraction = previousFractionGap(previousBevelling)
            val targetFraction = targetFractionGap(currentBevelling)
            listOf(
                CoreAnimationStep(
                    duration,
                    basePoly.bevelled(
                        previousFraction.interpolate(previousBevelling, currentBevelling),
                        scale,
                        previousPoly.faceKindSources,
                    ),
                    previousFraction,
                    basePoly.bevelled(
                        targetFraction.interpolate(previousBevelling, currentBevelling),
                        scale,
                        currentPoly.faceKindSources,
                    ),
                    targetFraction,
                )
            )
        }

        previousSnubbing != null && currentSnubbing != null -> {
            val previousFraction = previousFractionGap(previousSnubbing)
            val targetFraction = targetFractionGap(currentSnubbing)
            listOf(
                CoreAnimationStep(
                    duration,
                    basePoly.snub(
                        previousFraction.interpolate(previousSnubbing, currentSnubbing),
                        scale,
                        previousPoly.faceKindSources,
                    ),
                    previousFraction,
                    basePoly.snub(
                        targetFraction.interpolate(previousSnubbing, currentSnubbing),
                        scale,
                        currentPoly.faceKindSources,
                    ),
                    targetFraction,
                )
            )
        }

        previousChamfering != null && currentChamfering != null -> {
            val previousFraction = previousFractionGap(previousChamfering)
            val targetFraction = targetFractionGap(currentChamfering)
            listOf(
                CoreAnimationStep(
                    duration,
                    basePoly.chamfered(
                        previousFraction.interpolate(previousChamfering, currentChamfering),
                        scale,
                        previousPoly.faceKindSources,
                    ),
                    previousFraction,
                    basePoly.chamfered(
                        targetFraction.interpolate(previousChamfering, currentChamfering),
                        scale,
                        currentPoly.faceKindSources,
                    ),
                    targetFraction,
                )
            )
        }

        previousTransform != Transform.None && currentTransform != Transform.None ->
            transformAnimation(
                basePoly,
                scale,
                previousPoly,
                basePoly,
                previousTransform,
                Transform.None,
                duration / 2,
            ) + transformAnimation(
                basePoly,
                scale,
                basePoly,
                currentPoly,
                Transform.None,
                currentTransform,
                duration / 2,
            )

        else -> emptyList()
    }
}

private fun previousFractionGap(ratio: Double): Double =
    if (ratio <= 0.0 || ratio >= 1.0) ANIMATION_GAP else 0.0

private fun targetFractionGap(ratio: Double): Double =
    if (ratio <= 0.0 || ratio >= 1.0) 1.0 - ANIMATION_GAP else 1.0

private fun Double.interpolate(previous: Double, target: Double): Double =
    (1.0 - this) * previous + this * target

private fun previousFractionGap(ratio: BevellingRatio): Double =
    if (ratio.cr <= 0.0 || ratio.cr >= 1.0 || ratio.tr <= 0.0 || ratio.tr >= 1.0) ANIMATION_GAP else 0.0

private fun targetFractionGap(ratio: BevellingRatio): Double =
    if (ratio.cr <= 0.0 || ratio.cr >= 1.0 || ratio.tr <= 0.0 || ratio.tr >= 1.0) 1.0 - ANIMATION_GAP else 1.0

private fun Double.interpolate(previous: BevellingRatio, target: BevellingRatio): BevellingRatio =
    BevellingRatio(
        (1.0 - this) * previous.cr + this * target.cr,
        (1.0 - this) * previous.tr + this * target.tr,
    )

private fun previousFractionGap(ratio: SnubbingRatio): Double =
    if (ratio.cr <= 0.0 || ratio.cr >= 1.0) ANIMATION_GAP else 0.0

private fun targetFractionGap(ratio: SnubbingRatio): Double =
    if (ratio.cr <= 0.0 || ratio.cr >= 1.0) 1.0 - ANIMATION_GAP else 1.0

private fun Double.interpolate(previous: SnubbingRatio, target: SnubbingRatio): SnubbingRatio =
    SnubbingRatio(
        (1.0 - this) * previous.cr + this * target.cr,
        (1.0 - this) * previous.sa + this * target.sa,
    )
