package polyhedra.js.poly

import polyhedra.common.util.*
import polyhedra.js.*
import polyhedra.js.params.*
import kotlin.math.*

class PolyParams(tag: String) : Param.Composite(tag) {
    val animation = using(ViewAnimationParams("a"))
    val view = using(ViewParams("v", animation, animation))
    val lighting = using(LightingParams("l", animation))
}

class ViewAnimationParams(tag: String) : Param.Composite(tag), ValueAnimationParams, RotationAnimationParams  {
    val animateValueUpdates = using(BooleanParam("u", true))
    val animationDuration = using(DoubleParam("d", 0.5, 0.0, 2.0, 0.1))

    override val animatedRotation: BooleanParam = using(BooleanParam("r", true))
    val rotationSpeed = using(DoubleParam("rs", 1.0, 0.0, 2.0, 0.01))
    val rotationAngle = using(DoubleParam("ra", 60.0, 0.0, 360.0, 1.0))

    override val animateValueUpdatesDuration: Double?
        get() = animationDuration.value.takeIf { animateValueUpdates.value }

    override val animatedRotationAngles: Vec3
        get() {
            val ra = rotationAngle.value * PI / 180
            val rs = rotationSpeed.value
            return Vec3(rs * sin(ra), rs * cos(ra), 0.0)
        }
}

class ViewParams(
    tag: String,
    valueAnimationParams: ValueAnimationParams,
    rotationAnimationParams: RotationAnimationParams
) : Param.Composite(tag) {
    val rotate = using(RotationParam("r", Quat.ID, valueAnimationParams, rotationAnimationParams))
    val scale = using(DoubleParam("s", 0.0, -2.0, 2.0, 0.01, valueAnimationParams))
    val expand = using(DoubleParam("e", 0.0, 0.0, 2.0, 0.01, valueAnimationParams))
    val transparent = using(DoubleParam("t", 0.0, 0.0, 1.0, 0.01, valueAnimationParams))
    val display = using(EnumParam("d", Display.All, Displays))
}

class LightingParams(tag: String, valueAnimationParams: ValueAnimationParams) : Param.Composite(tag) {
    val ambientLight = using(DoubleParam("a", 0.25, 0.0, 1.0, 0.01, valueAnimationParams))
    val diffuseLight = using(DoubleParam("d", 1.0, 0.0, 1.0, 0.01, valueAnimationParams))
    val specularLight = using(DoubleParam("s", 1.0, 0.0, 1.0, 0.01, valueAnimationParams))
    val specularPower = using(DoubleParam("sp", 30.0, 0.0, 100.0, 1.0, valueAnimationParams))
}

