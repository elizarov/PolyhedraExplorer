package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.js.glsl.*
import polyhedra.js.util.*
import kotlin.math.*

class ViewParameters {
    var rotationQuat = quat.create()
    var viewScale = 0.0
    var expand = 0.0
    var transparent = 0.0

    private val axis = Float32Array(3)
    private val deltaQuat = quat.create()

    fun rotate(dx: Double, dy: Double) {
        val angle = sqrt(dx * dx + dy * dy)
        if (angle == 0.0) return
        axis[0] = dy / angle
        axis[1] = dx / angle
        quat.setAxisAngle(deltaQuat, axis, angle)
        quat.multiply(rotationQuat, deltaQuat, rotationQuat)
    }
}

class ViewMatrices(val viewParameters: ViewParameters) {
    val cameraPosition = float32Of(0.0, 0.0, 3.0)
    val projectionMatrix = mat4.create()
    val modelMatrix = mat4.create()
    val normalMatrix = mat3.create()

    private val cameraFieldOfViewDegrees = 45.0

    private val modelTranslation =  Float32Array(3) // model at origin
    private val modelScale = Float32Array(3)

    private val tmpVec3 = Float32Array(3)
    private val tmpQuat = quat.create()

    fun initProjection(width: Int, height: Int) {
        mat4.perspective(
            projectionMatrix, cameraFieldOfViewDegrees * PI / 180,
            width.toDouble() / height, 0.1, 30.0
        )
        for (i in 0..2) tmpVec3[i] = -cameraPosition[i]
        mat4.translate(projectionMatrix, projectionMatrix, tmpVec3)
    }

    fun initView(params: ViewParameters) {
        modelScale.fill(2.0.pow(params.viewScale))
        mat4.fromRotationTranslationScale(modelMatrix, params.rotationQuat, modelTranslation, modelScale)

        quat.conjugate(tmpQuat, params.rotationQuat)
        mat3.fromQuat(normalMatrix, tmpQuat)
        mat3.transpose(normalMatrix, normalMatrix)
    }
}


