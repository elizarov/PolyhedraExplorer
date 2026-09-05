/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import kotlinx.browser.window
import polyhedra.model.poly.*
import polyhedra.model.util.*
import polyhedra.model.api.*
import polyhedra.model.serialization.ParsedParameter
import polyhedra.web.catalog.*
import polyhedra.web.main.*
import polyhedra.web.params.*
import polyhedra.web.util.Oklch
import polyhedra.web.worker.evaluateInWasm
import polyhedra.web.worker.CoreWorkerException
import kotlin.js.console
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class RenderParams(tag: String, val animationParams: ViewAnimationParams?) : Param.Composite(tag) {
    val poly = using(PolyParams("", animationParams))
    val view = using(ViewParams("v", animationParams))
    val lighting = using(LightingParams("l", animationParams))
    val printPreview = using(PrintPreviewParams("p"))

    init {
        poly.connectRimDimensions(view.faceRim, view.faceWidth)
    }
}

private val defaultSeed = Seed.Tetrahedron
private val defaultScale = Scale.Circumradius
internal const val WORKER_PROGRESS_GRACE_MS = 500

class PolyParams(tag: String, val animationParams: ViewAnimationParams?) : Param.Composite(tag) {
    val seed = using(EnumParam("s", defaultSeed, Seeds))
    val transforms = using(EnumListParam("t", emptyList(), Transforms, String::toTransformOrNull))
    val baseScale = using(EnumParam("bs", defaultScale, Scales))
    val hideFaces = using(SetParam("hf", emptySet()) { it.toFaceKindOrNull() })
    val selectedFace = using(TransientParam<FaceKind?>(null))
    val selectedEdge = using(TransientParam<EdgeKind?>(null))
    val selectedVertex = using(TransientParam<VertexKind?>(null))
    val showSymmetry = using(BooleanParam("sym", false))

    var poly: Polyhedron? = null
        private set
    var polyName: String = "Loading"
        private set
    var transformWarnings: List<IndicatorMessage<*>?> = emptyList()
        private set
    var transformError: TransformError? = null
        private set
    var coreError: String? = null
        private set
    var coreLoaded: Boolean = false
        private set
    var transformProgress: Int = 0
        private set
    var transformAnimation: TransformAnimation? = null
        private set
    var suggestedSeed: Seed? = null
        private set
    var symmetry: CoreSymmetry? = null
        private set
    var geometryAnalysis: CoreGeometryAnalysis? = null
        private set
    var resolvedRims: List<ResolvedRimGeometry> = emptyList()
        private set

    val targetPoly: Polyhedron
        get() = transformAnimation?.targetPoly ?: requireNotNull(poly) { "The Wasm core has not produced a polyhedron yet" }

    var transformedPolys: List<Polyhedron> = emptyList()
        private set

    private var transformTweakRanges: List<Map<TransformTweak, CoreTransformTweakRange>> = emptyList()

    fun transformTweakRangesAt(transformIndex: Int): Map<TransformTweak, CoreTransformTweakRange>? =
        transformTweakRanges.getOrNull(transformIndex)

    internal fun updateTransformTweakRanges(rangesByTransform: List<List<CoreTransformTweakRange>>) {
        transformTweakRanges = rangesByTransform.map { ranges ->
            ranges.associateBy(CoreTransformTweakRange::tweak)
        }
    }

    private var orbitTransforms: List<Set<Transform>> = emptyList()
    val currentOrbitTransforms: Set<Transform>
        get() = orbitTransforms.getOrNull(transformedPolys.size).orEmpty()

    fun availableOrbitTransformsAt(transformIndex: Int): Set<Transform> =
        orbitTransforms.getOrNull(transformIndex).orEmpty()

    private var rememberedFaceOrbit: FaceKind? = null
    private var rememberedEdgeOrbit: EdgeKind? = null
    private var rememberedVertexOrbit: VertexKind? = null

    internal fun rememberOrbitTarget(transform: Transform?) {
        when (val kind = transform?.orbitTargetOrNull()?.kind) {
            is FaceKind -> rememberedFaceOrbit = kind
            is EdgeKind -> rememberedEdgeOrbit = kind
            is VertexKind -> rememberedVertexOrbit = kind
            null -> Unit
            else -> Unit
        }
    }

