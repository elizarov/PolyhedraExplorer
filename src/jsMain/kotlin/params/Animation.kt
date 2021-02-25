package polyhedra.js.params

import polyhedra.common.util.*

open class AnimationParams(tag: String) : Param.Composite(tag) {
    val animateValueUpdates = using(BooleanParam("u", true))
    val animationDuration = using(DoubleParam("d", 0.5, 0.0, 2.0, 0.1))
}

abstract class Animation {
    abstract val param: Param
    abstract val isOver: Boolean
    abstract fun update(dt: Double)
}

abstract class ValueUpdateAnimation<T : Any, P : AnimatedValueParam<T, P>>(
    override val param: P,
    val duration: Double
) : Animation() {
    abstract val animatedValue: T

    var position = 0.0

    val fraction: Double
        get() = if (duration <= 0.0) 1.0 else (position / duration).coerceIn(0.0, 1.0)

    override val isOver: Boolean
        get() = position >= duration

    override fun update(dt: Double) {
        position += dt
        if (isOver) param.resetAnimation()
        param.notifyAnimationUpdate() // :todo: move efficient impl for multiple animations
    }
}

class DoubleUpdateAnimation(
    param: DoubleParam,
    duration: Double,
    val oldValue: Double
) : ValueUpdateAnimation<Double, DoubleParam>(param, duration) {
    override val animatedValue: Double get() {
        val f = fraction
        return oldValue * (1 - f) + param.value * f
    }
}

class RotationUpdateAnimation(
    param: RotationParam,
    duration: Double,
    val oldValue: Quat
) : ValueUpdateAnimation<Quat, RotationParam>(param, duration) {
    override val animatedValue: Quat get() {
        val f = fraction
        return (oldValue * (1 - f) + param.value * f).unit
    }
}