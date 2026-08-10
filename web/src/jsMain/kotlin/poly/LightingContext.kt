/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import polyhedra.web.glsl.*
import polyhedra.web.params.*
import kotlin.math.pow

class LightingContext(params: LightingParams) : Param.Context(params) {
    val keyLightIntensity by { params.keyLight.value }
    val fillLightIntensity by { params.fillLight.value }
    val roughness by { params.roughness.value }
    val fresnelF0 by { dielectricF0(params.ior.value) }

    // A neutral-warm key and subtle cool environment produce readable studio lighting without IBL.
    val lightColor = float32Of(1.0, 0.97, 0.92)
    val fillColor = float32Of(0.72, 0.80, 0.95)
    val lightPosition = float32Of(-2.4, 3.2, 4.3)

    init { setup() }

    override fun update() = Unit
}

/** Fresnel reflectance at normal incidence for an air/dielectric boundary. */
internal fun dielectricF0(ior: Double): Double = ((ior - 1.0) / (ior + 1.0)).pow(2)

internal fun schlickFresnel(cosTheta: Double, ior: Double): Double {
    val f0 = dielectricF0(ior)
    return f0 + (1.0 - f0) * (1.0 - cosTheta.coerceIn(0.0, 1.0)).pow(5)
}
