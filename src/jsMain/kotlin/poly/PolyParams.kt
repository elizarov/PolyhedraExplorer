/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import kotlinx.coroutines.*
import polyhedra.common.*
import polyhedra.common.poly.*
import polyhedra.common.transform.*
import polyhedra.common.util.*
import polyhedra.js.*
import polyhedra.js.main.*
import polyhedra.js.params.*
import polyhedra.js.worker.*
import kotlin.js.*
import kotlin.math.*

private const val MAX_DISPLAY_EDGES = (1 shl 15) - 1

private const val DEBUG_ANIMATION = false

class RenderParams(tag: String, val animationParams: ViewAnimationParams?) : Param.Composite(tag) {
    val poly = using(PolyParams("", animationParams))
    val view = using(ViewParams("v", animationParams))
    val lighting = using(LightingParams("l", animationParams))
}

private val defaultSeed = Seed.Tetrahedron
private val defaultScale = Scale.Circumradius

class PolyParams(tag: String, val animationParams: ViewAnimationParams?) : Param.Composite(tag) {
    val seed = using(EnumParam("s", defaultSeed, Seeds))
    val transforms = using(EnumListParam("t", emptyList(), Transforms))
    val baseScale = using(EnumParam("bs", defaultScale, Scales))
    val hideFaces = using(SetParam("hf", emptySet()) { it.toFaceKindOrNull() })
    val selectedFace = using(TransientParam<FaceKind?>(null))

    // computed value of the currently shown polyhedron
    var poly: Polyhedron = defaultSeed.poly
        private set
    var polyName: String = ""
        private set
    var transformWarnings: List<IndicatorMessage<*>?> = emptyList()
        private set
    var transformError: TransformError? = null
        private set

    var transformProgress: Int = 0
        private set

    // polyhedra transformation animation
    var transformAnimation: TransformAnimation? = null
        private set

    // for geometry extraction
    val targetPoly: Polyhedron
        get() = transformAnimation?.targetPoly ?: poly

    // previous state stored to compute animated transformations
    private var prevSeed: Seed = defaultSeed
    private var prevTransforms: List<Transform> = emptyList()
    private var prevPolys: List<Polyhedron> = emptyList() // after each transform
    private var prevScale: Scale = defaultScale
    private var prevPoly: Polyhedron = defaultSeed.poly.scaled(defaultScale)
    private var prevValidTransforms: List<Transform> = emptyList()

    // ongoing asynchronous transformation
    private var asyncTransform: AsyncTransform? = null

    private val progress = OperationProgressContext { done ->
        transformProgress = done
        notifyUpdated(Progress)
    }

    override fun update(update: UpdateType, dt: Double) {
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
        val curSeed = seed.value
        val curTransforms = transforms.value
        val curPolys = ArrayList<Polyhedron>()
        val curScale = baseScale.value
        if (prevSeed == curSeed && prevTransforms == curTransforms && prevScale == curScale && asyncTransform == null) {
            return // nothing to do
        }
        val validTransforms = recomputeTransforms(curSeed, curTransforms, curScale, curPolys)
        // compute transformation animation
        val animationDuration = animationParams?.animateValueUpdatesDuration
        // only animate when animation is turned on and when polyhedron had changed
        val animation: TransformAnimation? = if (animationDuration != null && poly != prevPoly) {
            // compute common size between prev/new transforms
            val commonSize = computeCommonSize(prevValidTransforms, validTransforms)
            when {
                // no animation on seed changes
                curSeed != prevSeed -> {
                    // todo: animate via left/right fly in/out
                    null
                }
                // animate update of the last applied transform if there is one transform difference
                validTransforms != prevValidTransforms && validTransforms.size <= commonSize + 1 && prevValidTransforms.size <= commonSize + 1 -> {
                    val basePoly = if (commonSize == 0) curSeed.poly else curPolys[commonSize - 1]
                    val prevPoly = prevPolys.getOrNull(commonSize) ?: basePoly
                    val curPoly = curPolys.getOrNull(commonSize) ?: basePoly
                    val prevTransform = prevValidTransforms.getOrNull(commonSize)
                        ?.takeIf { !it.isIdentityTransform(basePoly) } ?: Transform.None
                    val curTransform = validTransforms.getOrNull(commonSize)
                        ?.takeIf { !it.isIdentityTransform(basePoly) } ?: Transform.None
                    transformUpdateAnimation(
                        this, basePoly, curScale,
                        prevPoly, curPoly,
                        prevTransform, curTransform,
                        animationDuration
                    )
                }
                // polyhedron is the same topologically but with a different geometry -- animate smooth transition
                poly.hasSameTopology(prevPoly) ->
                    TransformAnimationStep(
                        animationDuration,
                        TransformKeyframe(prevPoly, 0.0),
                        TransformKeyframe(poly, 1.0)
                    )
                else -> null
            }
        } else null
        updateAnimation(animation)
        // save to optimize future updates
        prevSeed = curSeed
        prevTransforms = curTransforms
        prevPolys = curPolys
        prevScale = curScale
        prevPoly = poly
        prevValidTransforms = validTransforms
    }

