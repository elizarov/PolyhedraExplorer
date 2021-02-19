package polyhedra.js.poly

import polyhedra.common.*
import polyhedra.js.util.*

class Lightning {
    val ambientLightColor = float32Of(0.3, 0.3, 0.3)
    val directionalLightColor = float32Of(1.0, 1.0, 1.0)
    val directionalLightVector = Vec3(0.85, 0.8, 0.75).unit.toFloat32Array()
}