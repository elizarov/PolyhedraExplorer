package polyhedra.js.params

import polyhedra.common.util.*

abstract class Animation {
    abstract val isOver: Boolean
    abstract fun update(dt: Double)
}

abstract class ValueUpdateAnimation<T : Any, P : AnimatedValueParam<T, P>>(
    protected val param: P,
    private val duration: Double
) : Animation() {
    abstract val animatedValue: T

    private var position = 0.0

    val fraction: Double
        get() = if (duration <= 0.0) 1.0 else (position / duration).coerceIn(0.0, 1.0)

    override val isOver: Boolean
        get() = position >= duration

    override fun update(dt: Double) {
        position += dt
        if (isOver) param.resetValueUpdateAnimation()
        param.notifyAnimationUpdate() // :todo: move efficient impl for multiple animations
    }
}

class DoubleUpdateAnimation(
    param: DoubleParam,
    duration: Double,
    private val oldValue: Double
) : ValueUpdateAnimation<Double, DoubleParam>(param, duration) {
    override val animatedValue: Double get() {
        val f = fraction
        return oldValue * (1 - f) + param.value * f
    }
}

class RotationUpdateAnimation(
    param: RotationParam,
    duration: Double,
    private val oldValue: Quat
) : ValueUpdateAnimation<Quat, RotationParam>(param, duration) {
    override val animatedValue: Quat get() {
        val f = fraction
        return (oldValue * (1 - f) + param.value * f).unit
    }
}

class RotationAnimation(
    private val param: RotationParam,
    private val animation: RotationAnimationParams
) : Animation() {
    override var isOver: Boolean = false

    override fun update(dt: Double) {
        if (isOver) return
        param.rotate((dt * animation.animatedRotationAngles).anglesToQuat(), Param.UpdateType.ValueAnimation)
    }
}