    private fun computeCommonSize(prevValidTransforms: List<Transform>, validTransforms: List<Transform>): Int {
        var commonSize = 0
        while (commonSize < validTransforms.size && commonSize < prevValidTransforms.size &&
            validTransforms[commonSize] == prevValidTransforms[commonSize]
        ) {
            commonSize++
        }
        return commonSize
    }

    // updates poly, polyName, transformError
    // returns valid transforms
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun recomputeTransforms(
        curSeed: Seed,
        curTransforms: List<Transform>,
        curScale: Scale,
        curPolys: ArrayList<Polyhedron>
    ): List<Transform> {
        var curPoly = curSeed.poly
        var curPolyName = curSeed.toString()
        var curIndex = 0
        var equalsToPrev = curSeed == prevSeed
        val curTransformWarnings = ArrayList<IndicatorMessage<*>?>()
        transformError = null // will set if fail in process
        loop@ for (transform in curTransforms) {
            try {
                if (!transform.isApplicable(curPoly)) {
                    transformError = TransformError(curIndex, TransformNotApplicable(transform))
                    break
                }
                // compute FEV before doing an actual transform
                val fev = transform.fev * curPoly.fev()
                if (fev.e > MAX_DISPLAY_EDGES) {
                    transformError = TransformError(curIndex, TooLarge(fev))
                    break@loop
                }
                // Reuse previously transformed polyhedron if possible
                val prevPoly = prevPolys.getOrNull(curIndex)
                var curWarning: IndicatorMessage<*>? = null
                val newPoly = when {
                    equalsToPrev && prevTransforms.getOrNull(curIndex) == transform && prevPoly != null -> {
                        // reuse previous transform result is available and transform is the same
                        prevPoly
                    }
                    transform.isIdentityTransform(curPoly) -> {
                        // keep poly if this transform is an identity transform for this polyhedron
                        curWarning = TransformIsId(transform)
                        curPoly
                    }
                    else -> {
                        // transform from scratch
                        equalsToPrev = false
                        // check if requested transformation is slow
                        val atx = transform.asyncTransform
                        if (atx != null) {
                            val at = asyncTransform
                            val cached = TransformCache[curPoly, transform]
                            when {
                                // see if this transform is already running
                                at?.poly == curPoly && at.transform == transform -> {
                                    if (at.job.isCompleted) {
                                        // it is already done -- use the result and store it to cache
                                        asyncTransform = null
                                        val result = runCatching { at.job.getCompleted() }
                                        TransformCache[curPoly, transform] = result
                                        result.getOrThrow()
                                    } else {
                                        // it is still running
                                        transformError = TransformError(curIndex, isAsync = true)
                                        break@loop // continue to wait
                                    }
                                }
                                // see if this transform was cached
                                cached != null -> cached
                                else -> {
                                    // perform transformation asynchronously
                                    startAsyncTransform(curIndex, curPoly, transform)
                                    break@loop // skip further transforms while transform is running
                                }
                            }
                        } else {
                            // transformation is fast -- just do it immediately
                            curPoly.transformed(transform)
                        }
                    }
                }
                if (newPoly.fs.any { !it.isPlanar } && curIndex == curTransforms.lastIndex) {
                    curWarning = SomeFacesNotPlanar()
                }
                curPolyName = "$transform $curPolyName"
                curPoly = newPoly
                curPolys.add(newPoly)
                curTransformWarnings.add(curWarning)
                curIndex++
            } catch (e: Exception) {
                transformError = TransformError(curIndex, TransformFailed(transform))
                e.printStackTrace() // print exception onto console
                break@loop
            }
        }
        // If we are not waiting for an async transform anymore, then cancel any ongoing async operation (if any)
        asyncTransform?.let { at ->
            if (transformError?.isAsync != true) {
                at.job.cancel()
                asyncTransform = null
            }
        }
        poly = curPoly.scaled(curScale)
        polyName = curPolyName
        transformWarnings = curTransformWarnings
        return curTransforms.subList(0, curIndex)
    }

