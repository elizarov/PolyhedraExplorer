package polyhedra.js.poly

import kotlinext.js.*
import org.khronos.webgl.*
import org.w3c.dom.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.glsl.*
import polyhedra.js.params.*
import org.khronos.webgl.WebGLRenderingContext as GL

class DrawContext(
    canvas: HTMLCanvasElement,
    override val params: PolyParams,
    private val onUpdate: () -> Unit,
) : Param.Context() {
    val gl: GL = canvas.getContext("webgl", js {
        premultipliedAlpha = false  // Ask for non-premultiplied alpha
    } as Any) as GL

    val view = ViewContext(params.view)
    val lightning = LightningContext(params.lighting)
    
    val sharedPolyBuffers = SharedPolyBuffers(gl)
    val faceBuffers = FaceBuffers(gl, sharedPolyBuffers)
    val edgeBuffers = EdgeBuffers(gl, sharedPolyBuffers)

    var prevPoly: Polyhedron? = null
    var prevDisplay: Display? = null

    init {
        setup()
        initGL()
    }

    override fun update() {
        onUpdate()
    }
}

private fun DrawContext.initGL() {
    gl.blendFunc(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA);
    gl.depthFunc(GL.LEQUAL)
    gl.clearColor(0.0f, 0.0f, 0.0f, 0.0f)
    gl.clearDepth(1.0f)
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

    view.initProjection(width, height)
    gl.viewport(0, 0, width, height)
    gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)

    val transparentFaces = params.view.transparentFaces.animatedValue
    val hasFaces = display.hasFaces() && transparentFaces < 1.0
    val hasEdges = display.hasEdges()
    val transparent = transparentFaces != 0.0 && hasFaces
    gl[GL.DEPTH_TEST] = !transparent
    gl[GL.BLEND] = transparent
    if (transparent) {
        // special code for transparent faces - draw back faces, then front faces
        gl[GL.CULL_FACE] = true
        gl.cullFace(GL.FRONT)
        faceBuffers.draw(view, lightning)
        if (hasEdges) edgeBuffers.draw(view, 1)
        gl.cullFace(GL.BACK)
        faceBuffers.draw(view, lightning)
        if (hasEdges) edgeBuffers.draw(view, -1)
    } else {
        if (hasFaces) {
            // regular draw faces
            val solid = params.view.expandFaces.animatedValue == 0.0
            gl[GL.CULL_FACE] = solid // can cull faces when drawing solid
            faceBuffers.draw(view, lightning)
        }
        if (hasEdges) {
            edgeBuffers.draw(view)
        }
    }
}




