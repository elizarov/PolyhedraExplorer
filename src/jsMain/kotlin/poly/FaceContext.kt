package polyhedra.js.poly

import org.khronos.webgl.*
import org.w3c.dom.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.glsl.*
import polyhedra.js.params.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceContext(val gl: GL, val polyContext: PolyContext, override val params: PolyParams) : Param.Context(Param.UpdateType.TargetValueAndAnimationsList)  {
    val program = FaceProgram(gl)
    val colorBuffer = program.aVertexColor.createBuffer()
    val prevColorBuffer = program.aPrevVertexColor.createBuffer()
    val indexBuffer = program.createUint16Buffer()
    var nIndices = 0                 

    init {
        setupAndUpdate()
    }

    override fun update() {
        val poly = params.targetPoly
        val animation = params.transformAnimation
        if (animation != null) {
            updateColor(gl, animation.targetPoly, colorBuffer, animation.target.dual)
            updateColor(gl, animation.prevPoly, prevColorBuffer, animation.prev.dual)
        } else {
            // simple case without animation
            updateColor(gl, poly, colorBuffer)
        }
        // indices
        nIndices = poly.fs.sumOf { 3 * (it.size - 2) }
        val indices = indexBuffer.takeData(nIndices)
        var i = 0
        var j = 0
        for (f in poly.fs) {
            // Note: In GL front faces are CCW
            for (k in 2 until f.size) {
                indices[j++] = i
                indices[j++] = i + k
                indices[j++] = i + k - 1
            }
            i += f.size
        }
        gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
        gl.bufferData(GL.ELEMENT_ARRAY_BUFFER, indices, GL.STATIC_DRAW)
    }

}

private fun updateColor(gl: GL, poly: Polyhedron, buffer: Float32Buffer<GLType.vec3>, dual: Boolean = false) {
    poly.faceVerticesData(buffer) { f, _, a, i ->
        a.setRGB(i, PolyStyle.faceColor(f, dual))
    }
    buffer.bindBufferData(gl)
}

fun FaceContext.draw(view: ViewContext, lightning: LightningContext) {
    program.use {
        assignView(view)
        assignPolyContext(polyContext)

        uAmbientLightColor by lightning.ambientLightColor
        uDiffuseLightColor by lightning.diffuseLightColor
        uSpecularLightColor by lightning.specularLightColor
        uSpecularLightPower by lightning.specularLightPower
        uLightPosition by lightning.lightPosition

        aVertexColor by colorBuffer
        aPrevVertexColor by if (params.transformAnimation != null) prevColorBuffer else colorBuffer
    }
    
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.TRIANGLES, nIndices, GL.UNSIGNED_SHORT, 0)
}

