package polyhedra.core.api

import polyhedra.core.poly.*
import polyhedra.core.transform.*
import polyhedra.core.util.OperationProgressContext
import polyhedra.model.api.*
import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.math.abs

private const val ANIMATION_GAP = 1e-4
// Eight bisections resolve even the widest envelope more finely than the UI's 0.01 step.
private const val SAFE_RANGE_SEARCH_STEPS = 8
private const val SAFE_RANGE_ANCHOR_SAMPLES = 20

private data class LogicalTransform(
    val spec: TransformSpec,
    val name: String,
    val primitiveTransforms: List<Transform>,
    /** Primitive visual stages; macros can differ from their fused evaluation kernel. */
    val animationTransforms: List<Transform> = primitiveTransforms,
) {
    val tag: String get() = spec.tag
    val animationTransform: Transform?
        get() = animationTransforms.singleOrNull()

    fun isIdentityTransform(poly: Polyhedron): Boolean =
        primitiveTransforms.size == 1 && primitiveTransforms.single().isIdentityTransform(poly)

    override fun toString(): String = name
}

private fun TransformSpec.toLogicalTransformOrNull(): LogicalTransform? {
    id.toTransformMacroOrNull()?.let { macro ->
        if (!tweaks.keys.all { it in id.transformTweakRanges() }) return null

        val primitives = when (id.operation) {
            TransformOperation.Kis -> listOf(
                KisAll(tweaks[TransformTweak.Height] ?: 1.0)
            )
            TransformOperation.Join -> listOf(Transform.Dual, Transform.Rectified, Transform.Dual)
            TransformOperation.Needle -> listOf(
                Transform.Truncated.withTweaks(tweaks),
                Transform.Dual,
            )
            TransformOperation.Zip -> listOf(
                Transform.Dual,
                Transform.Truncated.withTweaks(tweaks),
            )
            TransformOperation.Cantellated -> listOf(
                Transform.Cantellated.withTweaks(tweaks)
            )
            TransformOperation.Bevelled -> listOf(
                Transform.Bevelled.withTweaks(tweaks)
            )
            TransformOperation.Ortho -> listOf(
                Transform.Dual,
                Transform.Cantellated.withTweaks(tweaks),
                Transform.Dual,
            )
            TransformOperation.Meta -> listOf(
                Transform.Dual,
                Transform.Bevelled.withTweaks(tweaks),
                Transform.Dual,
            )
            TransformOperation.Gyro -> listOf(
                Transform.Dual,
                (if (macro.chirality == Chirality.Flipped) Transform.SnubFlipped else Transform.Snub)
                    .withTweaks(tweaks),
                Transform.Dual,
            )
            else -> return null
        }
        val animationTransforms = when (id.operation) {
            // The direct Kis kernel supports height but has no stable collapsed topology. Its
            // Conway expansion is composed entirely of well-behaved animated operations.
            TransformOperation.Kis -> listOf(Transform.Dual, Transform.Truncated, Transform.Dual)
            else -> primitives
        }
        return LogicalTransform(
            this,
            macro.displayName,
            primitives,
            animationTransforms,
        )
    }
    return toTransformOrNull()?.let { transform ->
        val resultNumber = tweaks[TransformTweak.StellationResult]
            ?.takeIf {
                (id.operation == TransformOperation.Greatened || id.operation == TransformOperation.Stellated) &&
                    it > 1.0
            }
            ?.toInt()
        LogicalTransform(
            this,
            transform.toString() + resultNumber?.let { " $it" }.orEmpty(),
            listOf(transform),
        )
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
        rimWidth = request.rimWidth,
        faceWidth = request.faceWidth,
    )
    val duration = request.animationDuration?.takeIf { it > 0.0 }
    val previousState = request.previousState
    if (duration == null || previousState == null || previousState == request.state) {
        reportCompletion(current, reportProgress)
        return current.response
    }

    // Greatened and Stellated Result changes are discrete and have no animation keyframes. Avoid
    // rebuilding the previous large candidate merely to discover that the animation is empty.
    if (request.state.hasNonAnimatedStarResultChangeFrom(previousState)) {
        reportCompletion(current, reportProgress)
        return current.response
    }

    // A seed change cannot be animated, so evaluating the old transform chain would only repeat
    // potentially expensive operations such as canonicalization before returning no animation.
    if (previousState.seedTag != request.state.seedTag) {
        reportCompletion(current, reportProgress)
        return current.response
    }

    // The previous response is internal animation input; none of its symmetry metadata is consumed.
    // Reuse the current payload instead of running a second geometric automorphism search.
    val previous = evaluateState(
        previousState,
        {},
        detectSeed = false,
        computeTweakRanges = false,
        symmetryOverride = current.response.symmetry,
        rimWidth = null,
        faceWidth = null,
    )
    val animation = runCatching {
        computeAnimation(previous, current, duration).also { steps ->
            // Polyhedron serialization materializes resolved-face presentation lazily. Do it
            // inside the best-effort animation boundary so a bad intermediate keyframe cannot
            // discard the already completed transform response in the worker.
            steps.forEach { step ->
                step.previousPoly.resolvedFaces.forEach { face -> face.triangles.size }
                step.targetPoly.resolvedFaces.forEach { face -> face.triangles.size }
            }
        }
    }.getOrElse { cause ->
        println("Animation skipped: ${cause.message ?: cause::class.simpleName}")
        cause.printStackTrace()
        emptyList()
    }
    reportCompletion(current, reportProgress)
    return current.response.copy(animation = animation)
}