    private fun startAsyncTransform(
        curIndex: Int,
        curPoly: Polyhedron,
        transform: Transform
    ) {
        asyncTransform?.job?.cancel() // cancel the previous one (if any)
        val job = GlobalScope.async {
            performWorkerTask(TransformTask(curPoly, transform), progress)
        }
        job.invokeOnCompletion {
            notifyUpdated(TargetValue)
        }
        asyncTransform = AsyncTransform(curPoly, transform, job)
        transformProgress = 0
        transformError = TransformError(curIndex, isAsync = true)
    }

    fun updateAnimation(transformAnimation: TransformAnimation?) {
        if (this.transformAnimation == transformAnimation) return
        this.transformAnimation = transformAnimation
        if (transformAnimation != null) notifyUpdated(ActiveAnimation)
    }
}

private class AsyncTransform(
    val poly: Polyhedron,
    val transform: Transform,
    val job: Deferred<Polyhedron>
)

private fun transformUpdateAnimation(
    params: PolyParams,
    basePoly: Polyhedron,
    scale: Scale,
    prevPoly: Polyhedron,
    curPoly: Polyhedron,
    prevTransform: Transform,
    curTransform: Transform,
    animationDuration: Double
): TransformAnimation? {
    // no transforms -- no animation
    if (prevTransform == Transform.None && curTransform == Transform.None) return null
    // same topology animation is trivial
    if (curPoly.hasSameTopology(prevPoly)) {
        return TransformAnimationStep(
            animationDuration,
            TransformKeyframe(prevPoly.scaled(scale), 0.0),
            TransformKeyframe(curPoly.scaled(scale), 1.0)
        )
    }
    // figure out the type of transform to do
    val prevTruncationRatio = prevTransform.truncationRatio(basePoly)
    val curTruncationRatio = curTransform.truncationRatio(basePoly)
    val prevCantellationRatio = prevTransform.cantellationRatio(basePoly)
    val curCantellationRatio = curTransform.cantellationRatio(basePoly)
    val prevBevellingRatio = prevTransform.bevellingRatio(basePoly)
    val curBevellingRatio = curTransform.bevellingRatio(basePoly)
    val prevSnubbingRatio = prevTransform.snubbingRatio(basePoly)
    val curSnubbingRatio = curTransform.snubbingRatio(basePoly)
    val prevChamferingRatio = prevTransform.chamferingRatio(basePoly)
    val curChamferingRatio = curTransform.chamferingRatio(basePoly)
    return when {
        // Truncation animation
        prevTruncationRatio != null && curTruncationRatio != null -> {
            val prevF = prevFractionGap(prevTruncationRatio)
            val curF = curFractionGap(curTruncationRatio)
            val prevR = prevF.interpolate(prevTruncationRatio, curTruncationRatio)
            val curR = curF.interpolate(prevTruncationRatio, curTruncationRatio)
            TransformAnimationStep(
                animationDuration,
                TransformKeyframe(basePoly.truncated(prevR, scale, prevPoly.faceKindSources), prevF),
                TransformKeyframe(basePoly.truncated(curR, scale, curPoly.faceKindSources), curF)
            ).also {
                if (DEBUG_ANIMATION) {
                    println("Truncation animation: tr=${prevTruncationRatio.fmt} -> tr=${curTruncationRatio.fmt}")
                    println(it)
                }
            }
        }
        // Cantellation animation
        prevCantellationRatio != null && curCantellationRatio != null -> {
            val prevF = prevFractionGap(prevCantellationRatio)
            val curF = curFractionGap(curCantellationRatio)
            val prevR = prevF.interpolate(prevCantellationRatio, curCantellationRatio)
            val curR = curF.interpolate(prevCantellationRatio, curCantellationRatio)
            TransformAnimationStep(
                animationDuration,
                TransformKeyframe(basePoly.cantellated(prevR, scale, prevPoly.faceKindSources), prevF),
                TransformKeyframe(basePoly.cantellated(curR, scale, curPoly.faceKindSources), curF)
            ).also {
                if (DEBUG_ANIMATION) {
                    println("Cantellation animation: cr=${prevCantellationRatio.fmt} -> cr=${curCantellationRatio.fmt}")
                    println(it)
                }
            }
        }
        // Bevelling animation
        prevBevellingRatio != null && curBevellingRatio != null -> {
            val prevF = prevFractionGap(prevBevellingRatio)
            val curF = curFractionGap(curBevellingRatio)
            val prevR = prevF.interpolate(prevBevellingRatio, curBevellingRatio)
            val curR = curF.interpolate(prevBevellingRatio, curBevellingRatio)
            TransformAnimationStep(
                animationDuration,
                TransformKeyframe(basePoly.bevelled(prevR, scale, prevPoly.faceKindSources), prevF),
                TransformKeyframe(basePoly.bevelled(curR, scale, curPoly.faceKindSources), curF)
            ).also {
                if (DEBUG_ANIMATION) {
                    println("Bevelling animation: $prevBevellingRatio -> $curBevellingRatio")
                    println(it)
                }
            }
        }
        // Snubbing animation
        prevSnubbingRatio != null && curSnubbingRatio != null -> {
            val prevF = prevFractionGap(prevSnubbingRatio)
            val curF = curFractionGap(curSnubbingRatio)
            val prevR = prevF.interpolate(prevSnubbingRatio, curSnubbingRatio)
            val curR = curF.interpolate(prevSnubbingRatio, curSnubbingRatio)
            TransformAnimationStep(
                animationDuration,
                TransformKeyframe(basePoly.snub(prevR, scale, prevPoly.faceKindSources), prevF),
                TransformKeyframe(basePoly.snub(curR, scale, curPoly.faceKindSources), curF)
            ).also {
                if (DEBUG_ANIMATION) {
                    println("Snubbing animation: $prevSnubbingRatio -> $curSnubbingRatio")
                    println(it)
                }
            }
        }
        // Chamfering animation
        prevChamferingRatio != null && curChamferingRatio != null -> {
            val prevF = prevFractionGap(prevChamferingRatio)
            val curF = curFractionGap(curChamferingRatio)
            val prevR = prevF.interpolate(prevChamferingRatio, curChamferingRatio)
            val curR = curF.interpolate(prevChamferingRatio, curChamferingRatio)
            TransformAnimationStep(
                animationDuration,
                TransformKeyframe(basePoly.chamfered(prevR, scale, prevPoly.faceKindSources), prevF),
                TransformKeyframe(basePoly.chamfered(curR, scale, curPoly.faceKindSources), curF)
            ).also {
                if (DEBUG_ANIMATION) {
                    println("Chamfering animation: vr=${prevChamferingRatio.fmt} -> vr=${curChamferingRatio.fmt}")
                    println(it)
                }
            }
        }
        prevTransform != Transform.None && curTransform != Transform.None -> {
            // two-step animation
            val step1 = transformUpdateAnimation(
                params, basePoly, scale,
                prevPoly, basePoly,
                prevTransform, Transform.None,
                animationDuration / 2
            )
            val step2 = transformUpdateAnimation(
                params, basePoly, scale,
                basePoly, curPoly,
                Transform.None, curTransform,
                animationDuration / 2
            )
            if (step1 != null && step2 != null) {
                TransformAnimationList(step1, step2)
            } else null
        }
        else -> null
    }
}

