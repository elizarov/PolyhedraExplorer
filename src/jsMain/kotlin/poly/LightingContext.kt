package polyhedra.js.poly

import polyhedra.js.glsl.*
import polyhedra.js.params.*

class LightningContext(override val params: LightingParams) : Param.Context() {
    val ambientLightColor = float32Of(0.3, 0.3, 0.3)
    val pointLightColor = float32Of(1.0, 1.0, 1.0)
    val specularLightColor = float32Of(1.0, 1.0, 1.0)
    val lightPosition = float32Of(1.0, 1.0, 3.0)
    var specularLightPower = 0.0
        private set

    init {
        setupAndUpdate()
    }

    override fun update() {
        ambientLightColor.fill(params.ambientLight.value)
        pointLightColor.fill(params.pointLight.value)
        specularLightColor.fill(params.specularLight.value)
        specularLightPower = params.specularPower.value
    }
}