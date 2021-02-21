package polyhedra.js.poly

import org.khronos.webgl.*
import org.w3c.dom.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

class DrawContext(canvas: HTMLCanvasElement) {
    val gl: GL = canvas.getContext("webgl") as GL

    val viewParameters = ViewParameters()
    val viewMatrices = ViewMatrices(viewParameters)
    val lightning = Lightning()
    
    val sharedPolyBuffers = SharedPolyBuffers(gl)
    val faceBuffers = FaceBuffers(gl, sharedPolyBuffers)
    val edgeBuffers = EdgeBuffers(gl, sharedPolyBuffers)

    var prevPoly: Polyhedron? = null
    var prevStyle: PolyStyle? = null
}

fun DrawContext.drawScene(poly: Polyhedron, style: PolyStyle) {
    if (poly != prevPoly || style != prevStyle) {
        prevPoly = poly
        prevStyle = style
        sharedPolyBuffers.initBuffers(poly)
        if (style.display.hasFaces()) faceBuffers.initBuffers(poly, style)
        if (style.display.hasEdges()) edgeBuffers.initBuffers(poly, style)
    }

    val width = gl.canvas.width
    val height = gl.canvas.height

    gl.viewport(0, 0, width, height);
    gl.clearColor(0.0f, 0.0f, 0.0f, 0.0f)

    if (viewParameters.transparent == 0.0) {
        gl.enable(GL.DEPTH_TEST)
        gl.clearDepth(1.0f)
        gl.depthFunc(GL.LEQUAL)
    } else {
        gl.disable(GL.DEPTH_TEST)
    }
    gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)

    viewMatrices.initProjection(width, height)
    viewMatrices.initView(viewParameters)
    if (style.display.hasFaces()) faceBuffers.draw(viewMatrices, lightning)
    if (style.display.hasEdges()) edgeBuffers.draw(viewMatrices)
}




