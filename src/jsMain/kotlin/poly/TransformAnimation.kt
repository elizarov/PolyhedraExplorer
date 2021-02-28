package polyhedra.js.poly

import polyhedra.common.*
import polyhedra.js.params.*

class TransformAnimation(
    override val param: PolyParams,
    private val duration: Double,
    val prev: TransformKeyframe,
    val target: TransformKeyframe
) : Animation() {
    init { require(duration > 0) }

    private var position = 0.0

    override val isOver: Boolean
        get() = position >= duration

    override fun update(dt: Double) {
        position += dt
        if (isOver) param.resetTransformAnimation()
    }

    private val desiredRatio: Double get() {
        val f = (position / duration).coerceIn(0.0, 1.0)
        return (1 - f) * prev.desiredRatio + f * target.desiredRatio
    }

    val prevPoly = prev.poly
    val prevFraction: Double
        get() = (desiredRatio - target.polyRatio) / (prev.polyRatio - target.polyRatio)

    val targetPoly: Polyhedron = target.poly
    val targetFraction: Double
        get() = (desiredRatio - prev.polyRatio) / (target.polyRatio - prev.polyRatio)
}

data class TransformKeyframe(
    val poly: Polyhedron,
    val polyRatio: Double,
    val desiredRatio: Double,
    val dual: Boolean = false
)