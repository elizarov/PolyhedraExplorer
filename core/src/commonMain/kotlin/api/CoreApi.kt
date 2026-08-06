package polyhedra.core.api

import polyhedra.core.poly.*
import polyhedra.core.util.OperationProgressContext
import polyhedra.model.api.*
import polyhedra.model.poly.*
import polyhedra.core.transform.*

private const val MAX_DISPLAY_EDGES = (1 shl 15) - 1
private const val ANIMATION_GAP = 1e-4

private data class LogicalTransform(
    val tag: String,
    val name: String,
    val primitiveTransforms: List<Transform>,
    val animationTransform: Transform? = primitiveTransforms.singleOrNull(),
) {
    fun isIdentityTransform(poly: Polyhedron): Boolean =
        primitiveTransforms.size == 1 && primitiveTransforms.single().isIdentityTransform(poly)

    override fun toString(): String = name
}

private fun String.toLogicalTransformOrNull(): LogicalTransform? {
    toTransformMacroOrNull()?.let { macro ->
        val primitives = macro.expansionTags.map { tag ->
            requireNotNull(tag.toTransformOrNull()) { "Unknown primitive transform tag in ${macro.name}: $tag" }
        }
        val animationTransform = when (macro.tag) {
            "e" -> Transform.Cantellated
            "b" -> Transform.Bevelled
            else -> null
        }
        return LogicalTransform(macro.tag, macro.displayName, primitives, animationTransform)
    }
    return toTransformOrNull()?.let { transform ->
        LogicalTransform(transform.tag, transform.toString(), listOf(transform))
    }
}

private data class PendingRectification(val basePoly: Polyhedron)

private sealed interface TransformApplication {
    data class Success(
        val poly: Polyhedron,
        val pendingRectification: PendingRectification?,
        val isIdentity: Boolean,
    ) : TransformApplication

    data class Failure(val issue: CoreIssue) : TransformApplication
}

suspend fun evaluateCore(
    request: CoreRequest,
    reportProgress: (Int) -> Unit = {},
): CoreResponse {
    reportProgress(1)
    val current = evaluateState(request.state, reportProgress, detectSeed = request.detectSeed)
    val duration = request.animationDuration?.takeIf { it > 0.0 }
    val previousState = request.previousState
    if (duration == null || previousState == null || previousState == request.state) {
        reportProgress(100)
        return current.response
    }

    val previous = evaluateState(previousState, {}, detectSeed = false)
    val animation = computeAnimation(previous, current, duration)
    reportProgress(100)
    return current.response.copy(animation = animation)
}

private data class Evaluation(
    val state: CoreState,
    val seed: Seed,
    val scale: Scale,
    val validTransforms: List<LogicalTransform>,
    val rawPoly: Polyhedron,
    val response: CoreResponse,
)

