package polyhedra.js.poly

import org.khronos.webgl.*
import polyhedra.js.*
import polyhedra.js.glsl.*
import polyhedra.js.params.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceContext(val gl: GL, val polyContext: PolyContext, override val params: PolyParams) : Param.Context(Param.UpdateType.TargetValueAndAnimationsList)  {
    val program = FaceProgram(gl)
    val colorBuffer = program.aVertexColor.createBuffer()
    val indexBuffer = program.createUint16Buffer()
    var nIndices = 0                 

    init {
        setupAndUpdate()
    }

    override fun update() {
        val poly = params.targetPoly
        poly.faceVerticesData(colorBuffer) { f, _, a, i ->
            a.setRGB(i, PolyStyle.faceColor(f))
        }
        colorBuffer.bindBufferData(gl)
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

fun FaceContext.draw(view: ViewContext, lightning: LightningContext) {
    program.use {
        assignView(view)
        assignPolyGeometry(polyContext)

        uAmbientLightColor by lightning.ambientLightColor
        uDiffuseLightColor by lightning.diffuseLightColor
        uSpecularLightColor by lightning.specularLightColor
        uSpecularLightPower by lightning.specularLightPower
        uLightPosition by lightning.lightPosition

        aVertexColor by colorBuffer
    }
    
    gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, indexBuffer.glBuffer)
    gl.drawElements(GL.TRIANGLES, nIndices, GL.UNSIGNED_SHORT, 0)
}

