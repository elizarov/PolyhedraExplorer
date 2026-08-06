/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import polyhedra.model.poly.*
import polyhedra.model.util.*
import polyhedra.model.api.*
import polyhedra.web.catalog.*
import polyhedra.web.main.*
import polyhedra.web.params.*
import polyhedra.web.worker.evaluateInWasm
import kotlin.js.console
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class RenderParams(tag: String, val animationParams: ViewAnimationParams?) : Param.Composite(tag) {
    val poly = using(PolyParams("", animationParams))
    val view = using(ViewParams("v", animationParams))
    val lighting = using(LightingParams("l", animationParams))
}

private val defaultSeed = Seed.Tetrahedron
private val defaultScale = Scale.Circumradius

class PolyParams(tag: String, val animationParams: ViewAnimationParams?) : Param.Composite(tag) {
    val seed = using(EnumParam("s", defaultSeed, Seeds))
    val transforms = using(EnumListParam("t", emptyList(), Transforms, String::toTransformOrNull))
    val baseScale = using(EnumParam("bs", defaultScale, Scales))
    val hideFaces = using(SetParam("hf", emptySet()) { it.toFaceKindOrNull() })
    val selectedFace = using(TransientParam<FaceKind?>(null))
    val selectedEdge = using(TransientParam<EdgeKind?>(null))
    val selectedVertex = using(TransientParam<VertexKind?>(null))

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
    var transformProgress: Int = 0
        private set
    var transformAnimation: TransformAnimation? = null
        private set
    var suggestedSeed: Seed? = null
        private set

    val targetPoly: Polyhedron
        get() = transformAnimation?.targetPoly ?: requireNotNull(poly) { "The Wasm core has not produced a polyhedron yet" }

    var transformedPolys: List<Polyhedron> = emptyList()
        private set

    private var dropKinds: List<Set<AnyKind>> = emptyList()
    val currentCanDrop: Set<AnyKind>
        get() = dropKinds.getOrNull(transformedPolys.size).orEmpty()

    fun availableDropsAt(transformIndex: Int): Set<AnyKind> =
        dropKinds.getOrNull(transformIndex).orEmpty()

    private var requestId = 0
    private var coreStarted = false
    private var cancelCoreRequest: (() -> Unit)? = null

    fun clearRolloverSelection() {
        selectedFace.updateValue(null)
        selectedEdge.updateValue(null)
        selectedVertex.updateValue(null)
    }
    private var requestedState: CoreState? = null
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
        if (!coreStarted) return
        val state = currentState()
        if (state == requestedState) return

        val stateKey = state.seedDetectionKey()
        if (suggestedSeedKey != null && suggestedSeedKey != stateKey) {
            suggestedSeed = null
            suggestedSeedKey = null
        }

        requestedState = state
        coreError = null
        transformProgress = 0
        transformError = TransformError(firstChangedTransform(appliedState, state), isAsync = true)
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
            ),
            reportProgress = progress@{ done ->
                if (requestId != activeRequestId) return@progress
                transformProgress = done
                notifyUpdated(Progress)
            },
            onSuccess = success@{ response ->
                if (requestId != activeRequestId || requestedState != state) return@success
                cancelCoreRequest = null
                applyResponse(state, response)
                notifyUpdated(TargetValue)
                performUpdate(null, 0.0)
            },
            onFailure = failure@{ cause ->
                if (requestId != activeRequestId || requestedState != state) return@failure
                cancelCoreRequest = null
                coreError = cause.message ?: cause.toString()
                console.error("Wasm core request failed", cause)
                transformError = state.transformTags.firstOrNull()?.toTransformOrNull()?.let {
                    TransformError(0, TransformFailed(it))
                }
                notifyUpdated(TargetValue)
                performUpdate(null, 0.0)
            },
        )
    }

    fun startCore() {
        if (coreStarted) return
        coreStarted = true
        computeDerivedTargetValues()
    }

    private fun applyResponse(state: CoreState, response: CoreResponse) {
        poly = response.poly
        polyName = response.polyName
        transformedPolys = response.transformedPolys
        updateAvailableDrops(response.availableDrops)
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

    internal fun updateAvailableDrops(availableDrops: List<List<String>>) {
        dropKinds = availableDrops.map { tags ->
            tags.mapNotNullTo(linkedSetOf(), String::toAnyKindOrNull)
        }
    }

    internal fun updateSuggestedSeed(state: CoreState, response: CoreResponse) {
        val seedTag = response.recognizedSeedTag ?: return
        if (state.transformTags.isEmpty() || response.validTransformTags != state.transformTags) return
        val recognizedSeed = Seeds.firstOrNull { it.tag == seedTag } ?: return

        suggestedSeed = recognizedSeed
        suggestedSeedKey = state.seedDetectionKey()
    }

    fun acceptSuggestedSeed() {
        val replacement = suggestedSeed ?: return
        suggestedSeed = null
        suggestedSeedKey = null

        // Set both values before sending one target-value notification, so no intermediate worker request is made.
        seed.updateValue(replacement, Param.None)
        transforms.updateValue(emptyList())
    }

    private fun currentState() = CoreState(
        seedTag = seed.value.tag,
        transformTags = transforms.value.map(Transform::tag),
        scaleTag = baseScale.value.tag,
    )

    private fun firstChangedTransform(previous: CoreState?, current: CoreState): Int {
        if (previous == null || previous.seedTag != current.seedTag) return 0
        val common = previous.transformTags.zip(current.transformTags).takeWhile { (a, b) -> a == b }.size
        return common.coerceAtMost(current.transformTags.lastIndex.coerceAtLeast(0))
    }

    fun updateAnimation(animation: TransformAnimation?) {
        if (transformAnimation == animation) return
        transformAnimation = animation
        if (animation != null) notifyUpdated(ActiveAnimation)
    }

    override fun destroy() {
        requestId++
        cancelCoreRequest?.invoke()
        cancelCoreRequest = null
        super.destroy()
    }
}

internal fun shouldDetectSeed(previous: CoreState?, current: CoreState): Boolean =
    current.transformTags.isNotEmpty() &&
        (previous == null || previous.seedTag != current.seedTag || previous.transformTags != current.transformTags)

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
        CoreIssueCode.TransformNotApplicable -> TransformNotApplicable(transform)
        CoreIssueCode.TransformIsIdentity -> TransformIsId(transform)
        CoreIssueCode.TooLarge -> TooLarge(requireNotNull(fev))
        CoreIssueCode.SomeFacesNotPlanar -> SomeFacesNotPlanar()
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
    val transparentFaces = using(DoubleParam("t", 0.0, 0.0, 1.0, 0.01, animationParams))
    val faceWidth = using(DoubleParam("fw", 0.10, 0.0, 0.2, 0.001, animationParams))
    val faceRim = using(DoubleParam("fr", 0.05, 0.0, 0.2, 0.001, animationParams))
    val display = using(EnumParam("d", Display.All, Displays))
}

class LightingParams(tag: String, animationParams: ViewAnimationParams?) : Param.Composite(tag) {
    val ambientLight = using(DoubleParam("a", 0.25, 0.0, 1.0, 0.01, animationParams))
    val diffuseLight = using(DoubleParam("d", 1.0, 0.0, 1.0, 0.01, animationParams))
    val specularLight = using(DoubleParam("s", 1.0, 0.0, 1.0, 0.01, animationParams))
    val specularPower = using(DoubleParam("sp", 30.0, 0.0, 100.0, 1.0, animationParams))
}

class ExportParams(tag: String) : Param.Composite(tag) {
    val size = using(DoubleParam("s", 40.0, 10.0, 100.0, 0.1))
}
