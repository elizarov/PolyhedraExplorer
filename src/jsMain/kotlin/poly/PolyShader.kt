package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

private const val uProjectionMatrix = "uProjectionMatrix"
private const val uModelViewMatrix = "uModelViewMatrix"
private const val uNormalMatrix = "uNormalMatrix"

private const val aVertexPosition = "aVertexPosition"
private const val aVertexNormal = "aVertexNormal"
private const val aVertexColor = "aVertexColor"

private const val vColor = "vColor"
private const val vLighting = "vLighting"

private val vsSource = """
    uniform mat4 $uProjectionMatrix;
    uniform mat4 $uModelViewMatrix;
    uniform mat4 $uNormalMatrix;

    attribute vec4 $aVertexPosition;
    attribute vec3 $aVertexNormal;
    attribute vec4 $aVertexColor;
    
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

private val fsSource = """
    varying lowp vec4 $vColor;
    varying highp vec3 $vLighting;
    
    void main() {
      gl_FragColor = vec4($vColor.rgb * $vLighting, $vColor.a);
    }
""".trimIndent()

class PolyShader(gl: GL) {
    val program: WebGLProgram = initShaderProgram(gl, vsSource, fsSource)
    val projectionMatrixLocation = gl.getUniformLocation(program, uProjectionMatrix)!!
    val modelViewMatrixLocation = gl.getUniformLocation(program, uModelViewMatrix)!!
    val normalMatrixLocation = gl.getUniformLocation(program, uNormalMatrix)!!
    val aVertexPositionLocation = gl.getAttribLocation(program, aVertexPosition)
    val aVertexNormalLocation = gl.getAttribLocation(program, aVertexNormal)
    val aVertexColorLocation = gl.getAttribLocation(program, aVertexColor)
}

class PolyBuffers(val gl: GL) {
    val shader = PolyShader(gl)
    val positionBuffer = gl.createBuffer()!!
    val normalBuffer = gl.createBuffer()!!
    val colorBuffer = gl.createBuffer()!!
    val indexBuffer = gl.createBuffer()
    var nIndices = 0
}

fun PolyBuffers.draw(viewMatrices: ViewMatrices) {
    gl.useProgram(shader.program)
    gl.uniformMatrix4fv(shader.projectionMatrixLocation, false, viewMatrices.projectionMatrix)
    gl.uniformMatrix4fv(shader.modelViewMatrixLocation, false, viewMatrices.modelViewMatrix)
    gl.uniformMatrix4fv(shader.normalMatrixLocation, false, viewMatrices.normalMatrix)

    gl.enableVertexAttribBuffer(positionBuffer, shader.aVertexPositionLocation, 3)
    gl.enableVertexAttribBuffer(normalBuffer, shader.aVertexNormalLocation, 3)
    gl.enableVertexAttribBuffer(colorBuffer, shader.aVertexColorLocation, 4)
    
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer)
    gl.drawElements(GL.TRIANGLES, nIndices, GL.UNSIGNED_SHORT, 0)
}

fun GL.enableVertexAttribBuffer(buffer: WebGLBuffer, location: Int, size: Int) {
    bindBuffer(GL.ARRAY_BUFFER, buffer)
    vertexAttribPointer(location, size, GL.FLOAT, false, 0, 0)
    enableVertexAttribArray(location)
}

fun PolyBuffers.initBuffers(poly: Polyhedron, style: PolyStyle) {
    gl.useProgram(shader.program)
    poly.vertexAttribData(gl, positionBuffer, 3) { _, v, a, i ->
        a[i] = v.pt
    }
    poly.vertexAttribData(gl, normalBuffer,3) { f, _, a, i ->
        a[i] = f.plane.n
    }
    poly.vertexAttribData(gl, colorBuffer, 4) { f, _, a, i ->
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

fun Polyhedron.vertexAttribData(
    gl: GL,
    buffer: WebGLBuffer,
    size: Int,
    transform: (f: Face, v: Vertex, a: Float32Array, i: Int) -> Unit)
{
    val data = vertexArray(size, ::Float32Array, transform)
    gl.bindBuffer(GL.ARRAY_BUFFER, buffer)
    gl.bufferData(GL.ARRAY_BUFFER, data, GL.STATIC_DRAW)
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