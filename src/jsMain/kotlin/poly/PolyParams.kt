package polyhedra.js.poly

import polyhedra.js.*
import polyhedra.js.params.*

class PolyParams(tag: String) : Param.Composite(tag) {
    val animation = using(ViewAnimationParams("a"))
    val view = using(ViewParams("v", animation))
    val lighting = using(LightingParams("l", animation))
}

class ViewAnimationParams(tag: String) : AnimationParams(tag) {
    val rotate = using(BooleanParam("r", true))
    val rotationAngle = using(DoubleParam("ra", 60.0, 0.0, 360.0, 1.0))
}

class ViewParams(tag: String, animationParams: AnimationParams) : Param.Composite(tag) {
    val scale = using(DoubleParam("s", 0.0, -2.0, 2.0, 0.01, animationParams))
    val expand = using(DoubleParam("e", 0.0, 0.0, 2.0, 0.01, animationParams))
    val transparent = using(DoubleParam("t", 0.0, 0.0, 1.0, 0.01, animationParams))
    val display = using(EnumParam("d", Display.All, Displays))
}

class LightingParams(tag: String, animationParams: AnimationParams) : Param.Composite(tag) {
    val ambientLight = using(DoubleParam("a", 0.25, 0.0, 1.0, 0.01, animationParams))
    val diffuseLight = using(DoubleParam("d", 1.0, 0.0, 1.0, 0.01, animationParams))
    val specularLight = using(DoubleParam("s", 1.0, 0.0, 1.0, 0.01, animationParams))
    val specularPower = using(DoubleParam("sp", 30.0, 0.0, 100.0, 1.0, animationParams))
}

