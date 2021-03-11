/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.common.*
import polyhedra.common.transform.*
import polyhedra.js.params.*

class TransformAnimation(
    override val param: PolyParams,
    private val duration: Double,
    val prev: TransformKeyframe,
    val target: TransformKeyframe,
) : Animation() {
    init { require(duration > 0) }

    private var position = 0.0

    override val isOver: Boolean
        get() = position >= duration

    override fun update(dt: Double) {
        position += dt
        if (isOver) param.resetTransformAnimation()
    }

    private val fraction: Double
        get() = (position / duration).coerceIn(0.0, 1.0)

    val prevPoly = prev.poly
    val prevFraction: Double
        get() = (fraction - target.fraction) / (prev.fraction - target.fraction)

    val targetPoly: Polyhedron = target.poly
    val targetFraction: Double
        get() = (fraction - prev.fraction) / (target.fraction - prev.fraction)
}

data class TransformKeyframe(
    val poly: Polyhedron,
    val fraction: Double,
    val dual: Boolean = false
)

private const val GAP = 0.01

fun prevFractionGap(ratio: Double): Double =
    if (ratio <= 0 || ratio >= 1) GAP else 0.0

fun curFractionGap(ratio: Double): Double =
    if (ratio <= 0 || ratio >= 1) 1 - GAP else 1.0

fun Double.interpolate(prev: Double, target: Double): Double =
    (1 - this) * prev + this * target

fun prevFractionGap(ratio: BevellingRatio): Double =
    if (ratio.cr <= 0 || ratio.cr >= 1 || ratio.tr <= 0 || ratio.tr >= 1) GAP else 0.0

fun curFractionGap(ratio: BevellingRatio): Double =
    if (ratio.cr <= 0 || ratio.cr >= 1 || ratio.tr <= 0 || ratio.tr >= 1) 1 - GAP else 1.0

fun Double.interpolate(prev: BevellingRatio, target: BevellingRatio): BevellingRatio =
    BevellingRatio(
        (1 - this) * prev.cr + this * target.cr,
        (1 - this) * prev.tr + this * target.tr
    )

fun prevFractionGap(ratio: SnubbingRatio): Double =
    if (ratio.cr <= 0 || ratio.cr >= 1) GAP else 0.0

fun curFractionGap(ratio: SnubbingRatio): Double =
    if (ratio.cr <= 0 || ratio.cr >= 1) 1 - GAP else 1.0

fun Double.interpolate(prev: SnubbingRatio, target: SnubbingRatio): SnubbingRatio =
    SnubbingRatio(
        (1 - this) * prev.cr + this * target.cr,
        (1 - this) * prev.sa + this * target.sa
    )