    internal fun reuseRememberedOrbitTarget(
        transform: Transform,
        supportedTransforms: Set<Transform>,
    ): Transform {
        val target = transform.orbitTargetOrNull() ?: return transform
        val rememberedKind: AnyKind? = when (target.kind) {
            is FaceKind -> rememberedFaceOrbit
            is EdgeKind -> rememberedEdgeOrbit
            is VertexKind -> rememberedVertexOrbit
            else -> null
        }
        return supportedTransforms.firstOrNull { option ->
            option.orbitTargetOrNull()?.let { candidate ->
                candidate.operation == target.operation && candidate.kind == rememberedKind
            } == true
        } ?: transform
    }

    internal fun clearRememberedOrbitTargets() {
        rememberedFaceOrbit = null
        rememberedEdgeOrbit = null
        rememberedVertexOrbit = null
    }

    private var requestId = 0
    private var coreStarted = false
    private var cancelCoreRequest: (() -> Unit)? = null
    private var progressStageIndex: Int? = null
    private var progressGraceTimeout = 0
    private var progressVisible = false

    fun clearRolloverSelection() {
        selectedFace.updateValue(null)
        selectedEdge.updateValue(null)
        selectedVertex.updateValue(null)
    }
    private var requestedState: CoreState? = null
    private var requestedRimWidth: Double? = null
    private var requestedFaceWidth: Double? = null
    private var appliedState: CoreState? = null
    private var suggestedSeedKey: Pair<String, List<String>>? = null

    override fun update(update: Param.UpdateType, dt: Double) {
        transformAnimation?.let { animation ->
            animation.update(dt)
            if (animation.isOver) {
                transformAnimation = null
                notifyUpdated(AnimatedValue)
            } else {
                notifyUpdated(AnimatedValue + ActiveAnimation)
            }
        }
    }

    override fun computeDerivedTargetValues() {
        transforms.value.forEach(::rememberOrbitTarget)
        if (!coreStarted) return
        val state = currentState()
        val rimWidth = rimWidthProvider().takeIf { it > 0.0 }
        val faceWidth = faceWidthProvider().takeIf { rimWidth != null && it > 0.0 }
        if (
            state == requestedState &&
            rimWidth == requestedRimWidth &&
            faceWidth == requestedFaceWidth
        ) return

        val stateKey = state.seedDetectionKey()
        if (suggestedSeedKey != null && suggestedSeedKey != stateKey) {
            suggestedSeed = null
            suggestedSeedKey = null
        }

        requestedState = state
        requestedRimWidth = rimWidth
        requestedFaceWidth = faceWidth
        coreError = null
        resetTransformProgress()
        transformProgress = 0
        transformError = null
        val previousState = appliedState
        val duration = animationParams?.animateValueUpdatesDuration
        val detectSeed = shouldDetectSeed(previousState, state)

        val activeRequestId = ++requestId
        cancelCoreRequest?.invoke()
        cancelCoreRequest = evaluateInWasm(
            request = CoreRequest(
                state = state,
                previousState = previousState,
                animationDuration = duration,
                detectSeed = detectSeed,
                rimWidth = rimWidth,
                faceWidth = faceWidth,
            ),
            reportProgress = progress@{ progress ->
                if (requestId != activeRequestId) return@progress
                updateTransformProgress(progress)
            },
            onSuccess = success@{ response ->
                if (requestId != activeRequestId || requestedState != state) return@success
                cancelCoreRequest = null
                resetTransformProgress()
                applyCoreResponse(state, response)
                performUpdate(null, 0.0)
            },
            onFailure = failure@{ cause ->
                if (requestId != activeRequestId || requestedState != state) return@failure
                cancelCoreRequest = null
                handleCoreFailure(state, cause)
            },
        )
    }

    internal fun handleCoreFailure(state: CoreState, cause: Throwable) {
        val stageIndex = (cause as? CoreWorkerException)?.transformIndex ?: progressStageIndex
        resetTransformProgress()
        coreError = cause.message ?: cause.toString()
        console.error("Wasm core request failed", cause)
        transformError = stageIndex?.let { index ->
            state.transformTags.getOrNull(index)?.toTransformOrNull()?.let { transform ->
                TransformError(index, TransformFailed(transform))
            }
        }
        notifyUpdated(TargetValue)
        performUpdate(null, 0.0)
    }

    fun startCore() {
        if (coreStarted) return
        coreStarted = true
        computeDerivedTargetValues()
    }