private fun BevellingRatio.coerceIn(range: ClosedFloatingPointRange<Double>): BevellingRatio =
    BevellingRatio(cr.coerceIn(range), tr.coerceIn(range))

data class TransformError(
    val index: Int,
    val msg: IndicatorMessage<*>? = null,
    val isAsync: Boolean = false
)

// Optionally passed from the outside (not needed in the backend)
class ViewAnimationParams(tag: String) : Param.Composite(tag), ValueAnimationParams, RotationAnimationParams {
    val animateValueUpdates = using(BooleanParam("u", true))
    val animationDuration = using(DoubleParam("d", 0.5, 0.0, 2.0, 0.1))

    override val animatedRotation: BooleanParam = using(BooleanParam("r", true))
    val rotationSpeed = using(DoubleParam("rs", 1.0, 0.0, 2.0, 0.01))
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
    val display = using(EnumParam("d", Display.All, Displays))
}

class LightingParams(tag: String, animationParams: ViewAnimationParams?) : Param.Composite(tag) {
    val ambientLight = using(DoubleParam("a", 0.25, 0.0, 1.0, 0.01, animationParams))
    val diffuseLight = using(DoubleParam("d", 1.0, 0.0, 1.0, 0.01, animationParams))
    val specularLight = using(DoubleParam("s", 1.0, 0.0, 1.0, 0.01, animationParams))
    val specularPower = using(DoubleParam("sp", 30.0, 0.0, 100.0, 1.0, animationParams))
}

