package polyhedra.js

import org.khronos.webgl.*

fun float32Of(vararg a: Float) = Float32Array(a.size).apply {
    for (i in a.indices) this[i] = a[i]
}

fun float32Of(vararg a: Double) = Float32Array(a.size).apply {
    for (i in a.indices) this[i] = a[i].toFloat()
}

fun uint16Of(vararg a: Int) = Uint16Array(a.size).apply {
    for (i in a.indices) this[i] = a[i].toShort()
}