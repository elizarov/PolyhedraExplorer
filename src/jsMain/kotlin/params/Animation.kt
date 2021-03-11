/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.params

import polyhedra.common.util.*

abstract class Animation {
    abstract val param: Param
    abstract val isOver: Boolean
    abstract fun update(dt: Double)
}

abstract class ValueUpdateAnimation<T : Any, P : AnimatedValueParam<T, P>>(
    override val param: P,
    private val duration: Double
) : Animation() {
    init { require(duration > 0) }

    abstract val value: T

    private var position = 0.0

    protected val fraction: Double
        get() = (position / duration).coerceIn(0.0, 1.0)

    override val isOver: Boolean
        get() = position >= duration

    override fun update(dt: Double) {
        position += dt
        if (isOver) param.resetValueUpdateAnimation()
    }
}

class DoubleUpdateAnimation(
    param: DoubleParam,
    duration: Double,
    private val oldValue: Double
) : ValueUpdateAnimation<Double, DoubleParam>(param, duration) {
    override val value: Double get() {
        val f = fraction
        return oldValue * (1 - f) + param.targetValue * f
    }
}

class RotationUpdateAnimation(
    param: RotationParam,
    duration: Double,
    private val oldValue: Quat
) : ValueUpdateAnimation<Quat, RotationParam>(param, duration) {
    override val value: Quat get() {
        val f = fraction
        return (oldValue * (1 - f) + param.targetValue * f).unit
    }
}

class RotationAnimation(
    override val param: RotationParam,
    private val animation: RotationAnimationParams
) : Animation() {
    override var isOver: Boolean = false

    override fun update(dt: Double) {
        if (isOver) return
        param.rotate((dt * animation.animatedRotationAngles).anglesToQuat(), Param.UpdateType.None)
    }
}