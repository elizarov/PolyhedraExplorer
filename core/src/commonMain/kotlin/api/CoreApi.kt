package polyhedra.core.api

import polyhedra.core.poly.*
import polyhedra.core.transform.*
import polyhedra.core.util.OperationProgressContext
import polyhedra.model.api.*
import polyhedra.model.poly.*
import kotlin.math.abs

private const val MAX_DISPLAY_EDGES = (1 shl 15) - 1
private const val ANIMATION_GAP = 1e-4
// Eight bisections resolve even the widest envelope more finely than the UI's 0.01 step.
private const val SAFE_RANGE_SEARCH_STEPS = 8
private const val SAFE_RANGE_ANCHOR_SAMPLES = 20

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
    val parsed = parseTransformTag() ?: return null
    parsed.operationTag.toTransformMacroOrNull()?.let { macro ->
        fun remap(vararg mappings: Pair<TransformTweak, TransformTweak>): Map<TransformTweak, Double>? {
            val sources = mappings.mapTo(hashSetOf()) { it.first }
            if (!parsed.tweaks.keys.all { it in sources }) return null
            return mappings.mapNotNull { (source, target) ->
                parsed.tweaks[source]?.let { target to it }
            }.toMap()
        }

        val primitives = when (macro.tag.removeSuffix("'")) {
            "k" -> listOf(
                KisAll(
                    (remap(TransformTweak.Height to TransformTweak.Height) ?: return null)[TransformTweak.Height]
                        ?: 1.0
                )
            )
            "j" -> {
                if (parsed.tweaks.isNotEmpty()) return null
                listOf(Transform.Dual, Transform.Rectified, Transform.Dual)
            }
            "N" -> listOf(
                Transform.Truncated.withTweaks(remap(TransformTweak.Depth to TransformTweak.Depth) ?: return null),
                Transform.Dual,
            )
            "z" -> listOf(
                Transform.Dual,
                Transform.Truncated.withTweaks(remap(TransformTweak.Depth to TransformTweak.Depth) ?: return null),
            )
            "e" -> listOf(
                Transform.Cantellated.withTweaks(
                    remap(TransformTweak.Distance to TransformTweak.Distance) ?: return null
                )
            )
            "b" -> listOf(
                Transform.Bevelled.withTweaks(
                    remap(
                        TransformTweak.Distance to TransformTweak.Distance,
                        TransformTweak.Depth to TransformTweak.Depth,
                    ) ?: return null
                )
            )
            "O" -> listOf(
                Transform.Dual,
                Transform.Cantellated.withTweaks(
                    remap(TransformTweak.Distance to TransformTweak.Distance) ?: return null
                ),
                Transform.Dual,
            )
            "m" -> listOf(
                Transform.Dual,
                Transform.Bevelled.withTweaks(
                    remap(
                        TransformTweak.Distance to TransformTweak.Distance,
                        TransformTweak.Depth to TransformTweak.Depth,
                    ) ?: return null
                ),
                Transform.Dual,
            )
            "g" -> listOf(
                Transform.Dual,
                (if (macro.chirality == Chirality.Flipped) Transform.SnubFlipped else Transform.Snub).withTweaks(
                    remap(
                        TransformTweak.Inset to TransformTweak.Inset,
                        TransformTweak.Twist to TransformTweak.Twist,
                    ) ?: return null
                ),
                Transform.Dual,
            )
            else -> return null
        }
        val animationTransform = primitives.singleOrNull()
        return LogicalTransform(
            encodeTransformTag(macro.tag, parsed.tweaks),
            macro.displayName,
            primitives,
            animationTransform,
        )
    }
    return encodeTransformTag(parsed.operationTag, parsed.tweaks).toTransformOrNull()?.let { transform ->
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
    reportProgress: (CoreProgress) -> Unit = {},
): CoreResponse {
    reportProgress(CoreProgress(transformIndex = 0, done = 0))
    val current = evaluateState(
        request.state,
        reportProgress,
        detectSeed = request.detectSeed,
        computeTweakRanges = request.calculateTweakRanges,
    )
    val duration = request.animationDuration?.takeIf { it > 0.0 }
    val previousState = request.previousState
    if (duration == null || previousState == null || previousState == request.state) {
        reportCompletion(current, reportProgress)
        return current.response
    }

    // A seed change cannot be animated, so evaluating the old transform chain would only repeat
    // potentially expensive operations such as canonicalization before returning no animation.
    if (previousState.seedTag != request.state.seedTag) {
        reportCompletion(current, reportProgress)
        return current.response
    }

    val previous = evaluateState(previousState, {}, detectSeed = false, computeTweakRanges = false)
    val animation = computeAnimation(previous, current, duration)
    reportCompletion(current, reportProgress)
    return current.response.copy(animation = animation)
}

