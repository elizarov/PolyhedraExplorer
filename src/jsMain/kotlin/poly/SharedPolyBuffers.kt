package polyhedra.js.poly

import polyhedra.common.*
import polyhedra.js.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

class SharedPolyBuffers(val gl: GL) {
    val positionBuffer = createBuffer(gl, GLType.vec3)
    val normalBuffer = createBuffer(gl, GLType.vec3)
}

fun SharedPolyBuffers.initBuffers(poly: Polyhedron) {
    poly.faceVerticesData(positionBuffer) { _, v, a, i ->
        a[i] = v.pt
    }
    positionBuffer.bindBufferData(gl)

    poly.faceVerticesData(normalBuffer) { f, _, a, i ->
        a[i] = f.plane.n
    }
    normalBuffer.bindBufferData(gl)
}

