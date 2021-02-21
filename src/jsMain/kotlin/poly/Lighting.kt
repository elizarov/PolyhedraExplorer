package polyhedra.js.poly

import polyhedra.js.glsl.*

class Lightning {
    val ambientLightColor = float32Of(0.3, 0.3, 0.3)
    val pointLightColor = float32Of(1.0, 1.0, 1.0)
    val specularLightColor = float32Of(1.0, 1.0, 1.0)
    val lightPosition = float32Of(1.0, 1.0, 3.0)

    var specularLightPower = 10.0
}