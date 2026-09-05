/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import org.khronos.webgl.*
import polyhedra.web.glsl.*
import polyhedra.web.params.*
import polyhedra.web.util.*
import kotlin.math.*

class ViewContext(params: ViewParams) : Param.Context(params) {
    private val scale by { params.scale.value }
    private val rotate by { params.rotate.value }

    val expandFaces by { params.expandFaces.value }
    val cutEnabled by { params.cutEnabled.value }
    private val cutPosition by { params.cutPosition.value }
    val transparencyEnabled by { params.transparencyEnabled.value }
    val transparentFaces by { if (params.transparencyEnabled.value) params.transparentFaces.value else 0.0 }
    val faceWidth by { params.faceWidth.value }
    val faceRim by { params.faceRim.value }

    val cameraPosition = float32Of(0.0, 0.0, 4.0)
    val projectionMatrix = mat4.create()
    val modelMatrix = mat4.create()
    val normalMatrix = mat3.create()
    var scaleFactor = 1.0
        private set
    val cutPlanePosition: Double
        get() = cutPosition * scaleFactor

    private val cameraFieldOfViewDegrees = 45.0

    private val modelTranslation =  Float32Array(3) // model at origin
    private val modelScale = Float32Array(3)

    private val tmpQuat = quat.create()
    private val tmpVec3 = Float32Array(3)

    fun initProjection(width: Int, height: Int) {
        mat4.perspective(
            projectionMatrix, cameraFieldOfViewDegrees * PI / 180,
            width.toDouble() / height, 0.1, 30.0
        )
        for (i in 0..2) tmpVec3[i] = -cameraPosition[i]
        mat4.translate(projectionMatrix, projectionMatrix, tmpVec3)
    }

    init {
        setup()
        // URL values can be loaded before this rendering context exists. Initialize eagerly because
        // their LoadedValue notification cannot be replayed to a dependency registered afterward.
        performUpdate(null, 0.0)
    }

    override fun update() {
        scaleFactor = 2.0.pow(scale)
        modelScale.fill(scaleFactor)
        val r = rotate
        tmpQuat[0] = r.x
        tmpQuat[1] = r.y
        tmpQuat[2] = r.z
        tmpQuat[3] = r.w
        mat4.fromRotationTranslationScale(modelMatrix, tmpQuat, modelTranslation, modelScale)

        quat.conjugate(tmpQuat, tmpQuat)
        mat3.fromQuat(normalMatrix, tmpQuat)
        mat3.transpose(normalMatrix, normalMatrix)
    }
}


