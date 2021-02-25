package polyhedra.js.params

import polyhedra.common.util.*

open class AnimationParams(tag: String) : Param.Composite(tag) {
    val animateUpdates = using(BooleanParam("u", true))
    val animationDuration = using(DoubleParam("d", 0.5, 0.0, 2.0, 0.1))
}

abstract class Animation(
    val duration: Double
) {
    abstract val param: Param

    var position = 0.0

    val fraction: Double
        get() = if (duration <= 0.0) 1.0 else (position / duration).coerceIn(0.0, 1.0)

    val isOver: Boolean
        get() = position >= duration

    open fun update(dt: Double) {
        position += dt
    }
}

abstract class ValueAnimation<T : Any, P : AnimatedValueParam<T, P>>(
    override val param: P,
    duration: Double
) : Animation(duration) {
    abstract val animatedValue: T

    override fun update(dt: Double) {
        super.update(dt)
        if (isOver) param.resetAnimation()
        param.notifyAnimationUpdate() // :todo: move efficient impl for multiple animations
    }
}

class DoubleAnimation(
    param: DoubleParam,
    duration: Double,
    val oldValue: Double
) : ValueAnimation<Double, DoubleParam>(param, duration) {
    override val animatedValue: Double get() {
        val f = fraction
        return oldValue * (1 - f) + param.value * f
    }
}

class RotationAnimation(
    param: RotationParam,
    duration: Double,
    val oldValue: Quat
) : ValueAnimation<Quat, RotationParam>(param, duration) {
    override val animatedValue: Quat get() {
        val f = fraction
        return (oldValue * (1 - f) + param.value * f).unit
    }
}