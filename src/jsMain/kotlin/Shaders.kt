package polyhedra.js

import org.khronos.webgl.*
import org.khronos.webgl.WebGLRenderingContext as GL

private const val aVertexPosition = "aVertexPosition"
private const val uProjectionMatrix = "uProjectionMatrix"
private const val uModelViewMatrix = "uModelViewMatrix"
private const val aVertexColor = "aVertexColor"
private const val vColor = "vColor"

val vsSource = """
    attribute vec4 $aVertexPosition;
    attribute vec4 $aVertexColor;

    uniform mat4 $uModelViewMatrix;
    uniform mat4 $uProjectionMatrix;
    
    varying lowp vec4 $vColor;

    void main() {
      gl_Position = $uProjectionMatrix * $uModelViewMatrix * $aVertexPosition;
      vColor = $aVertexColor;
    }
""".trimIndent()

val fsSource = """
    varying lowp vec4 $vColor;
    
    void main() {
      gl_FragColor = $vColor;
    }
""".trimIndent()

class Shader(gl: GL) {
    val program: WebGLProgram = initShaderProgram(gl)
    val aVertexPositionLocation = gl.getAttribLocation(program, aVertexPosition)
    val aVertexColorLocation = gl.getAttribLocation(program, aVertexColor)
    val projectionMatrixLocation = gl.getUniformLocation(program, uProjectionMatrix)!!
    val modelViewMatrixLocation = gl.getUniformLocation(program, uModelViewMatrix)!!
}

private fun loadShader(gl: GL, type: Int, source: String): WebGLShader {
    val shader = gl.createShader(type)!!
    gl.shaderSource(shader, source)
    gl.compileShader(shader)
    if (gl.getShaderParameter(shader, GL.COMPILE_STATUS) != true)
        error("Shader compilation error: ${gl.getShaderInfoLog(shader)}")
    return shader
}

private fun initShaderProgram(gl: GL): WebGLProgram {
    val vs = loadShader(gl, GL.VERTEX_SHADER, vsSource)
    val fs = loadShader(gl, GL.FRAGMENT_SHADER, fsSource)
    val shaderProgram = gl.createProgram()!!
    gl.attachShader(shaderProgram, vs)
    gl.attachShader(shaderProgram, fs)
    gl.linkProgram(shaderProgram)
    if (gl.getProgramParameter(shaderProgram, GL.LINK_STATUS) != true)
        error("Shader program error: ${gl.getProgramInfoLog(shaderProgram)}")
    return shaderProgram
}