private suspend fun evaluateState(
    state: CoreState,
    reportProgress: (Int) -> Unit,
    detectSeed: Boolean,
): Evaluation {
    val seed = state.seedTag.toSeedOrNull()
        ?: error("Unknown seed tag: ${state.seedTag}")
    val scale = Scales.firstOrNull { it.tag == state.scaleTag }
        ?: error("Unknown scale tag: ${state.scaleTag}")
    val transforms = state.transformTags.map { tag ->
        tag.toLogicalTransformOrNull() ?: error("Unknown transform tag: $tag")
    }

    var poly = seed.poly
    var pendingRectification: PendingRectification? = null
    var polyName = seed.toString()
    val transformedPolys = ArrayList<Polyhedron>()
    val validTransforms = ArrayList<LogicalTransform>()
    val availableOrbitTransforms = ArrayList<List<String>>()
    val warnings = ArrayList<CoreIssue?>()
    var errorIndex: Int? = null
    var errorIssue: CoreIssue? = null

    availableOrbitTransforms += poly.availableOrbitTransforms.map(Transform::tag).sorted()
    for ((index, transform) in transforms.withIndex()) {
        var warning: CoreIssue? = null
        val reportTransformProgress: (Int) -> Unit = { done ->
            val span = 98
            reportProgress(1 + (index * span + done.coerceIn(0, 100) * span / 100) / transforms.size)
        }
        when (val application = applyTransform(transform, poly, pendingRectification, reportTransformProgress)) {
            is TransformApplication.Failure -> {
                errorIndex = index
                errorIssue = application.issue
                break
            }

            is TransformApplication.Success -> {
                poly = application.poly
                pendingRectification = application.pendingRectification
                if (application.isIdentity) {
                    warning = CoreIssue(CoreIssueCode.TransformIsIdentity, transform.tag)
                }
            }
        }

        if (poly.fs.any { !it.isPlanar } && index == transforms.lastIndex) {
            warning = CoreIssue(CoreIssueCode.SomeFacesNotPlanar, transform.tag)
        }
        polyName = "$transform $polyName"
        transformedPolys += poly
        validTransforms += transform
        warnings += warning
        availableOrbitTransforms += poly.availableOrbitTransforms.map(Transform::tag).sorted()
    }

    val response = CoreResponse(
        poly = poly.scaled(scale),
        polyName = polyName,
        recognizedSeedTag = if (
            detectSeed && errorIndex == null &&
            (validTransforms.isNotEmpty() || seed.type == SeedType.Families)
        ) {
            poly.recognizedSeedOrNull()?.tag
        } else {
            null
        },
        transformedPolys = transformedPolys,
        validTransformTags = validTransforms.map(LogicalTransform::tag),
        availableOrbitTransforms = availableOrbitTransforms,
        warnings = warnings,
        errorIndex = errorIndex,
        error = errorIssue,
    )
    return Evaluation(state, seed, scale, validTransforms, poly, response)
}

