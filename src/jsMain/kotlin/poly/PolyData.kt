package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.glsl.*

fun <T : GLType<T>> Polyhedron.faceVerticesData(
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
}