private fun reportCompletion(evaluation: Evaluation, reportProgress: (CoreProgress) -> Unit) {
    if (evaluation.response.errorIndex != null) return
    reportProgress(CoreProgress(evaluation.validTransforms.lastIndex.coerceAtLeast(0), 100))
}

private fun CoreState.hasNonAnimatedStarResultChangeFrom(previous: CoreState): Boolean {
    if (seedTag != previous.seedTag || scaleTag != previous.scaleTag || transformTags.size != previous.transformTags.size) {
        return false
    }
    val changed = transformTags.indices.filter { index -> transformTags[index] != previous.transformTags[index] }
    if (changed.size != 1 || changed.single() != transformTags.lastIndex) return false
    val currentSpec = transformTags.last().parseTransformTag() ?: return false
    val previousSpec = previous.transformTags.last().parseTransformTag() ?: return false
    if (currentSpec.id != previousSpec.id) return false
    if (currentSpec.id.operation != TransformOperation.Greatened &&
        currentSpec.id.operation != TransformOperation.Stellated
    ) return false
    return currentSpec.tweaks - TransformTweak.StellationResult ==
        previousSpec.tweaks - TransformTweak.StellationResult
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
    symmetryOverride: CoreSymmetry? = null,
    rimWidth: Double? = null,
    faceWidth: Double? = null,
): Evaluation {
    val seed = state.seedTag.toSeedOrNull()
        ?: error("Unknown seed tag: ${state.seedTag}")
    val scale = Scales.firstOrNull { it.tag == state.scaleTag }
        ?: error("Unknown scale tag: ${state.scaleTag}")
    val transforms = state.transformTags.map { tag ->
        tag.parseTransformTag()?.toLogicalTransformOrNull() ?: error("Unknown transform tag: $tag")
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
        var lastReportedDone = -1
        val reportTransformProgress: (Int) -> Unit = { done ->
            val clamped = done.coerceIn(0, 100)
            if (clamped > lastReportedDone) {
                lastReportedDone = clamped
                reportProgress(CoreProgress(index, clamped))
            }
        }
        reportTransformProgress(0)
        val reportsCandidateProgress = computeTweakRanges && (
            transform.spec.id.operation == TransformOperation.Greatened ||
                transform.spec.id.operation == TransformOperation.Stellated
            )
        if (computeTweakRanges) {
            val rangeProgress = if (reportsCandidateProgress) {
                OperationProgressContext { done ->
                    reportTransformProgress(if (done <= 0) 0 else 1 + done.coerceAtMost(100) * 94 / 100)
                }
            } else {
                null
            }
            transformTweakRanges += transform.safeTweakRanges(poly, pendingRectification, scale, rangeProgress)
        }
        val applicationProgress = if (reportsCandidateProgress) {
            { done: Int -> reportTransformProgress(95 + done * 3 / 100) }
        } else {
            reportTransformProgress
        }
        when (
            val application = applyTransform(
                transform,
                poly,
                pendingRectification,
                applicationProgress,
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
        availableOrbitTransforms += poly.cachedConstellationOrbitTransformTagsOrNull()
            ?: poly.availableOrbitTransforms.map(Transform::tag).sorted()
        if (reportsCandidateProgress) reportTransformProgress(99)
    }

    val displayPoly = poly.scaled(scale)
    val response = CoreResponse(
        poly = displayPoly,
        polyName = polyName,
        symmetry = symmetryOverride
            ?: poly.cachedConstellationSymmetryOrNull()
            ?: poly.analyzeSymmetry(),
        recognizedSeedTag = if (
            detectSeed && errorIndex == null &&
            (validTransforms.isNotEmpty() || seed.type == SeedType.Families ||
                seed.type == SeedType.StarFamilies)
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
        geometryAnalysis = poly.cachedConstellationGeometryAnalysisOrNull() ?: poly.analyzeGeometry(),
        resolvedRims = rimWidth?.takeIf { it > 0.0 }?.let { width ->
            val thickness = faceWidth?.takeIf { it > 0.0 } ?: 0.0
            poly.cachedConstellationResolvedRimsOrNull(scale, width, thickness)
                ?: displayPoly.resolvedRims(width, thickness)
        }.orEmpty(),
    )
    return Evaluation(state, seed, scale, validTransforms, poly, response)
}

private suspend fun LogicalTransform.safeTweakRanges(
    inputPoly: Polyhedron,
    inputPendingRectification: PendingRectification?,
    outputScale: Scale,
    progress: OperationProgressContext? = null,
): List<CoreTransformTweakRange> {
    when (spec.id.operation) {
        TransformOperation.Greatened,
        TransformOperation.Stellated -> {
            val operation = if (spec.id.operation == TransformOperation.Greatened) {
                ConstellationOperation.Greaten
            } else {
                ConstellationOperation.Stellate
            }
            val candidates = inputPoly.stellationCandidatesAsync(operation, progress)
            if (candidates.isEmpty()) return emptyList()
            return listOf(
                CoreTransformTweakRange(
                    tweak = TransformTweak.StellationResult,
                    min = 1.0,
                    max = candidates.size.toDouble(),
                    options = candidates.mapIndexed { index, candidate ->
                        CoreTransformTweakOption(index + 1, candidate.fev)
                    },
                ),
            )
        }
        else -> Unit
    }
    val envelopes = spec.id.transformTweakRanges()
    if (envelopes.isEmpty()) return emptyList()

    val result = ArrayList<CoreTransformTweakRange>(envelopes.size)
    for ((tweak, envelope) in envelopes) {
        safeTweakRange(spec, tweak, envelope, inputPoly, inputPendingRectification, outputScale)
            ?.let(result::add)
    }
    return result
}

private suspend fun safeTweakRange(
    spec: TransformSpec,
    tweak: TransformTweak,
    envelope: TransformTweakRange,
    inputPoly: Polyhedron,
    inputPendingRectification: PendingRectification?,
    outputScale: Scale,
): CoreTransformTweakRange? {
    suspend fun isValid(value: Double): Boolean {
        val tweaks = spec.tweaks.toMutableMap().apply {
            if (value == 1.0) remove(tweak) else put(tweak, value)
        }
        val candidate = spec.copy(tweaks = tweaks).toLogicalTransformOrNull() ?: return false
        return applyTransform(
            candidate,
            inputPoly,
            inputPendingRectification,
            reportProgress = {},
            outputScale = outputScale,
        ) is TransformApplication.Success
    }

    val current = (spec.tweaks[tweak] ?: 1.0).coerceIn(envelope.min, envelope.max)
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

    val minimum = findBoundary(envelope.min)
    val maximum = findBoundary(envelope.max)
    val landmarks = if (
        spec.id.operation == TransformOperation.StellateFace &&
        tweak == TransformTweak.Radius
    ) {
        val kind = spec.id.target as? FaceKind
        kind?.let(inputPoly::stellateFaceCoplanarRadii).orEmpty()
            .filter { value -> value in minimum..maximum && isValid(value) }
    } else {
        emptyList()
    }
    return CoreTransformTweakRange(
        tweak = tweak,
        min = minimum,
        max = maximum,
        landmarks = landmarks,
    )
}

private suspend fun applyTransform(
    transform: LogicalTransform,
    inputPoly: Polyhedron,
    inputPendingRectification: PendingRectification?,
    reportProgress: (Int) -> Unit,
    outputScale: Scale? = null,
): TransformApplication {
    var poly = inputPoly
    val logicalInputContract = (
        inputPoly.cachedConstellationGeometryAnalysisOrNull() ?: inputPoly.analyzeGeometry()
    ).strongestContract
    var pendingRectification = inputPendingRectification
    var isIdentity = false
    var outputContract = PolyhedronContract.EmbeddedBoundary

    val primitiveCount = transform.primitiveTransforms.size
    for ((primitiveIndex, primitive) in transform.primitiveTransforms.withIndex()) {
        val reportPrimitiveProgress: (Int) -> Unit = { done ->
            reportProgress((primitiveIndex * 100 + done.coerceIn(0, 100)) / primitiveCount)
        }
        reportPrimitiveProgress(0)
        val applicability = primitive.applicability(poly)
        if (!applicability.isApplicable) {
            return TransformApplication.Failure(
                CoreIssue(
                    CoreIssueCode.TransformNotApplicable,
                    transform.tag,
                    detail = applicability.rejectionReason,
                )
            )
        }
        val expectedFev = primitive.fev?.let { it * poly.fev() }
        if (expectedFev != null && expectedFev.e > MAX_POLYHEDRON_EDGES) {
            return TransformApplication.Failure(
                CoreIssue(CoreIssueCode.TooLarge, transform.tag, expectedFev)
            )
        }

        val primitiveInput = poly
        val pendingInput = pendingRectification
        val primitiveOutputContract = applicability.outputContract
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

                transform.spec.id.operation == TransformOperation.Gyro &&
                    primitive is Dual && primitiveIndex == transform.primitiveTransforms.lastIndex ->
                    poly.directDual().also { result ->
                        if (logicalInputContract == PolyhedronContract.EmbeddedBoundary) {
                            result.validateProperGeometry()
                        }
                    }

                else -> primitive.asyncTransform?.invoke(poly, OperationProgressContext(reportPrimitiveProgress))
                    ?: primitive.transform(poly)
            }
            // A later primitive (especially Dual's canonical fallback) must not turn an improper
            // intermediate into an apparently valid macro result. Each logical construction stage
            // is part of the operation's geometric meaning and must satisfy the surface contract.
            when (primitiveOutputContract) {
                PolyhedronContract.RenderableImmersion -> poly.validateRenderableImmersion()
                PolyhedronContract.EmbeddedBoundary -> poly.validateProperGeometry()
                PolyhedronContract.AbstractSurface -> error("Primitive output must be renderable")
            }
            outputContract = primitiveOutputContract
        } catch (cause: TransformApplicabilityException) {
            return TransformApplication.Failure(
                CoreIssue(cause.issueCode, transform.tag, detail = cause.message)
            )
        } catch (cause: IllegalArgumentException) {
            return TransformApplication.Failure(
                CoreIssue(CoreIssueCode.InvalidGeometry, transform.tag, detail = cause.message)
            )
        } catch (cause: Throwable) {
            cause.printStackTrace()
            return TransformApplication.Failure(
                CoreIssue(CoreIssueCode.TransformFailed, transform.tag, detail = cause.message)
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

    try {
        when (outputContract) {
            PolyhedronContract.RenderableImmersion -> poly.validateRenderableImmersion()
            PolyhedronContract.EmbeddedBoundary -> poly.validateProperGeometry()
            PolyhedronContract.AbstractSurface -> error("Transform output must be renderable")
        }
        // Uniform positive scaling preserves intersections; only recheck finite coordinates and
        // orientation because a non-convex radius denominator can be zero or negative.
        outputScale?.let { scale ->
            val scaled = poly.scaled(scale)
            when (outputContract) {
                PolyhedronContract.RenderableImmersion -> scaled.validateRenderableImmersion()
                PolyhedronContract.EmbeddedBoundary -> scaled.validateMeshGeometry()
                PolyhedronContract.AbstractSurface -> error("Transform output must be renderable")
            }
        }
    } catch (cause: TransformApplicabilityException) {
        return TransformApplication.Failure(
            CoreIssue(cause.issueCode, transform.tag, detail = cause.message)
        )
    } catch (cause: IllegalArgumentException) {
        return TransformApplication.Failure(
            CoreIssue(CoreIssueCode.InvalidGeometry, transform.tag, detail = cause.message)
        )
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
        val previousId = previousLogicalTransform?.spec?.id
        val currentId = currentLogicalTransform?.spec?.id
        if (
            previousId != null && currentId != null &&
            previousId.operation == currentId.operation &&
            previousId.chirality != currentId.chirality &&
            previousId.operation.isChiral
        ) {
            return emptyList()
        }
        if (
            previousLogicalTransform != null && currentLogicalTransform != null &&
            previousLogicalTransform.spec != currentLogicalTransform.spec &&
            previousId == currentId &&
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
        val previousAnimationTransform = previousLogicalTransform
            ?.takeUnless { transform -> transform.isIdentityTransform(basePoly) }
        val currentAnimationTransform = currentLogicalTransform
            ?.takeUnless { transform -> transform.isIdentityTransform(basePoly) }
        if (
            previousAnimationTransform?.animationTransforms?.size?.let { it > 1 } == true ||
            currentAnimationTransform?.animationTransforms?.size?.let { it > 1 } == true
        ) {
            return macroAnimation(
                basePoly,
                current.scale,
                previousPoly,
                currentPoly,
                previousAnimationTransform,
                currentAnimationTransform,
                duration,
            ) ?: emptyList()
        }
        val previousTransform = previousAnimationTransform
            ?.animationTransform
            ?: Transform.None
        val currentTransform = currentAnimationTransform
            ?.animationTransform
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

private fun macroAnimation(
    basePoly: Polyhedron,
    scale: Scale,
    previousPoly: Polyhedron,
    currentPoly: Polyhedron,
    previousTransform: LogicalTransform?,
    currentTransform: LogicalTransform?,
    duration: Double,
): List<CoreAnimationStep>? {
    val removing = previousTransform?.let { transform ->
        transform.macroDirectionAnimation(basePoly, scale, previousPoly, duration, applying = false)
            ?: return null
    }.orEmpty().withoutStationarySteps(duration)
    val applying = currentTransform?.let { transform ->
        transform.macroDirectionAnimation(basePoly, scale, currentPoly, duration, applying = true)
            ?: return null
    }.orEmpty().withoutStationarySteps(duration)
    return removing + applying
}

/** Runs every primitive component of a multi-part macro on one shared animation clock. */
private fun LogicalTransform.macroDirectionAnimation(
    basePoly: Polyhedron,
    scale: Scale,
    resultPoly: Polyhedron,
    duration: Double,
    applying: Boolean,
): List<CoreAnimationStep>? {
    if (animationTransforms.size > 1) {
        return listOf(macroAnimationStep(basePoly, scale, resultPoly, this, duration, applying) ?: return null)
    }
    val transform = animationTransforms.singleOrNull() ?: return null
    return transformAnimation(
        basePoly,
        scale,
        if (applying) basePoly else resultPoly,
        if (applying) resultPoly else basePoly,
        if (applying) Transform.None else transform,
        if (applying) transform else Transform.None,
        duration,
    ).takeIf { it.isNotEmpty() }
}

private fun Polyhedron.hasSameAnimationGeometry(other: Polyhedron): Boolean =
    hasSameTopology(other) && vs.indices.all { index -> vs[index] approx other.vs[index] }

private fun List<CoreAnimationStep>.withoutStationarySteps(
    totalDuration: Double? = null,
): List<CoreAnimationStep> {
    val active = filterNot { step -> step.previousPoly.hasSameAnimationGeometry(step.targetPoly) }
    if (totalDuration == null || active.isEmpty()) return active
    val durationScale = totalDuration / active.sumOf(CoreAnimationStep::duration)
    return active.map { step -> step.copy(duration = step.duration * durationScale) }
}

private fun macroAnimationStep(
    basePoly: Polyhedron,
    scale: Scale,
    resultPoly: Polyhedron,
    transform: LogicalTransform,
    duration: Double,
    applying: Boolean,
): CoreAnimationStep? {
    val result = resultPoly.scaled(scale).triangulatedForAnimation()
    val originScale = 1.0 / basePoly.scaleDenominator(scale)
    val origins = transform.macroAnimationOrigins(basePoly, resultPoly)
        .map { position -> position * originScale }
    if (origins.size != result.vs.size) return null
    val nearCollapsed = result.withAnimationPositions(result.vs.indices.map { index ->
        ANIMATION_GAP.atSegment(origins[index], result.vs[index])
    })
    return if (applying) {
        CoreAnimationStep(duration, nearCollapsed, ANIMATION_GAP, result, 1.0)
    } else {
        CoreAnimationStep(duration, result, 0.0, nearCollapsed, 1.0 - ANIMATION_GAP)
    }
}

/** Maps the final macro topology onto the input while all primitive components are at 0%. */
private fun LogicalTransform.macroAnimationOrigins(
    basePoly: Polyhedron,
    resultPoly: Polyhedron,
): List<Vec3> {
    val positions = when (spec.id.operation) {
        // Full Kis retains all input vertices and appends one apex per input face. Collapsing an
        // apex to a boundary vertex removes its spike while keeping the final topology available.
        TransformOperation.Kis -> basePoly.vs + basePoly.fs.map { face -> face.fvs.first() }
        // Zip has a precise directed-edge correspondence.
        TransformOperation.Zip -> basePoly.dual().directedEdges.map { edge -> basePoly.vs[edge.r.id] }
        // The remaining Conway macros preserve orientation. Assign every final vertex to the
        // input vertex in the same radial sector, collapsing all component-created vertices at
        // once instead of exposing any intermediate logical solid.
        else -> resultPoly.vs.map { resultVertex ->
            basePoly.vs.minBy { sourceVertex ->
                (resultVertex / resultVertex.norm - sourceVertex / sourceVertex.norm).norm
            }
        }
    }
    require(positions.size == resultPoly.vs.size) {
        "Macro $tag animation has ${positions.size} origins for ${resultPoly.vs.size} vertices"
    }
    return positions
}

private fun Polyhedron.withAnimationPositions(positions: List<Vec3>): Polyhedron {
    require(positions.size == vs.size)
    val topology = this
    return polyhedron {
        positions.forEachIndexed { index, position -> vertex(position, topology.vs[index].kind) }
        faces(topology.fs)
        faceKindSources(topology.faceKindSources)
    }
}

/** Keeps every interpolated face planar; the completed polygon mesh replaces these triangles. */
private fun Polyhedron.triangulatedForAnimation(): Polyhedron {
    val topology = this
    return polyhedron {
        vertices(topology.vs)
        for (face in topology.fs) {
            for (triangle in face.triangles) {
                face(
                    listOf(face[triangle.c], face[triangle.b], face[triangle.a]),
                    face.kind,
                )
            }
        }
        faceKindSources(topology.faceKindSources)
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
    if (previousTransform is Snub && currentTransform is Snub &&
        previousTransform.chirality != currentTransform.chirality
    ) return emptyList()
    if (previousTransform is Propeller && currentTransform is Propeller &&
        previousTransform.chirality != currentTransform.chirality
    ) return emptyList()
    if (previousTransform is Whirl && currentTransform is Whirl &&
        previousTransform.chirality != currentTransform.chirality
    ) return emptyList()
    if (previousTransform is Greatened || currentTransform is Greatened ||
        previousTransform is Stellated || currentTransform is Stellated
    ) return emptyList()
    if ((previousTransform is Dual || currentTransform is Dual) &&
        (basePoly.cachedConstellationGeometryAnalysisOrNull() ?: basePoly.analyzeGeometry())
            .strongestContract != PolyhedronContract.EmbeddedBoundary
    ) return emptyList()
    if (!basePoly.isConvexGeometry && basePoly.regularStarFormOrNull() != null &&
        (previousTransform is Dual || currentTransform is Dual)
    ) return emptyList()
    if (previousTransform is KisFace || currentTransform is KisFace) return emptyList()
    if (previousTransform is StellateFace && currentTransform == Transform.None) {
        return listOfNotNull(
            stellateFaceAnimationStep(basePoly, scale, previousPoly, previousTransform, duration, applying = false)
        )
    }
    if (previousTransform == Transform.None && currentTransform is StellateFace) {
        return listOfNotNull(
            stellateFaceAnimationStep(basePoly, scale, currentPoly, currentTransform, duration, applying = true)
        )
    }
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

    surfaceSubdivisionAnimation(
        basePoly,
        scale,
        previousPoly,
        currentPoly,
        previousTransform,
        currentTransform,
        duration,
    )?.let { return it }

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
                duration,
            ).withoutStationarySteps() + transformAnimation(
                basePoly,
                scale,
                basePoly,
                currentPoly,
                Transform.None,
                currentTransform,
                duration,
            ).withoutStationarySteps()

        else -> emptyList()
    }
}

private fun stellateFaceAnimationStep(
    basePoly: Polyhedron,
    scale: Scale,
    resultPoly: Polyhedron,
    transform: StellateFace,
    duration: Double,
    applying: Boolean,
): CoreAnimationStep? {
    val result = resultPoly.scaled(scale).triangulatedForAnimation()
    val originScale = 1.0 / basePoly.scaleDenominator(scale)
    val origins = basePoly.vs + basePoly.fs.filter { face -> face.kind == transform.kind }.map { face ->
        face.fvs.fold<Vertex, Vec3>(Vec3.ZERO) { sum, vertex -> sum + vertex } / face.size.toDouble()
    }
    if (origins.size != result.vs.size) return null
    val nearCollapsed = result.withAnimationPositions(origins.mapIndexed { index, origin ->
        ANIMATION_GAP.atSegment(origin * originScale, result.vs[index])
    })
    return if (applying) {
        CoreAnimationStep(duration, nearCollapsed, ANIMATION_GAP, result, 1.0)
    } else {
        CoreAnimationStep(duration, result, 0.0, nearCollapsed, 1.0 - ANIMATION_GAP)
    }
}

/**
 * Propeller, Whirl, and Quinto first subdivide the source faces without moving the surface, then
 * morph that topology into the canonical output. This gives both sides identical buffers without
 * collapsing faces or inventing an unstable vertex correspondence.
 */
private fun surfaceSubdivisionAnimation(
    basePoly: Polyhedron,
    scale: Scale,
    previousPoly: Polyhedron,
    currentPoly: Polyhedron,
    previousTransform: Transform,
    currentTransform: Transform,
    duration: Double,
): List<CoreAnimationStep>? {
    val applying = previousTransform == Transform.None
    val transform = when {
        applying -> currentTransform
        currentTransform == Transform.None -> previousTransform
        else -> return null
    }
    val subdivision = when (transform) {
        is Propeller -> basePoly.propellerAnimationStart(transform.chirality)
        is Whirl -> basePoly.whirlAnimationStart(transform.chirality)
        is Quinto -> basePoly.quintoAnimationStart()
        else -> return null
    }
    val transformed = if (applying) currentPoly else previousPoly
    if (!subdivision.hasSameTopology(transformed)) return emptyList()

    // The subdivision lies exactly on the input surface. Scale it by the input denominator so the
    // first/last visual frame also coincides under Midradius, whose value changes after subdivision.
    val subdivisionScale = 1.0 / basePoly.scaleDenominator(scale)
    return if (applying) {
        listOf(
            CoreAnimationStep(
                duration,
                subdivision.scaled(subdivisionScale),
                0.0,
                currentPoly.scaled(scale),
                1.0,
            )
        )
    } else {
        listOf(
            CoreAnimationStep(
                duration,
                previousPoly.scaled(scale),
                0.0,
                subdivision.scaled(subdivisionScale),
                1.0,
            )
        )
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
