package polyhedra.js.poly

import org.khronos.webgl.*
import org.w3c.dom.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.util.*
import kotlin.math.*
import org.khronos.webgl.WebGLRenderingContext as GL

class DrawContext(canvas: HTMLCanvasElement) {
    val gl: GL = canvas.getContext("webgl") as GL
    val backgroundColor = canvas.computedStyle().backgroundColor.parseCSSColor() ?: Color(0.0f, 0.0f, 0.0f)

    val viewParameters = ViewParameters()
    val viewMatrices = ViewMatrices(canvas)
    val lightning = Lightning()
    val polyBuffers = PolyBuffers(gl)

    var prevPoly: Polyhedron? = null
    var prevStyle: PolyStyle? = null
}

fun DrawContext.drawScene(poly: Polyhedron, style: PolyStyle) {
    if (poly != prevPoly || style != prevStyle) {
        prevPoly = poly
        prevStyle = style
        polyBuffers.initBuffers(poly, style)
    }

    gl.clearColor(backgroundColor.r, backgroundColor.g, backgroundColor.b, backgroundColor.a)
    gl.clearDepth(1.0f)
    gl.enable(GL.DEPTH_TEST)
    gl.depthFunc(GL.LEQUAL)
    gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)
    
    viewMatrices.initModelAndNormalMatrices(viewParameters)
    polyBuffers.draw(viewMatrices, lightning)
}




