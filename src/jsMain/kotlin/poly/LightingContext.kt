/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.js.glsl.*
import polyhedra.js.params.*

class LightningContext(params: LightingParams) : Param.Context(params) {
    private val ambientLight by { params.ambientLight.value }
    private val diffuseLight by { params.diffuseLight.value }
    private val specularLight by { params.specularLight.value }

    val specularLightPower by { params.specularPower.value }

    val ambientLightColor = float32Of(0.3, 0.3, 0.3)
    val diffuseLightColor = float32Of(1.0, 1.0, 1.0)
    val specularLightColor = float32Of(1.0, 1.0, 1.0)
    val lightPosition = float32Of(1.0, 1.0, 4.0)
    
    init { setup() }

    override fun update() {
        ambientLightColor.fill(ambientLight)
        diffuseLightColor.fill(diffuseLight)
        specularLightColor.fill(specularLight)
    }
}