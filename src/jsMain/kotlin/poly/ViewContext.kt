package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.js.glsl.*
import polyhedra.js.params.*
import polyhedra.js.util.*
import kotlin.math.*

class ViewContext(override val params: ViewParams) : Param.Context() {
    val cameraPosition = float32Of(0.0, 0.0, 3.0)
    val projectionMatrix = mat4.create()
    val modelMatrix = mat4.create()
    val normalMatrix = mat3.create()

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
        setupAndUpdate()
    }

    override fun update() {
        modelScale.fill(2.0.pow(params.scale.animatedValue))
        val r = params.rotate.animatedValue
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


