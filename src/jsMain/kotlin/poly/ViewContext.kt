package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.js.glsl.*
import polyhedra.js.params.*
import polyhedra.js.util.*
import kotlin.math.*

class ViewContext(override val params: ViewParams) : Param.Context() {
    var rotationQuat = quat.create()

    val cameraPosition = float32Of(0.0, 0.0, 3.0)
    val projectionMatrix = mat4.create()
    val modelMatrix = mat4.create()
    val normalMatrix = mat3.create()

    private val cameraFieldOfViewDegrees = 45.0

    private val modelTranslation =  Float32Array(3) // model at origin
    private val modelScale = Float32Array(3)

    private val tmpQuat = quat.create()
    private val tmpVec3 = Float32Array(3)

    fun rotate(dx: Double, dy: Double) {
        val angle = sqrt(dx * dx + dy * dy)
        if (angle == 0.0) return
        tmpVec3[0] = dy / angle
        tmpVec3[1] = dx / angle
        tmpVec3[2] = 0.0
        quat.setAxisAngle(tmpQuat, tmpVec3, angle)
        quat.multiply(rotationQuat, tmpQuat, rotationQuat)
        update()
    }

    fun initProjection(width: Int, height: Int) {
        mat4.perspective(
            projectionMatrix, cameraFieldOfViewDegrees * PI / 180,
            width.toDouble() / height, 0.1, 30.0
        )
        for (i in 0..2) tmpVec3[i] = -cameraPosition[i]
        mat4.translate(projectionMatrix, projectionMatrix, tmpVec3)
    }

    init {
        setupAndUpdate()
    }

    override fun update() {
        modelScale.fill(2.0.pow(params.scale.value))
        mat4.fromRotationTranslationScale(modelMatrix, rotationQuat, modelTranslation, modelScale)

        quat.conjugate(tmpQuat, rotationQuat)
        mat3.fromQuat(normalMatrix, tmpQuat)
        mat3.transpose(normalMatrix, normalMatrix)
    }
}


