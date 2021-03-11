/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.common.*
import polyhedra.common.transform.*
import polyhedra.common.util.*
import polyhedra.js.*
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

class PolyParams(tag: String, val animationParams: ViewAnimationParams?) : Param.Composite(tag, UpdateType.TargetValue) {
    val seed = using(EnumParam("s", defaultSeed, Seeds))
    val transforms = using(EnumListParam("t", emptyList(), Transforms))
    val baseScale = using(EnumParam("bs", defaultScale, Scales))

    // computed value of the currently shown polyhedron
    var poly: Polyhedron = defaultSeed.poly
        private set
    var polyName: String = ""
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
    private var prevValidTransforms: List<Transform> = emptyList()

    // last asynchronous transformation
    private var asyncTransform: AsyncTransform? = null

    private val progress = object : OperationProgressContext {
        override val isActive: Boolean
            get() = true // todo: cancellation support
        override fun reportProgress(done: Int) {
            transformProgress = done
            notifyUpdate(UpdateType.Value)
        }
    }

    override fun update() {
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
        if (animationDuration != null) when {
            curSeed != prevSeed -> updateAnimation(null)
            validTransforms != prevValidTransforms -> {
                var commonSize = 0
                while (commonSize < validTransforms.size && commonSize < prevValidTransforms.size &&
                        validTransforms[commonSize] == prevValidTransforms[commonSize]) {
                    commonSize++
                }
                if (validTransforms.size <= commonSize + 1 && prevValidTransforms.size <= commonSize + 1) {
                    val prefix = curTransforms.subList(0, commonSize)
                    val basePoly = if (prefix.isEmpty()) curSeed.poly else curPolys[prefix.size - 1]
                    val prevTransform = prevValidTransforms.getOrNull(commonSize) ?: Transform.None
                    val curTransform = validTransforms.getOrNull(commonSize) ?: Transform.None
                    updateAnimation(transformUpdateAnimation(this, basePoly, curScale, prevTransform, curTransform, animationDuration))
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
        prevValidTransforms = validTransforms
    }

    // updates poly, polyName, transformError
    // returns valid transforms
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
        transformError = null // will set if fail in process
        try {
            loop@ for (transform in curTransforms) {
                val applicable = transform.isApplicable(curPoly)
                if (applicable != null) {
                    transformError = TransformError(curIndex, applicable)
                    break
                }
                // compute FEV before doing an actual transform
                val fev = transform.fev * curPoly.fev()
                if (fev.e > MAX_DISPLAY_EDGES) {
                    transformError = TransformError(curIndex, "Polyhedron is too large to display ($fev)")
                    break@loop
                }
                // Reuse previously transformed polyhedron if possible
                val prevPoly = prevPolys.getOrNull(curIndex)
                val newPoly = if (equalsToPrev && prevTransforms.getOrNull(curIndex) == transform && prevPoly != null) {
                    prevPoly
                } else {
                    // transform from scratch
                    equalsToPrev = false
                    // check if requested transformation is slow
                    val twp = transform.transformWithProgress
                    if (twp != null) {
                        val at = asyncTransform
                        val cached = TransformCache[curPoly, transform]
                        when {
                            // see if this transform is already running
                            at?.poly == curPoly && at.transform == transform -> {
                                val result = at.result
                                val exception = at.exception
                                when {
                                    // it is already done -- use the result
                                    result != null -> {
                                        asyncTransform = null
                                        TransformCache[curPoly, transform] = Result.success(result)
                                        result
                                    }
                                    // it is done with error (transform failed)
                                    exception != null -> {
                                        TransformCache[curPoly, transform] = Result.failure(exception)
                                        throw Exception(exception)
                                    }
                                    // it is still running
                                    else -> {
                                        transformError = TransformError(curIndex, isAsync = true)
                                        break@loop // continue to wait
                                    }
                                }
                            }
                            // see if this transform is cached
                            cached != null -> cached
                            else -> {
                                // perform transformation asynchronously
                                startAsyncTransform(curIndex, curPoly, transform)
                                break@loop // skip further transforms while transform is running
                            }
                        }
                    } else {
                        // transformation is fast -- just do it immediately
                        curPoly.transformed(transform).also {
                            it.validateGeometry()
                        }
                    }
                }
                curPolyName = "$transform $curPolyName"
                curPoly = newPoly
                curPolys.add(newPoly)
                curIndex++
            }
        } catch (e: Exception) {
            transformError = TransformError(curIndex, "Transform produces invalid polyhedron geometry")
            e.printStackTrace() // print exception onto console
        }
        poly = curPoly.scaled(curScale)
        polyName = curPolyName
        return curTransforms.subList(0, curIndex)
    }

    private fun startAsyncTransform(
        curIndex: Int,
        curPoly: Polyhedron,
        transform: Transform
    ) {
        val promise = sendWorkerTask(TransformTask(curPoly, transform), progress)
        val async = AsyncTransform(curPoly, transform)
        transformProgress = 0
        transformError = TransformError(curIndex, isAsync = true)
        asyncTransform = async
        promise.then { result ->
            async.result = result
            asyncTransformDone()
        }
        promise.catch { exception ->
            async.exception = exception
            asyncTransformDone()
        }
    }

    private fun asyncTransformDone() {
        update()
        notifyUpdate(UpdateType.Value)
    }

    private fun updateAnimation(transformAnimation: TransformAnimation?) {
        if (this.transformAnimation == transformAnimation) return
        this.transformAnimation = transformAnimation
        notifyUpdate(UpdateType.AnimationsList)
    }

    fun resetTransformAnimation() {
        updateAnimation(null)
    }

    override fun visitActiveAnimations(visitor: (Animation) -> Unit) {
        super.visitActiveAnimations(visitor)
        transformAnimation?.let { visitor(it) }
    }
}

private class AsyncTransform(
    val poly: Polyhedron,
    val transform: Transform,
) {
    var result: Polyhedron? = null
    var exception: Throwable? = null
}

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

data class TransformError (
    val index: Int,
    val message: String = "",
    val isAsync: Boolean = false
)

// Optionally passed from the outside (not needed in the backend)
class ViewAnimationParams(tag: String) : Param.Composite(tag), ValueAnimationParams, RotationAnimationParams  {
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

