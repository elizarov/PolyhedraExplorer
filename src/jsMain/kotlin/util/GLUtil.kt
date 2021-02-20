package polyhedra.js.util

import org.khronos.webgl.*
import polyhedra.common.*

fun float32Of(vararg a: Float) = Float32Array(a.size).apply {
    for (i in a.indices) this[i] = a[i]
}

fun float32Of(vararg a: Double) = Float32Array(a.size).apply {
    for (i in a.indices) this[i] = a[i].toFloat()
}

fun uint16Of(vararg a: Int) = Uint16Array(a.size).apply {
    for (i in a.indices) this[i] = a[i].toShort()
}

inline operator fun Float32Array.set(i: Int, x: Double) {
    set(i, x.toFloat())
}

fun Float32Array.fill(x: Double) {
    for (i in 0 until length) set(i, x.toFloat())
}

operator fun Float32Array.set(i: Int, v: Vec3) {
    set(i, v.x)
    set(i + 1, v.y)
    set(i + 2, v.z)
}

operator fun Float32Array.set(i: Int, c: Color) {
    set(i, c.r)
    set(i + 1, c.g)
    set(i + 2, c.b)
    set(i + 3, c.a)
}

fun Vec3.toFloat32Array(): Float32Array = Float32Array(3).apply {
    set(0, x)
    set(1, y)
    set(2, z)
}

inline operator fun Uint16Array.set(i: Int, x: Int) {
    set(i, x.toShort())
}

fun loadShader(gl: WebGLRenderingContext, type: Int, source: String): WebGLShader {
    val shader = gl.createShader(type)!!
    gl.shaderSource(shader, source)
    gl.compileShader(shader)
    if (gl.getShaderParameter(shader, WebGLRenderingContext.COMPILE_STATUS) != true) {
        val error = gl.getShaderInfoLog(shader)
        println("Error while compiling shader: $error")
        println("// --- begin source ---")
        println(source)
        println("// --- end source ---")
        error("Shader compilation error: $error")
    }
    return shader
}

fun initShaderProgram(gl: WebGLRenderingContext, vs: WebGLShader, fs: WebGLShader): WebGLProgram {
    val shaderProgram = gl.createProgram()!!
    gl.attachShader(shaderProgram, vs)
    gl.attachShader(shaderProgram, fs)
    gl.linkProgram(shaderProgram)
    if (gl.getProgramParameter(shaderProgram, WebGLRenderingContext.LINK_STATUS) != true)
        error("Shader program error: ${gl.getProgramInfoLog(shaderProgram)}")
    return shaderProgram
}

