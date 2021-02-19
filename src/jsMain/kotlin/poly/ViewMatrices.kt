package polyhedra.js.poly

import org.khronos.webgl.*
import org.w3c.dom.*
import polyhedra.js.util.*
import kotlin.math.*

class ViewParameters {
    var rotationQuat = quat.create()
    var viewScale = 0.0

    private val axis = vec3.create()
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

class ViewMatrices(canvas: HTMLCanvasElement) {
    val projectionMatrix = mat4.create()
    val modelViewMatrix = mat4.create()
    val normalMatrix = mat4.create()

    private val fieldOfViewDegrees = 45
    private val modelViewTranslation = float32Of(-0.0f, 0.0f, -6.0f)
    private val modelViewScale = Float32Array(3)

    init {
        mat4.perspective(
            projectionMatrix, fieldOfViewDegrees * PI / 180,
            canvas.clientWidth.toDouble() / canvas.clientHeight, 0.1, 100.0
        )
    }

    fun initModelAndNormalMatrices(params: ViewParameters) {
        modelViewScale.fill(2.0.pow(params.viewScale))
        mat4.fromRotationTranslationScale(modelViewMatrix, params.rotationQuat, modelViewTranslation, modelViewScale)

        mat4.invert(normalMatrix, modelViewMatrix)
        mat4.transpose(normalMatrix, normalMatrix)
    }
}