    internal fun updateTransformProgress(progress: CoreProgress) {
        val lastTransformIndex = transforms.value.lastIndex.coerceAtLeast(0)
        val stageIndex = progress.transformIndex.coerceIn(0, lastTransformIndex)
        coreLoaded = true
        transformProgress = progress.done.coerceIn(0, 100)
        if (progressStageIndex != stageIndex) startTransformProgressStage(stageIndex)

        if (transformProgress >= 100) {
            resetTransformProgress()
            transformError = null
        } else if (progressVisible) {
            transformError = TransformError(stageIndex, isAsync = true)
        } else {
            scheduleTransformProgress(stageIndex)
        }
        notifyUpdated(Progress)
    }

    private fun startTransformProgressStage(stageIndex: Int) {
        resetTransformProgress()
        progressStageIndex = stageIndex
        transformError = null
    }

    private fun scheduleTransformProgress(stageIndex: Int) {
        if (progressGraceTimeout != 0) return
        val activeRequestId = requestId
        progressGraceTimeout = window.setTimeout({
            progressGraceTimeout = 0
            if (
                requestId == activeRequestId &&
                progressStageIndex == stageIndex &&
                transformProgress < 100
            ) {
                progressVisible = true
                transformError = TransformError(stageIndex, isAsync = true)
                notifyUpdated(Progress)
            }
        }, WORKER_PROGRESS_GRACE_MS)
    }

    private fun resetTransformProgress() {
        if (progressGraceTimeout != 0) {
            window.clearTimeout(progressGraceTimeout)
            progressGraceTimeout = 0
        }
        progressStageIndex = null
        progressVisible = false
    }

    private fun applyResponse(state: CoreState, response: CoreResponse) {
        poly = response.poly
        polyName = response.polyName
        symmetry = response.symmetry
        updateGeometryAnalysis(response.geometryAnalysis)
        updateResolvedRims(response.resolvedRims)
        transformedPolys = response.transformedPolys
        updateTransformTweakRanges(response.transformTweakRanges)
        updateAvailableOrbitTransforms(response.availableOrbitTransforms)
        transformWarnings = response.warnings.map { it?.toIndicatorMessage() }
        transformError = response.errorIndex?.let { index ->
            TransformError(index, response.error?.toIndicatorMessage())
        }
        transformProgress = 100
        coreError = null
        appliedState = state
        updateAnimation(response.animation.toUiAnimation())
        updateSuggestedSeed(state, response)
    }

    /** Applies a response evaluated outside the browser Wasm worker, such as by the Node renderer. */
    fun applyCoreResponse(state: CoreState, response: CoreResponse) {
        applyResponse(state, response)
        notifyUpdated(TargetValue)
    }

    internal fun updateAvailableOrbitTransforms(availableTransforms: List<List<String>>) {
        orbitTransforms = availableTransforms.map { tags ->
            tags.mapNotNullTo(linkedSetOf(), String::toTransformOrNull)
        }
    }

    internal fun updateGeometryAnalysis(analysis: CoreGeometryAnalysis?) {
        geometryAnalysis = analysis
    }

    internal fun updateResolvedRims(rims: List<ResolvedRimGeometry>) {
        resolvedRims = rims
    }

    private var rimWidthProvider: () -> Double = { 0.0 }
    private var faceWidthProvider: () -> Double = { 0.0 }
    private var rimWidthDependency: Param.Dependency? = null
    private var faceWidthDependency: Param.Dependency? = null

    internal fun connectRimDimensions(rimParam: DoubleParam, widthParam: DoubleParam) {
        rimWidthDependency?.destroy()
        faceWidthDependency?.destroy()
        rimWidthProvider = { rimParam.targetValue }
        faceWidthProvider = { widthParam.targetValue }
        rimWidthDependency = rimParam.onNotifyUpdated(Param.TargetValue, ::computeDerivedTargetValues)
        faceWidthDependency = widthParam.onNotifyUpdated(Param.TargetValue, ::computeDerivedTargetValues)
    }

    internal fun updateSuggestedSeed(state: CoreState, response: CoreResponse) {
        val seedTag = response.recognizedSeedTag ?: return
        val familySeed = state.seedTag.toFamilySeedIdOrNull() != null ||
            state.seedTag.toStarFamilySeedIdOrNull() != null
        if ((!familySeed && state.transformTags.isEmpty()) ||
            !response.validTransformTags.isSameTransformChainAs(state.transformTags)
        ) return
        val recognizedSeed = Seeds.firstOrNull { it.tag == seedTag } ?: return

        suggestedSeed = recognizedSeed
        suggestedSeedKey = state.seedDetectionKey()
    }

