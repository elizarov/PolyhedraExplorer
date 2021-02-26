package polyhedra.js.poly

import org.khronos.webgl.*
import org.w3c.dom.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.params.*
import org.khronos.webgl.WebGLRenderingContext as GL

class DrawContext(
    canvas: HTMLCanvasElement,
    override val params: PolyParams,
    private val onUpdate: () -> Unit,
) : Param.Context() {
    val gl: GL = canvas.getContext("webgl") as GL

    val view = ViewContext(params.view)
    val lightning = LightningContext(params.lighting)
    
    val sharedPolyBuffers = SharedPolyBuffers(gl)
    val faceBuffers = FaceBuffers(gl, sharedPolyBuffers)
    val edgeBuffers = EdgeBuffers(gl, sharedPolyBuffers)

    var prevPoly: Polyhedron? = null
    var prevDisplay: Display? = null

    init {
        setup()
    }

    override fun update() {
        onUpdate()
    }
}

fun DrawContext.drawScene(poly: Polyhedron) {
    val display = params.view.display.value
    if (poly != prevPoly || prevDisplay != display) {
        prevPoly = poly
        prevDisplay = display
        sharedPolyBuffers.initBuffers(poly)
        if (display.hasFaces()) faceBuffers.initBuffers(poly)
        if (display.hasEdges()) edgeBuffers.initBuffers(poly)
    }
    drawImpl(display)
}

private fun DrawContext.drawImpl(display: Display) {
    val width = gl.canvas.width
    val height = gl.canvas.height

    gl.viewport(0, 0, width, height)
    gl.clearColor(0.0f, 0.0f, 0.0f, 0.0f)

    if (params.view.transparent.value != 0.0 && display.hasFaces()) {
        gl.disable(GL.DEPTH_TEST)
    } else {
        gl.enable(GL.DEPTH_TEST)
        gl.clearDepth(1.0f)
        gl.depthFunc(GL.LEQUAL)
    }
    gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)

    view.initProjection(width, height)
    if (display.hasFaces()) faceBuffers.draw(view, lightning)
    if (display.hasEdges()) edgeBuffers.draw(view)
}