private fun reportCompletion(evaluation: Evaluation, reportProgress: (CoreProgress) -> Unit) {
    if (evaluation.response.errorIndex != null) return
    reportProgress(CoreProgress(evaluation.validTransforms.lastIndex.coerceAtLeast(0), 100))
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
    reportProgress: (CoreProgress) -> Unit,
    detectSeed: Boolean,
    computeTweakRanges: Boolean = true,
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
    val transformTweakRanges = ArrayList<List<CoreTransformTweakRange>>()
    var errorIndex: Int? = null
    var errorIssue: CoreIssue? = null

    availableOrbitTransforms += poly.availableOrbitTransforms.map(Transform::tag).sorted()
    for ((index, transform) in transforms.withIndex()) {
        var warning: CoreIssue? = null
        val reportTransformProgress: (Int) -> Unit = { done ->
            reportProgress(CoreProgress(index, done.coerceIn(0, 100)))
        }
        reportTransformProgress(0)
        if (computeTweakRanges) {
            transformTweakRanges += transform.safeTweakRanges(poly, pendingRectification, scale)
        }
        when (
            val application = applyTransform(
                transform,
                poly,
                pendingRectification,
                reportTransformProgress,
                outputScale = scale,
            )
        ) {
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
        transformTweakRanges = transformTweakRanges,
        errorIndex = errorIndex,
        error = errorIssue,
    )
    return Evaluation(state, seed, scale, validTransforms, poly, response)
}

private suspend fun LogicalTransform.safeTweakRanges(
    inputPoly: Polyhedron,
    inputPendingRectification: PendingRectification?,
    outputScale: Scale,
): List<CoreTransformTweakRange> {
    val parsed = tag.parseTransformTag() ?: return emptyList()
    val envelopes = parsed.operationTag.transformTweakRanges()
    if (envelopes.isEmpty()) return emptyList()

    val result = ArrayList<CoreTransformTweakRange>(envelopes.size)
    for ((tweak, envelope) in envelopes) {
        safeTweakRange(parsed, tweak, envelope, inputPoly, inputPendingRectification, outputScale)
            ?.let(result::add)
    }
    return result
}

private suspend fun safeTweakRange(
    parsed: ParsedTransformTag,
    tweak: TransformTweak,
    envelope: TransformTweakRange,
    inputPoly: Polyhedron,
    inputPendingRectification: PendingRectification?,
    outputScale: Scale,
): CoreTransformTweakRange? {
    suspend fun isValid(value: Double): Boolean {
        val tweaks = parsed.tweaks.toMutableMap().apply {
            if (value == 1.0) remove(tweak) else put(tweak, value)
        }
        val candidate = encodeTransformTag(parsed.operationTag, tweaks).toLogicalTransformOrNull() ?: return false
        return applyTransform(
            candidate,
            inputPoly,
            inputPendingRectification,
            reportProgress = {},
            validateResultGeometry = true,
            outputScale = outputScale,
        ) is TransformApplication.Success
    }

    val current = (parsed.tweaks[tweak] ?: 1.0).coerceIn(envelope.min, envelope.max)
    val anchors = buildList {
        add(current)
        if (current != 1.0 && 1.0 in envelope.min..envelope.max) add(1.0)
        addAll(
            (0..SAFE_RANGE_ANCHOR_SAMPLES)
                .map { index ->
                    envelope.min + (envelope.max - envelope.min) * index / SAFE_RANGE_ANCHOR_SAMPLES
                }
                .sortedBy { value -> abs(value - current) }
        )
    }.distinct()
    val anchor = anchors.firstOrNull { isValid(it) } ?: return null

    suspend fun findBoundary(extreme: Double): Double {
        if (isValid(extreme)) return extreme
        var valid = anchor
        var invalid = extreme
        repeat(SAFE_RANGE_SEARCH_STEPS) {
            val candidate = (valid + invalid) / 2.0
            if (isValid(candidate)) valid = candidate else invalid = candidate
        }
        return valid
    }

    return CoreTransformTweakRange(
        tweak = tweak,
        min = findBoundary(envelope.min),
        max = findBoundary(envelope.max),
    )
}

private suspend fun applyTransform(
    transform: LogicalTransform,
    inputPoly: Polyhedron,
    inputPendingRectification: PendingRectification?,
    reportProgress: (Int) -> Unit,
    validateResultGeometry: Boolean = transform.tag.parseTransformTag()?.let { parsed ->
        parsed.tweaks.isNotEmpty() || parsed.operationTag.transformTweakRanges().isNotEmpty()
    } == true,
    outputScale: Scale? = null,
): TransformApplication {
    var poly = inputPoly
    var pendingRectification = inputPendingRectification
    var isIdentity = false

    val primitiveCount = transform.primitiveTransforms.size
    for ((primitiveIndex, primitive) in transform.primitiveTransforms.withIndex()) {
        val reportPrimitiveProgress: (Int) -> Unit = { done ->
            reportProgress((primitiveIndex * 100 + done.coerceIn(0, 100)) / primitiveCount)
        }
        reportPrimitiveProgress(0)
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

                else -> primitive.asyncTransform?.invoke(poly, OperationProgressContext(reportPrimitiveProgress))
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
        reportPrimitiveProgress(100)
    }

    if (validateResultGeometry) {
        try {
            poly.validateMeshGeometry()
            outputScale?.let { poly.scaled(it).validateMeshGeometry() }
        } catch (cause: IllegalArgumentException) {
            return TransformApplication.Failure(
                CoreIssue(CoreIssueCode.InvalidGeometry, transform.tag)
            )
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
        val previousLogicalTransform = previous.validTransforms.getOrNull(commonSize)
        val currentLogicalTransform = current.validTransforms.getOrNull(commonSize)
        val previousOperationTag = previousLogicalTransform?.tag?.withoutTransformTweaks()
        val currentOperationTag = currentLogicalTransform?.tag?.withoutTransformTweaks()
        if (
            previousOperationTag != currentOperationTag &&
            previousOperationTag?.removeSuffix("'") == currentOperationTag?.removeSuffix("'") &&
            previousOperationTag?.removeSuffix("'") in setOf("s", "p", "w", "g")
        ) {
            return emptyList()
        }
        if (
            previousLogicalTransform != null && currentLogicalTransform != null &&
            previousLogicalTransform.tag != currentLogicalTransform.tag &&
            previousOperationTag == currentOperationTag &&
            currentPoly.hasSameTopology(previousPoly)
        ) {
            return listOf(
                CoreAnimationStep(
                    duration,
                    previousPoly.scaled(current.scale),
                    0.0,
                    currentPoly.scaled(current.scale),
                    1.0,
                )
            )
        }
        val previousTransform = previousLogicalTransform
            ?.animationTransform
            ?.takeIf { !it.isIdentityTransform(basePoly) }
            ?: Transform.None
        val currentTransform = currentLogicalTransform
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
