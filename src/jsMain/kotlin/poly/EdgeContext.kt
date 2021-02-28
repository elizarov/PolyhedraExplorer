package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.js.*
import polyhedra.js.glsl.*
import polyhedra.js.params.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

class EdgeContext(val gl: GL, val polyContext: PolyContext, override val params: PolyParams) : Param.Context(Param.UpdateType.TargetValueAndAnimationsList) {
    val program = EdgeProgram(gl)
    val indexBuffer = program.createUint16Buffer()
    var nIndices = 0
    lateinit var color: Float32Array

    init {
        setupAndUpdate()
    }

    override fun update() {
        val poly = params.targetPoly
        color = PolyStyle.edgeColor.toFloat32Array()
        program.use()
        // indices
        nIndices = poly.es.size * 4
        val indices = indexBuffer.takeData(nIndices)
        var i = 0
        var j = 0
        for (f in poly.fs) {
            for (k in 0 until f.size) {
                indices[j++] = i + k
                indices[j++] = i + (k + 1) % f.size
            }
            i += f.size
        }
        gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
        gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, indices, GL.STATIC_DRAW)
    }
}

// cullMode: 0 - no, 1 - cull front, -1 - cull back
fun EdgeContext.draw(view: ViewContext, cullMode: Int = 0) {
    program.use {
        assignView(view)
        assignPolyContext(polyContext)
        uVertexColor by color
        uCullMode by cullMode.toDouble()
    }

    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.LINES, nIndices, GL.UNSIGNED_SHORT, 0)
}