    fun acceptSuggestedSeed() {
        val replacement = suggestedSeed ?: return
        suggestedSeed = null
        suggestedSeedKey = null

        // With a transform chain, set both values before sending one notification so no intermediate request is made.
        val hasTransforms = transforms.value.isNotEmpty()
        seed.updateValue(replacement, Param.None.takeIf { hasTransforms })
        if (hasTransforms) transforms.updateValue(emptyList())
    }

    private fun currentState() = CoreState(
        seedTag = seed.value.tag,
        transformTags = transforms.value.map(Transform::tag),
        scaleTag = baseScale.value.tag,
    )

    fun updateAnimation(animation: TransformAnimation?) {
        if (transformAnimation == animation) return
        transformAnimation = animation
        if (animation != null) notifyUpdated(ActiveAnimation)
    }

    override fun destroy() {
        requestId++
        resetTransformProgress()
        cancelCoreRequest?.invoke()
        cancelCoreRequest = null
        rimWidthDependency?.destroy()
        rimWidthDependency = null
        super.destroy()
    }
}

internal fun shouldDetectSeed(previous: CoreState?, current: CoreState): Boolean =
    (current.transformTags.isNotEmpty() || current.seedTag.toFamilySeedIdOrNull() != null ||
        current.seedTag.toStarFamilySeedIdOrNull() != null) &&
        (previous == null || previous.seedTag != current.seedTag ||
            previous.transformTags != current.transformTags)

private fun List<String>.isSameTransformChainAs(other: List<String>): Boolean =
    size == other.size && indices.all { index ->
        val transform = this[index].parseTransformTag()
        transform != null && transform == other[index].parseTransformTag()
    }

private fun CoreState.seedDetectionKey(): Pair<String, List<String>> = seedTag to transformTags

private fun List<CoreAnimationStep>.toUiAnimation(): TransformAnimation? {
    val steps = map { step ->
        TransformAnimationStep(
            step.duration,
            TransformKeyframe(step.previousPoly, step.previousFraction),
            TransformKeyframe(step.targetPoly, step.targetFraction),
        )
    }
    return when (steps.size) {
        0 -> null
        1 -> steps.single()
        else -> TransformAnimationList(*steps.toTypedArray())
    }
}

private fun CoreIssue.toIndicatorMessage(): IndicatorMessage<*> {
    val transform = transformTag?.toTransformOrNull() ?: Transform.None
    return when (code) {
        CoreIssueCode.TransformFailed -> TransformFailed(transform)
        CoreIssueCode.InvalidGeometry -> InvalidGeometry(detail ?: "the surface is not proper")
        CoreIssueCode.TransformNotApplicable -> TransformNotApplicable(transform)
        CoreIssueCode.TransformIsIdentity -> TransformIsId(transform)
        CoreIssueCode.TooLarge -> TooLarge(requireNotNull(fev))
        CoreIssueCode.SomeFacesNotPlanar -> SomeFacesNotPlanar()
        CoreIssueCode.GeometryContractNotSatisfied,
        CoreIssueCode.SelfIntersection,
        CoreIssueCode.NonPlanarSelfIntersection,
        CoreIssueCode.DisconnectedMaterial,
        CoreIssueCode.ScaleNotApplicable -> InvalidGeometry(detail ?: code.name)
    }
}

data class TransformError(
    val index: Int,
    val msg: IndicatorMessage<*>? = null,
    val isAsync: Boolean = false,
)

class ViewAnimationParams(tag: String) : Param.Composite(tag), ValueAnimationParams, RotationAnimationParams {
    val animateValueUpdates = using(BooleanParam("u", true))
    val animationDuration = using(DoubleParam("d", 0.5, 0.0, 2.0, 0.1))

    override val animatedRotation: BooleanParam = using(BooleanParam("r", true))
    val rotationSpeed = using(DoubleParam("rs", 0.5, 0.0, 2.0, 0.01))
    val rotationAngle = using(DoubleParam("ra", 60.0, 0.0, 360.0, 1.0))

    override val animateValueUpdatesDuration: Double?
        get() = animationDuration.value.takeIf { it > 0 && animateValueUpdates.value }

