package polyhedra.js.poly

import polyhedra.common.*
import polyhedra.js.glsl.*
import polyhedra.js.params.*
import org.khronos.webgl.WebGLRenderingContext as GL

class PolyContext(val gl: GL, override val params: PolyParams) : Param.Context(Param.UpdateType.ValueUpdateAndAnimationEffects) {
    val target = PolyBuffers(gl)
    val prev = PolyBuffers(gl)
    var animated: Boolean = false
        private set

    init {
        setupAndUpdate()
    }

    override fun update() {
        target.initBuffers(params.poly)
    }
}

class PolyBuffers(val gl: GL) {
    val positionBuffer = createBuffer(gl, GLType.vec3)
    val normalBuffer = createBuffer(gl, GLType.vec3)
}

private fun PolyBuffers.initBuffers(poly: Polyhedron) {
    poly.faceVerticesData(positionBuffer) { _, v, a, i ->
        a[i] = v.pt
    }
    positionBuffer.bindBufferData(gl)

    poly.faceVerticesData(normalBuffer) { f, _, a, i ->
        a[i] = f.plane.n
    }
    normalBuffer.bindBufferData(gl)
}

