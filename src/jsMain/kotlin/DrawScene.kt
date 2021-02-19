package polyhedra.js

import org.khronos.webgl.*
import org.w3c.dom.*
import polyhedra.common.*
import kotlin.math.*
import org.khronos.webgl.WebGLRenderingContext as GL

class DrawContext(
    val canvas: HTMLCanvasElement
) {
    val gl: GL = canvas.getContext("webgl") as GL
    val backgroundColor = canvas.computedStyle().backgroundColor.parseCSSColor() ?: Color(0.0f, 0.0f, 0.0f)

    val shader = Shader(gl)
    
    val projectionMatrix = mat4.create()
    val modelViewMatrix = mat4.create()
    val normalMatrix = mat4.create()

    val modelViewTranslation = float32Of(-0.0f, 0.0f, -6.0f)

    val positionBuffer = gl.createBuffer()!!
    val normalBuffer = gl.createBuffer()!!
    val colorBuffer = gl.createBuffer()!!
    val indexBuffer = gl.createBuffer()
    var nIndices = 0
}

private fun DrawContext.initProjectionMatrix(fieldOfViewDegrees: Double) {
    mat4.perspective(
        projectionMatrix, fieldOfViewDegrees * PI / 180,
        canvas.clientWidth.toDouble() / canvas.clientHeight, 0.1, 100.0
    )
}

private fun DrawContext.initModelAndNormalMatrices(state: PolyCanvasState) {
    mat4.fromTranslation(modelViewMatrix, modelViewTranslation)
    mat4.rotateX(modelViewMatrix, modelViewMatrix, state.rotation * 0.6)
    mat4.rotateZ(modelViewMatrix, modelViewMatrix, state.rotation)

    mat4.invert(normalMatrix, modelViewMatrix)
    mat4.transpose(normalMatrix, normalMatrix)
}

fun DrawContext.drawScene(poly: Polyhedron, style: PolyStyle, state: PolyCanvasState) {
    gl.clearColor(backgroundColor.r, backgroundColor.g, backgroundColor.b, backgroundColor.a)
    gl.clearDepth(1.0f)
    gl.enable(GL.DEPTH_TEST)
    gl.depthFunc(GL.LEQUAL)
    gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)
    
    gl.useProgram(shader.program)

    initPolyhedra(poly, style)
    initProjectionMatrix(45.0)
    initModelAndNormalMatrices(state)

    gl.uniformMatrix4fv(shader.projectionMatrixLocation, false, projectionMatrix)
    gl.uniformMatrix4fv(shader.modelViewMatrixLocation, false, modelViewMatrix)
    gl.uniformMatrix4fv(shader.normalMatrixLocation, false, normalMatrix)

    gl.drawElements(GL.TRIANGLES, nIndices, GL.UNSIGNED_SHORT, 0)
}

fun DrawContext.initPolyhedra(poly: Polyhedron, style: PolyStyle) {
    gl.vertexAttribArray(poly, positionBuffer, shader.aVertexPositionLocation, 3) { _, v, a, i ->
        a[i] = v.pt
    }
    gl.vertexAttribArray(poly, normalBuffer, shader.aVertexNormalLocation, 3) { f, _, a, i ->
        a[i] = f.plane.n
    }
    gl.vertexAttribArray(poly, colorBuffer, shader.aVertexColorLocation, 4) { f, _, a, i ->
        a[i] = style.faceColor(f)
    }
    // indices
    nIndices = poly.fs.sumOf { 3 * (it.size - 2) }
    val indices = Uint16Array(nIndices)
    var i = 0
    var j = 0
    for (f in poly.fs) {
        for (k in 2 until f.size) {
            indices[j++] = i
            indices[j++] = i + k - 1
            indices[j++] = i + k
        }
        i += f.size
    }
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer)
    gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, indices, GL.STATIC_DRAW)
}

fun GL.vertexAttribArray(
    poly: Polyhedron,
    buffer: WebGLBuffer,
    location: Int,
    size: Int,
    transform: (f: Face, v: Vertex, a: Float32Array, i: Int) -> Unit)
{
    val data = poly.vertexArray(size, ::Float32Array, transform)
    bindBuffer(GL.ARRAY_BUFFER, buffer)
    bufferData(GL.ARRAY_BUFFER, data, GL.STATIC_DRAW)
    vertexAttribPointer(location, size, GL.FLOAT, false, 0, 0)
    enableVertexAttribArray(location)
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
