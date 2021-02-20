package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.util.*

fun <T : GLType<T, *>> Polyhedron.vertexAttribData(
    gl: WebGLRenderingContext,
    buffer: GLProgram.Buffer<T>,
    transform: (f: Face, v: Vertex, a: Float32Array, i: Int) -> Unit)
{
    val data = vertexArray(buffer.type.bufferSize, ::Float32Array, transform)
    gl.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER, buffer.glBuffer)
    gl.bufferData(WebGLRenderingContext.ARRAY_BUFFER, data, WebGLRenderingContext.STATIC_DRAW)
}

fun <A> Polyhedron.vertexArray(
    size: Int,
    factory: (Int) -> A,
    transform: (f: Face, v: Vertex, a: A, i: Int) -> Unit
): A {
    val m = fs.sumOf { it.size }
    val a = factory(size * m)
    var i = 0
    for (f in fs) {
        for (v in f) {
            transform(f, v, a, i)
            i += size
        }
    }
    return a
}