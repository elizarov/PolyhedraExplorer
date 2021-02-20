package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.util.*

fun <T : GLType<T, *>> Polyhedron.faceVerticesData(
    gl: WebGLRenderingContext,
    buffer: Float32Buffer<T>,
    transform: (f: Face, v: Vertex, a: Float32Array, i: Int) -> Unit)
{
    val m = fs.sumOf { it.size }
    val a = buffer.takeData(buffer.type.bufferSize * m)
    var i = 0
    for (f in fs) {
        for (v in f) {
            transform(f, v, a, i)
            i += buffer.type.bufferSize
        }
    }
    gl.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, buffer.glBuffer)
    gl.bufferData(WebGLRenderingContext.ARRAY_BUFFER, a, WebGLRenderingContext.STATIC_DRAW)
}