    override val animatedRotationAngles: Vec3
        get() {
            val ra = rotationAngle.value * PI / 180
            val rs = rotationSpeed.value
            return Vec3(rs * sin(ra), rs * cos(ra), 0.0)
        }
}

class ViewParams(
    tag: String,
    animationParams: ViewAnimationParams?,
) : Param.Composite(tag) {
    val rotate = using(RotationParam("r", Quat.ID, animationParams, animationParams))
    val scale = using(DoubleParam("s", 0.0, -2.0, 2.0, 0.01, animationParams))
    val expandFaces = using(DoubleParam("e", 0.0, 0.0, 2.0, 0.01, animationParams))
    val cutEnabled = using(BooleanParam("c", false))
    val cutPosition = using(DoubleParam("cp", 0.5, -1.0, 1.0, 0.01, animationParams))
    val transparencyEnabled = using(BooleanParam("t", false))
    val transparentFaces = using(DoubleParam("ta", 0.85, 0.0, 1.0, 0.01, animationParams))
    val faceWidth = using(
        DoubleParam("fw", 0.10, 0.0, 0.2, 0.001, animationParams, serializationPrecision = 8),
    )
    val faceRim = using(
        DoubleParam("fr", 0.05, 0.0, 0.2, 0.001, animationParams, serializationPrecision = 8),
    )
    val symmetryPlaneSize = using(DoubleParam("ps", 1.1, 1.0, 2.0, 0.01))
    val symmetryAxisSize = using(DoubleParam("as", 1.2, 1.0, 2.0, 0.01))
    val display = using(EnumParam("d", Display.All, Displays))
    val environment = using(EnumParam("env", SceneEnvironment.Table, SceneEnvironments))

    override fun loadFrom(parsed: ParsedParam, update: (Param) -> Unit) {
        // Numeric t was the legacy opacity-fade control. New URLs separate mode from amount.
        val values = (parsed as? ParsedParameter.Composite)?.map
        val legacy = (values?.get("t") as? ParsedParameter.Value)?.value?.toDoubleOrNull()
        if (legacy != null && legacy.isFinite()) {
            val migrated = values + mapOf(
                "t" to ParsedParameter.Value(if (legacy > 0.0) "y" else "n"),
                "ta" to (values["ta"] ?: ParsedParameter.Value(legacy.coerceIn(0.0, 1.0).toString())),
            )
            super.loadFrom(ParsedParameter.Composite(migrated), update)
        } else super.loadFrom(parsed, update)
    }
}

class LightingParams(tag: String, animationParams: ViewAnimationParams?) : Param.Composite(tag) {
    val keyLight = using(DoubleParam("d", 2.5, 0.0, 5.0, 0.05, animationParams))
    val fillLight = using(DoubleParam("a", 0.22, 0.0, 1.0, 0.01, animationParams))
    val roughness = using(DoubleParam("r", 0.45, 0.15, 1.0, 0.01, animationParams))
    val ior = using(DoubleParam("i", 1.46, 1.3, 1.7, 0.01, animationParams))
    val acrylicRoughness = using(DoubleParam("ar", 0.12, 0.08, 1.0, 0.01, animationParams))
    val acrylicIor = using(DoubleParam("ai", 1.49, 1.3, 1.7, 0.01, animationParams))
}

internal const val DEFAULT_PRINT_LIGHTNESS = 0.58
internal const val DEFAULT_PRINT_CHROMA = 0.20
internal const val DEFAULT_PRINT_HUE = 28.0

class PrintPreviewParams(tag: String) : Param.Composite(tag) {
    val enabled = using(BooleanParam("e", false))
    val lightness = using(DoubleParam("l", DEFAULT_PRINT_LIGHTNESS, 0.20, 0.95, 0.01))
    val chroma = using(DoubleParam("c", DEFAULT_PRINT_CHROMA, 0.0, 0.30, 0.005))
    val hue = using(DoubleParam("h", DEFAULT_PRINT_HUE, 0.0, 360.0, 1.0))

    fun updateColor(color: Oklch) {
        lightness.updateValue(color.lightness)
        chroma.updateValue(color.chroma)
        hue.updateValue(color.hue)
    }
}

class ExportParams(tag: String) : Param.Composite(tag) {
    val size = using(DoubleParam("s", 40.0, 10.0, 100.0, 0.1))
}
