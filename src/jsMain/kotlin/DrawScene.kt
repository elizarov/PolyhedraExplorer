package polyhedra.js

import org.khronos.webgl.*
import kotlin.math.*
import org.khronos.webgl.WebGLRenderingContext as GL

class DrawContext(
    val gl: GL,
) {
    val shader = Shader(gl)
    
    val projectionMatrix = mat4.create()
    val modelViewMatrix = mat4.create()

    val modelViewTranslation = float32Of(-0.0f, 0.0f, -6.0f)

    val positions = float32Of(
        // Front face
        -1.0, -1.0,  1.0,
        1.0, -1.0,  1.0,
        1.0,  1.0,  1.0,
        -1.0,  1.0,  1.0,

        // Back face
        -1.0, -1.0, -1.0,
        -1.0,  1.0, -1.0,
        1.0,  1.0, -1.0,
        1.0, -1.0, -1.0,

        // Top face
        -1.0,  1.0, -1.0,
        -1.0,  1.0,  1.0,
        1.0,  1.0,  1.0,
        1.0,  1.0, -1.0,

        // Bottom face
        -1.0, -1.0, -1.0,
        1.0, -1.0, -1.0,
        1.0, -1.0,  1.0,
        -1.0, -1.0,  1.0,

        // Right face
        1.0, -1.0, -1.0,
        1.0,  1.0, -1.0,
        1.0,  1.0,  1.0,
        1.0, -1.0,  1.0,

        // Left face
        -1.0, -1.0, -1.0,
        -1.0, -1.0,  1.0,
        -1.0,  1.0,  1.0,
        -1.0,  1.0, -1.0,
    )

    val positionBuffer = gl.createBuffer()!!

    val nVertices = positions.length / 3

    val faceColors = float32Of(
        1.0,  1.0,  1.0,  1.0,    // Front face: white
        1.0,  0.0,  0.0,  1.0,    // Back face: red
        0.0,  1.0,  0.0,  1.0,    // Top face: green
        0.0,  0.0,  1.0,  1.0,    // Bottom face: blue
        1.0,  1.0,  0.0,  1.0,    // Right face: yellow
        1.0,  0.0,  1.0,  1.0,    // Left face: purple
    )

    val colors = Float32Array(nVertices * 4).apply {
        for (i in 0 until nVertices) {
            val j = i / 4
            for (k in 0 until 4)
                this[i * 4 + k] = faceColors[j * 4 + k]
        }
    }

    val colorBuffer = gl.createBuffer()

    val indices = uint16Of(
        0,  1,  2,      0,  2,  3,    // front
        4,  5,  6,      4,  6,  7,    // back
        8,  9,  10,     8,  10, 11,   // top
        12, 13, 14,     12, 14, 15,   // bottom
        16, 17, 18,     16, 18, 19,   // right
        20, 21, 22,     20, 22, 23,   // left
    )

    val indexBuffer = gl.createBuffer()
}

private fun DrawContext.initProjectionMatrix(gl: GL, fieldOfViewDegrees: Double) {
    mat4.perspective(
        projectionMatrix, fieldOfViewDegrees * PI / 180,
        gl.canvas.clientWidth.toDouble() / gl.canvas.clientHeight, 0.1, 100.0
    )
}

private fun DrawContext.initModelViewMatrix(state: CanvasState) {
    mat4.fromTranslation(modelViewMatrix, modelViewTranslation)
    mat4.rotateX(modelViewMatrix, modelViewMatrix, state.rotation * 0.6)
    mat4.rotateZ(modelViewMatrix, modelViewMatrix, state.rotation)
}

fun DrawContext.drawScene(state: CanvasState) {
    gl.clearColor(0.0f, 0.0f, 0.0f, 1.0f)
    gl.clearDepth(1.0f)
    gl.enable(GL.DEPTH_TEST)
    gl.depthFunc(GL.LEQUAL)
    gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)
    
    gl.useProgram(shader.program)

    initProjectionMatrix(gl, 45.0)
    initModelViewMatrix(state)

    gl.uniformMatrix4fv(shader.projectionMatrixLocation, false, projectionMatrix)
    gl.uniformMatrix4fv(shader.modelViewMatrixLocation, false, modelViewMatrix)

    gl.bindBuffer(GL.ARRAY_BUFFER, positionBuffer)
    gl.bufferData(GL.ARRAY_BUFFER, positions, GL.STATIC_DRAW)
    gl.vertexAttribPointer(shader.aVertexPositionLocation, 3, GL.FLOAT, false, 0, 0)
    gl.enableVertexAttribArray(shader.aVertexPositionLocation)

    gl.bindBuffer(GL.ARRAY_BUFFER, colorBuffer)
    gl.bufferData(GL.ARRAY_BUFFER, colors, GL.STATIC_DRAW)
    gl.vertexAttribPointer(shader.aVertexColorLocation, 4, GL.FLOAT, false, 0, 0)
    gl.enableVertexAttribArray(shader.aVertexColorLocation)

    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer);
    gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, indices, GL.STATIC_DRAW);

    gl.drawElements(GL.TRIANGLES, indices.length, GL.UNSIGNED_SHORT, 0)
}

