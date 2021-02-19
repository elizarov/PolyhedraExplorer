package polyhedra.js.poly

import org.w3c.dom.*
import polyhedra.js.util.*
import kotlin.math.*

class ViewParameters {
    var rotateX = 0.0
    var rotateY = 0.0
}

class ViewMatrices(canvas: HTMLCanvasElement) {
    val projectionMatrix = mat4.create()
    val modelViewMatrix = mat4.create()
    val normalMatrix = mat4.create()

    val fieldOfViewDegrees = 45
    val modelViewTranslation = float32Of(-0.0f, 0.0f, -6.0f)

    init {
        mat4.perspective(
            projectionMatrix, fieldOfViewDegrees * PI / 180,
            canvas.clientWidth.toDouble() / canvas.clientHeight, 0.1, 100.0
        )
    }
}

fun ViewMatrices.initModelAndNormalMatrices(params: ViewParameters) {
    mat4.fromTranslation(modelViewMatrix, modelViewTranslation)
    mat4.rotateX(modelViewMatrix, modelViewMatrix, params.rotateX)
    mat4.rotateZ(modelViewMatrix, modelViewMatrix, params.rotateY)

    mat4.invert(normalMatrix, modelViewMatrix)
    mat4.transpose(normalMatrix, normalMatrix)
}