private suspend fun applyTransform(
    transform: LogicalTransform,
    inputPoly: Polyhedron,
    inputPendingRectification: PendingRectification?,
    reportProgress: (Int) -> Unit,
): TransformApplication {
    var poly = inputPoly
    var pendingRectification = inputPendingRectification
    var isIdentity = false

    for (primitive in transform.primitiveTransforms) {
        if (!primitive.isApplicable(poly)) {
            return TransformApplication.Failure(
                CoreIssue(CoreIssueCode.TransformNotApplicable, transform.tag)
            )
        }
        val expectedFev = primitive.fev?.let { it * poly.fev() }
        if (expectedFev != null && expectedFev.e > MAX_DISPLAY_EDGES) {
            return TransformApplication.Failure(
                CoreIssue(CoreIssueCode.TooLarge, transform.tag, expectedFev)
            )
        }

        val primitiveInput = poly
        val pendingInput = pendingRectification
        try {
            poly = when {
                pendingInput != null && primitive == Transform.Rectified ->
                    pendingInput.basePoly.cantellated()

                pendingInput != null && primitive == Transform.Truncated ->
                    pendingInput.basePoly.bevelled()

                primitive.isIdentityTransform(poly) -> {
                    isIdentity = true
                    poly
                }

                else -> primitive.asyncTransform?.invoke(poly, OperationProgressContext(reportProgress))
                    ?: primitive.transform(poly)
            }
        } catch (cause: Throwable) {
            cause.printStackTrace()
            return TransformApplication.Failure(
                CoreIssue(CoreIssueCode.TransformFailed, transform.tag)
            )
        }

        pendingRectification = when {
            pendingInput != null &&
                (primitive == Transform.Rectified || primitive == Transform.Truncated) -> null
            primitive == Transform.Rectified -> PendingRectification(primitiveInput)
            else -> null
        }
    }

    return TransformApplication.Success(poly, pendingRectification, isIdentity)
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
        compositionFusionAnimation(previous, current, commonSize, duration)?.let { return it }
        val basePoly = if (commonSize == 0) current.seed.poly else current.response.transformedPolys[commonSize - 1]
        val previousPoly = previous.response.transformedPolys.getOrNull(commonSize) ?: basePoly
        val currentPoly = current.response.transformedPolys.getOrNull(commonSize) ?: basePoly
        val previousTransform = previous.validTransforms.getOrNull(commonSize)
            ?.animationTransform
            ?.takeIf { !it.isIdentityTransform(basePoly) }
            ?: Transform.None
        val currentTransform = current.validTransforms.getOrNull(commonSize)
            ?.animationTransform
            ?.takeIf { !it.isIdentityTransform(basePoly) }
            ?: Transform.None
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

private fun compositionFusionAnimation(
    previous: Evaluation,
    current: Evaluation,
    commonSize: Int,
    duration: Double,
): List<CoreAnimationStep>? {
    if (commonSize == 0) return null
    val sharedTransform = current.validTransforms[commonSize - 1]
    if (sharedTransform.primitiveTransforms != listOf(Transform.Rectified)) return null

    fun LogicalTransform?.fusedTransform(): Transform? = when (this?.primitiveTransforms) {
        null -> Transform.Rectified
        listOf(Transform.Rectified) -> Transform.Cantellated
        listOf(Transform.Truncated) -> Transform.Bevelled
        else -> null
    }

    val previousTransform = previous.validTransforms.getOrNull(commonSize).fusedTransform() ?: return null
    val currentTransform = current.validTransforms.getOrNull(commonSize).fusedTransform() ?: return null
    val basePoly = if (commonSize == 1) current.seed.poly else current.response.transformedPolys[commonSize - 2]
    val previousPoly = previous.response.transformedPolys.getOrNull(commonSize)
        ?: previous.response.transformedPolys[commonSize - 1]
    val currentPoly = current.response.transformedPolys.getOrNull(commonSize)
        ?: current.response.transformedPolys[commonSize - 1]
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
    if (previousTransform is Snub && currentTransform is Snub &&
        previousTransform.chirality != currentTransform.chirality
    ) return emptyList()
    if (previousTransform is Propeller && currentTransform is Propeller &&
        previousTransform.chirality != currentTransform.chirality
    ) return emptyList()
    if (previousTransform is Whirl && currentTransform is Whirl &&
        previousTransform.chirality != currentTransform.chirality
    ) return emptyList()
    if (previousTransform is KisFace || currentTransform is KisFace) return emptyList()
    val changesOrbitTarget = previousTransform is OrbitTargetedAnimation &&
        currentTransform is OrbitTargetedAnimation &&
        previousTransform.targetKind != currentTransform.targetKind

    orbitTargetedAnimation(
        basePoly,
        scale,
        previousPoly,
        currentPoly,
        previousTransform,
        currentTransform,
        duration,
    )?.let { return it }

    if (!changesOrbitTarget && currentPoly.hasSameTopology(previousPoly)) {
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

private fun orbitTargetedAnimation(
    basePoly: Polyhedron,
    scale: Scale,
    previousPoly: Polyhedron,
    currentPoly: Polyhedron,
    previousTransform: Transform,
    currentTransform: Transform,
    duration: Double,
): List<CoreAnimationStep>? {
    val animation: OrbitTargetedAnimation
    val previousRatio: Double
    val currentRatio: Double
    when {
        previousTransform is OrbitTargetedAnimation &&
            currentTransform is OrbitTargetedAnimation &&
            previousTransform.targetKind == currentTransform.targetKind -> {
            animation = currentTransform
            previousRatio = previousTransform.targetRatio(basePoly)
            currentRatio = currentTransform.targetRatio(basePoly)
        }

        previousTransform is OrbitTargetedAnimation && currentTransform == Transform.None -> {
            animation = previousTransform
            previousRatio = animation.targetRatio(basePoly)
            currentRatio = 0.0
        }

        previousTransform == Transform.None && currentTransform is OrbitTargetedAnimation -> {
            animation = currentTransform
            previousRatio = 0.0
            currentRatio = animation.targetRatio(basePoly)
        }

        else -> return null
    }

    val previousFraction = previousFractionGap(previousRatio)
    val targetFraction = targetFractionGap(currentRatio)
    return listOf(
        CoreAnimationStep(
            duration,
            animation.polyAtRatio(
                basePoly,
                previousFraction.interpolate(previousRatio, currentRatio),
                scale,
                previousPoly.faceKindSources,
            ),
            previousFraction,
            animation.polyAtRatio(
                basePoly,
                targetFraction.interpolate(previousRatio, currentRatio),
                scale,
                currentPoly.faceKindSources,
            ),
            targetFraction,
        )
    )
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
