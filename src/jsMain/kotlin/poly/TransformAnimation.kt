/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.common.poly.*
import polyhedra.common.transform.*
import polyhedra.common.util.*

interface TransformAnimation {
    val prevPoly: Polyhedron
    val prevFraction: Double
    val targetPoly: Polyhedron
    val targetFraction: Double
    val isOver: Boolean
    fun update(dt: Double)
}

class TransformAnimationStep(
    private val duration: Double,
    private val prev: TransformKeyframe,
    private val target: TransformKeyframe,
) : TransformAnimation {
    init { require(duration > 0) }

    private var position = 0.0

    override val isOver: Boolean
        get() = position >= duration

    override fun update(dt: Double) {
        position += dt
    }

    private val fraction: Double
        get() = (position / duration).coerceIn(0.0, 1.0)

    override val prevPoly = prev.poly

    override val prevFraction: Double
        get() = (fraction - target.fraction) / (prev.fraction - target.fraction)

    override val targetPoly: Polyhedron = target.poly

    override val targetFraction: Double
        get() = (fraction - prev.fraction) / (target.fraction - prev.fraction)

    override fun toString(): String = buildString {
        append("${position.fmt}/${duration.fmt}(${target.poly}: ")
        append(prevPoly.faceKinds.entries.joinToString { (fk, fl) ->
            val f = fl[0]
            "$fk -> ${targetPoly.fs[f.id].kind} (${fl.size} faces)"
        })
        append(")")
    }
}

data class TransformKeyframe(
    val poly: Polyhedron,
    val fraction: Double
)

class TransformAnimationList(private vararg val animations: TransformAnimation) : TransformAnimation {
    private var index = 0

    private val at: TransformAnimation
        get() = animations[index]

    override val prevPoly: Polyhedron
        get() = at.prevPoly

    override val prevFraction: Double
        get() = at.prevFraction

    override val targetPoly: Polyhedron
        get() = at.targetPoly

    override val targetFraction: Double
        get() = at.targetFraction

    override val isOver: Boolean
        get() = index == animations.lastIndex && at.isOver

    override fun update(dt: Double) {
        if (isOver) return
        at.update(dt)
        if (at.isOver && index < animations.lastIndex) index++
    }

    override fun toString(): String =
        "$index/${animations.size}[${animations.joinToString()}]"
}

private const val GAP = 1e-4

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
