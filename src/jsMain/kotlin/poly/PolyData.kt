/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.poly.*
import polyhedra.js.glsl.*

fun <T : GLType<T>, D, B: GLBuffer<T, D>> Polyhedron.faceVerticesData(
    buffer: B,
    transform: (f: Face, v: Vertex, a: D, i: Int) -> Unit)
{
    val m = fs.sumOf { it.size }
    val s = buffer.type.bufferSize
    val a = buffer.takeData(s * m)
    var i = 0
    for (f in fs) {
        for (v in f) {
            transform(f, v, a, i)
            i += s
        }
    }
}
