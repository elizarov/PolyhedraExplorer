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
        if (animationDuration != null && poly != prevPoly) when {
            // no animation on seed changes
            curSeed != prevSeed -> {
                // todo: animate via left/right fly in/out
                updateAnimation(null)
            }
            // polyhedron is the same topologically but with a different geometry -- animate smooth transition
            poly.hasSameTopology(prevPoly) -> {
                updateAnimation(
                    TransformAnimation(
                        this,
                        animationDuration,
                        TransformKeyframe(prevPoly, 0.0),
                        TransformKeyframe(poly, 1.0)
                    )
                )
            }
            // otherwise try to animate update of the last applied transform
            validTransforms != prevValidTransforms -> {
                var commonSize = 0
                while (commonSize < validTransforms.size && commonSize < prevValidTransforms.size &&
                    validTransforms[commonSize] == prevValidTransforms[commonSize]
                ) {
                    commonSize++
                }
                if (validTransforms.size <= commonSize + 1 && prevValidTransforms.size <= commonSize + 1) {
                    val prefix = curTransforms.subList(0, commonSize)
                    val basePoly = if (prefix.isEmpty()) curSeed.poly else curPolys[prefix.size - 1]
                    val prevTransform = prevValidTransforms.getOrNull(commonSize) ?: Transform.None
                    val curTransform = validTransforms.getOrNull(commonSize) ?: Transform.None
                    updateAnimation(
                        transformUpdateAnimation(
                            this,
                            basePoly,
                            curScale,
                            prevTransform,
                            curTransform,
                            animationDuration
                        )
                    )
                } else {
                    updateAnimation(null)
                }
            }
        } else {
            updateAnimation(null)
        }
        // save to optimize future updates
        prevSeed = curSeed
        prevTransforms = curTransforms
        prevPolys = curPolys
        prevScale = curScale
        prevPoly = poly
        prevValidTransforms = validTransforms
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
                if (newPoly.fs.any { !it.isPlanar }) {
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
    poly: Polyhedron,
    scale: Scale,
    prevTransform: Transform,
    curTransform: Transform,
    animationDuration: Double
): TransformAnimation? {
    val prevTruncationRatio = prevTransform.truncationRatio(poly)
    val curTruncationRatio = curTransform.truncationRatio(poly)
    val prevCantellationRatio = prevTransform.cantellationRatio(poly)
    val curCantellationRatio = curTransform.cantellationRatio(poly)
    val prevBevellingRatio = prevTransform.bevellingRatio(poly)
    val curBevellingRatio = curTransform.bevellingRatio(poly)
    val prevSnubbingRatio = prevTransform.snubbingRatio(poly)
    val curSnubbingRatio = curTransform.snubbingRatio(poly)
    val prevChamferingRatio = prevTransform.chamferingRatio(poly)
    val curChamferingRatio = curTransform.chamferingRatio(poly)
    return when {
        // Truncation animation
        prevTruncationRatio != null && curTruncationRatio != null -> {
            val prevF = prevFractionGap(prevTruncationRatio)
            val curF = curFractionGap(curTruncationRatio)
            val prevR = prevF.interpolate(prevTruncationRatio, curTruncationRatio)
            val curR = curF.interpolate(prevTruncationRatio, curTruncationRatio)
            TransformAnimation(
                params,
                animationDuration,
                TransformKeyframe(poly.truncated(prevR).scaled(scale), prevF),
                TransformKeyframe(poly.truncated(curR).scaled(scale), curF)
            )
        }
        // Cantellation animation
        prevCantellationRatio != null && curCantellationRatio != null -> {
            val prevF = prevFractionGap(prevCantellationRatio)
            val curF = curFractionGap(curCantellationRatio)
            val prevR = prevF.interpolate(prevCantellationRatio, curCantellationRatio)
            val curR = curF.interpolate(prevCantellationRatio, curCantellationRatio)
            TransformAnimation(
                params,
                animationDuration,
                TransformKeyframe(poly.cantellated(prevR).scaled(scale), prevF, prevCantellationRatio == 1.0),
                TransformKeyframe(poly.cantellated(curR).scaled(scale), curF, curCantellationRatio == 1.0)
            )
        }
        // Bevelling animation
        prevBevellingRatio != null && curBevellingRatio != null -> {
            val prevF = prevFractionGap(prevBevellingRatio)
            val curF = curFractionGap(curBevellingRatio)
            val prevR = prevF.interpolate(prevBevellingRatio, curBevellingRatio)
            val curR = curF.interpolate(prevBevellingRatio, curBevellingRatio)
            TransformAnimation(
                params,
                animationDuration,
                TransformKeyframe(poly.bevelled(prevR).scaled(scale), prevF, prevBevellingRatio.cr == 1.0),
                TransformKeyframe(poly.bevelled(curR).scaled(scale), curF, curBevellingRatio.cr == 1.0)
            )
        }
        // Snubbing animation
        prevSnubbingRatio != null && curSnubbingRatio != null -> {
            val prevF = prevFractionGap(prevSnubbingRatio)
            val curF = curFractionGap(curSnubbingRatio)
            val prevR = prevF.interpolate(prevSnubbingRatio, curSnubbingRatio)
            val curR = curF.interpolate(prevSnubbingRatio, curSnubbingRatio)
            TransformAnimation(
                params,
                animationDuration,
                TransformKeyframe(poly.snub(prevR).scaled(scale), prevF, prevSnubbingRatio.cr == 1.0),
                TransformKeyframe(poly.snub(curR).scaled(scale), curF, curSnubbingRatio.cr == 1.0)
            )
        }
        // Chamfering animation
        prevChamferingRatio != null && curChamferingRatio != null -> {
            val prevF = prevFractionGap(prevChamferingRatio)
            val curF = curFractionGap(curChamferingRatio)
            val prevR = prevF.interpolate(prevChamferingRatio, curChamferingRatio)
            val curR = curF.interpolate(prevChamferingRatio, curChamferingRatio)
            TransformAnimation(
                params,
                animationDuration,
                TransformKeyframe(poly.chamfered(prevR).scaled(scale), prevF),
                TransformKeyframe(poly.chamfered(curR).scaled(scale), curF)
            )
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

