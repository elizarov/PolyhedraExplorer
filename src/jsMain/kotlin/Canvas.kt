package polyhedra.js

import kotlinx.browser.*
import org.khronos.webgl.*
import org.w3c.dom.*
import polyhedra.common.*
import react.*
import react.dom.*

external interface CanvasProps : RProps {
    var poly: Polyhedron
    var style: PolyStyle
}

external interface CanvasState : RState {
    var rotation: Double
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class Canvas(props: CanvasProps) : RComponent<CanvasProps, CanvasState>(props) {
    private val canvasRef = createRef<HTMLCanvasElement>()
    private lateinit var drawContext: DrawContext
    private var animationHandle = 0
    private var prevTime = Double.NaN

    override fun CanvasState.init(props: CanvasProps) {
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

    override fun componentDidUpdate(prevProps: CanvasProps, prevState: CanvasState, snapshot: Any) =
        draw()

    private fun draw() =
        drawContext.drawScene(props.poly, props.style, state)
}