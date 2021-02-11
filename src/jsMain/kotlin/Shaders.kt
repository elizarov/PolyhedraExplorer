package polyhedra.js

import org.khronos.webgl.*
import org.khronos.webgl.WebGLRenderingContext as GL

private const val aVertexPosition = "aVertexPosition"
private const val aVertexNormal = "aVertexNormal"
private const val aVertexColor = "aVertexColor"

private const val uNormalMatrix = "uNormalMatrix"
private const val uProjectionMatrix = "uProjectionMatrix"
private const val uModelViewMatrix = "uModelViewMatrix"

private const val vColor = "vColor"
private const val vLighting = "vLighting"

val vsSource = """
    attribute vec4 $aVertexPosition;
    attribute vec3 $aVertexNormal;
    attribute vec4 $aVertexColor;

    uniform mat4 $uNormalMatrix;
    uniform mat4 $uModelViewMatrix;
    uniform mat4 $uProjectionMatrix;
    
    varying lowp vec4 $vColor;
    varying highp vec3 $vLighting;

    void main() {
      gl_Position = $uProjectionMatrix * $uModelViewMatrix * $aVertexPosition;
      
      highp vec3 ambientLight = vec3(0.3, 0.3, 0.3);
      highp vec3 directionalLightColor = vec3(1, 1, 1);
      highp vec3 directionalVector = normalize(vec3(0.85, 0.8, 0.75));
      
      highp vec4 transformedNormal = $uNormalMatrix * vec4($aVertexNormal, 1.0);
      highp float directional = max(dot(transformedNormal.xyz, directionalVector), 0.0);
      
      $vColor = $aVertexColor;
      $vLighting = ambientLight + (directionalLightColor * directional);
    }
""".trimIndent()

val fsSource = """
    varying lowp vec4 $vColor;
    varying highp vec3 $vLighting;
    
    void main() {
      gl_FragColor = vec4($vColor.rgb * $vLighting, $vColor.a);
    }
""".trimIndent()

class Shader(gl: GL) {
    val program: WebGLProgram = initShaderProgram(gl)
    val aVertexPositionLocation = gl.getAttribLocation(program, aVertexPosition)
    val aVertexNormalLocation = gl.getAttribLocation(program, aVertexNormal)
    val aVertexColorLocation = gl.getAttribLocation(program, aVertexColor)
    val projectionMatrixLocation = gl.getUniformLocation(program, uProjectionMatrix)!!
    val modelViewMatrixLocation = gl.getUniformLocation(program, uModelViewMatrix)!!
    val normalMatrixLocation = gl.getUniformLocation(program, uNormalMatrix)!!
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