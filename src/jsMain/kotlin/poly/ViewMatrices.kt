package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.js.util.*
import kotlin.math.*

class ViewParameters {
    var rotationQuat = quat.create()
    var viewScale = 0.0
    var expand = 0.0

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

class ViewMatrices {
    val projectionMatrix = mat4.create()
    val modelViewMatrix = mat4.create()
    val normalMatrix = mat3.create()
    var expand = 0.0

    private val fieldOfViewDegrees = 45
    private val modelViewTranslation = float32Of(-0.0, 0.0, -3.0)
    private val modelViewScale = Float32Array(3)

    fun initProjection(width: Int, height: Int) {
        mat4.perspective(
            projectionMatrix, fieldOfViewDegrees * PI / 180,
            width.toDouble() / height, 0.1, 100.0
        )
    }

    fun initView(params: ViewParameters) {
        expand = params.expand
        modelViewScale.fill(2.0.pow(params.viewScale))
        mat4.fromRotationTranslationScale(modelViewMatrix, params.rotationQuat, modelViewTranslation, modelViewScale)

        mat3.fromQuat(normalMatrix, params.rotationQuat)
        mat3.invert(normalMatrix, normalMatrix)
        mat3.transpose(normalMatrix, normalMatrix)
    }
}


