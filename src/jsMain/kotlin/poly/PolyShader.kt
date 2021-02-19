package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

private const val uProjectionMatrix = "uProjectionMatrix"
private const val uModelViewMatrix = "uModelViewMatrix"
private const val uNormalMatrix = "uNormalMatrix"

private const val uAmbientLightColor = "uAmbientLightColor"
private const val uDirectionalLightColor = "uDirectionalLightColor"
private const val uDirectionalLightVector = "uDirectionalLightVector"

private const val aVertexPosition = "aVertexPosition"
private const val aVertexNormal = "aVertexNormal"
private const val aVertexColor = "aVertexColor"

private const val vColor = "vColor"
private const val vLighting = "vLighting"

private val vsSource = """
    uniform mat4 $uProjectionMatrix;
    uniform mat4 $uModelViewMatrix;
    uniform mat3 $uNormalMatrix;
    
    uniform vec3 $uAmbientLightColor;
    uniform vec3 $uDirectionalLightColor;
    uniform vec3 $uDirectionalLightVector;

    attribute vec4 $aVertexPosition;
    attribute vec3 $aVertexNormal;
    attribute vec4 $aVertexColor;
    
    varying vec4 $vColor;
    varying vec3 $vLighting;

    void main() {
      gl_Position = $uProjectionMatrix * $uModelViewMatrix * $aVertexPosition;
      vec3 transformedNormal = $uNormalMatrix * $aVertexNormal;
      float directional = max(dot(transformedNormal, $uDirectionalLightVector), 0.0);
      $vColor = $aVertexColor;
      $vLighting = $uAmbientLightColor + ($uDirectionalLightColor * directional);
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

    val uProjectionMatrixLoc = gl.getUniformLocation(program, uProjectionMatrix)!!
    val uModelViewMatrixLoc = gl.getUniformLocation(program, uModelViewMatrix)!!
    val uNormalMatrixLoc = gl.getUniformLocation(program, uNormalMatrix)!!

    val uAmbientLightColorLoc = gl.getUniformLocation(program, uAmbientLightColor)!!
    val uDirectionalLightColorLoc = gl.getUniformLocation(program, uDirectionalLightColor)!!
    val uDirectionalLightVectorLoc = gl.getUniformLocation(program, uDirectionalLightVector)!!
    
    val aVertexPositionLoc = gl.getAttribLocation(program, aVertexPosition)
    val aVertexNormalLoc = gl.getAttribLocation(program, aVertexNormal)
    val aVertexColorLoc = gl.getAttribLocation(program, aVertexColor)
}

class PolyBuffers(val gl: GL) {
    val shader = PolyShader(gl)
    val positionBuffer = gl.createBuffer()!!
    val normalBuffer = gl.createBuffer()!!
    val colorBuffer = gl.createBuffer()!!
    val indexBuffer = gl.createBuffer()
    var nIndices = 0
}

fun PolyBuffers.draw(viewMatrices: ViewMatrices, lightning: Lightning) {
    gl.useProgram(shader.program)
    gl.uniformMatrix4fv(shader.uProjectionMatrixLoc, false, viewMatrices.projectionMatrix)
    gl.uniformMatrix4fv(shader.uModelViewMatrixLoc, false, viewMatrices.modelViewMatrix)
    gl.uniformMatrix3fv(shader.uNormalMatrixLoc, false, viewMatrices.normalMatrix)

    gl.uniform3fv(shader.uAmbientLightColorLoc, lightning.ambientLightColor)
    gl.uniform3fv(shader.uDirectionalLightColorLoc, lightning.directionalLightColor)
    gl.uniform3fv(shader.uDirectionalLightVectorLoc, lightning.directionalLightVector)

    gl.enableVertexAttribBuffer(shader.aVertexPositionLoc, positionBuffer, 3)
    gl.enableVertexAttribBuffer(shader.aVertexNormalLoc, normalBuffer, 3)
    gl.enableVertexAttribBuffer(shader.aVertexColorLoc, colorBuffer, 4)
    
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer)
    gl.drawElements(GL.TRIANGLES, nIndices, GL.UNSIGNED_SHORT, 0)
}

fun GL.enableVertexAttribBuffer(location: Int, buffer: WebGLBuffer, size: Int) {
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