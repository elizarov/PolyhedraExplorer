package polyhedra.js.poly

import polyhedra.js.*
import polyhedra.js.params.*

class PolyParams(tag: String) : Param.Composite(tag) {
    val view = using(ViewParams("v"))
    val animation = using(AnimationParams("a"))
    val lighting = using(LightingParams("l"))
}

class ViewParams(tag: String) : Param.Composite(tag) {
    val scale = using(DoubleParam("s", 0.0, -2.0, 2.0, 0.01))
    val expand = using(DoubleParam("e", 0.0, 0.0, 2.0, 0.01))
    val transparent = using(DoubleParam("t", 0.0, 0.0, 1.0, 0.01))
    val display = using(EnumParam("d", Display.Full, Displays))
}

class AnimationParams(tag: String) : Param.Composite(tag) {
    val rotate = using(BooleanParam("r", true))
    val rotationAngle = using(DoubleParam("ra", 60.0, 0.0, 360.0, 1.0))
}

class LightingParams(tag: String) : Param.Composite(tag) {
    val ambientLight = using(DoubleParam("a", 0.25, 0.0, 1.0, 0.01))
    val pointLight = using(DoubleParam("p", 1.0, 0.0, 1.0, 0.01))
    val specularLight = using(DoubleParam("s", 1.0, 0.0, 1.0, 0.01))
    val specularPower = using(DoubleParam("sp", 30.0, 0.0, 100.0, 0.1))
}

