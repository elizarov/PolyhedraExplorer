package polyhedra.js.util

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.*

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

inline operator fun Uint16Array.set(i: Int, x: Int) {
    set(i, x.toShort())
}

fun loadShader(gl: WebGLRenderingContext, type: Int, source: String): WebGLShader {
    val shader = gl.createShader(type)!!
    gl.shaderSource(shader, source)
    gl.compileShader(shader)
    if (gl.getShaderParameter(shader, WebGLRenderingContext.COMPILE_STATUS) != true)
        error("Shader compilation error: ${gl.getShaderInfoLog(shader)}")
    return shader
}

fun initShaderProgram(gl: WebGLRenderingContext, vsSource: String, fsSource: String): WebGLProgram {
    val vs = loadShader(gl, WebGLRenderingContext.VERTEX_SHADER, vsSource)
    val fs = loadShader(gl, WebGLRenderingContext.FRAGMENT_SHADER, fsSource)
    val shaderProgram = gl.createProgram()!!
    gl.attachShader(shaderProgram, vs)
    gl.attachShader(shaderProgram, fs)
    gl.linkProgram(shaderProgram)
    if (gl.getProgramParameter(shaderProgram, WebGLRenderingContext.LINK_STATUS) != true)
        error("Shader program error: ${gl.getProgramInfoLog(shaderProgram)}")
    return shaderProgram
}