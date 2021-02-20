package polyhedra.js.poly

import org.khronos.webgl.*
import org.w3c.dom.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

class DrawContext(canvas: HTMLCanvasElement) {
    val gl: GL = canvas.getContext("webgl") as GL
    val backgroundColor = canvas.computedStyle().backgroundColor.parseCSSColor() ?: Color(0.0f, 0.0f, 0.0f)

    val viewParameters = ViewParameters()
    val viewMatrices = ViewMatrices()
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

    gl.clearColor(backgroundColor.r, backgroundColor.g, backgroundColor.b, backgroundColor.a)
    gl.clearDepth(1.0f)
    gl.enable(GL.DEPTH_TEST)
    gl.depthFunc(GL.LEQUAL)
    gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)
    gl.viewport(0, 0, width, height);

    viewMatrices.initProjection(width, height)
    viewMatrices.initView(viewParameters)
    if (style.display.hasFaces()) faceBuffers.draw(viewMatrices, lightning)
    if (style.display.hasEdges()) edgeBuffers.draw(viewMatrices)
}




