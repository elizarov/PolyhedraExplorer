package polyhedra.js

import kotlinx.browser.*
import org.khronos.webgl.*
import org.w3c.dom.*
import polyhedra.common.*
import react.*
import react.dom.*

external interface GLCanvasProps : RProps {
    var poly: Polyhedron
    var style: PolyStyle
}

external interface GLCanvasState : RState {
    var rotation: Double
}

fun RBuilder.glCanvas(handler: GLCanvasProps.() -> Unit) {
    child<GLCanvasProps, GLCanvas> {
        attrs(handler)
    }
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class GLCanvas(props: GLCanvasProps) : RComponent<GLCanvasProps, GLCanvasState>(props) {
    private val canvasRef = createRef<HTMLCanvasElement>()
    private lateinit var drawContext: DrawContext
    private var animationHandle = 0
    private var prevTime = Double.NaN

    override fun GLCanvasState.init(props: GLCanvasProps) {
        rotation = 0.0
    }

    override fun RBuilder.render() {
        canvas {
            attrs {
                width = "640"
                height = "480"
                ref = canvasRef
            }
        }
    }

    override fun componentDidMount() {
        drawContext = DrawContext(canvasRef.current!!.getContext("webgl") as WebGLRenderingContext)
        draw()
        requestAnimation()
    }

    private fun requestAnimation() {
        animationHandle = window.requestAnimationFrame { nowTime ->
            if (prevTime.isNaN()) prevTime = nowTime
            val dt = (nowTime - prevTime) / 1000 // in seconds
            setState {
                rotation = state.rotation + dt
            }
            prevTime = nowTime
            requestAnimation()
        }
    }

    override fun componentWillUnmount() {
        window.cancelAnimationFrame(animationHandle)
    }

    override fun componentDidUpdate(prevProps: GLCanvasProps, prevState: GLCanvasState, snapshot: Any) =
        draw()

    private fun draw() =
        drawContext.drawScene(props.poly, props.style, state)
}