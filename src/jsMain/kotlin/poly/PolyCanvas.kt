package polyhedra.js.poly

import kotlinx.browser.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import polyhedra.common.*
import polyhedra.js.*
import polyhedra.js.util.*
import react.*
import react.dom.*
import kotlin.math.*

external interface PolyCanvasProps : RProps {
    var classes: String?
    var poly: Polyhedron
    var style: PolyStyle
    var rotate: Boolean
    var viewScale: Double
    var onRotateChange: (Boolean) -> Unit
    var onScaleChange: (Double) -> Unit
}

fun RBuilder.polyCanvas(classes: String? = null, handler: PolyCanvasProps.() -> Unit) {
    child<PolyCanvasProps, PolyCanvas> {
        attrs { this.classes = classes }
        attrs(handler)
    }
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class PolyCanvas(props: PolyCanvasProps) : RPureComponent<PolyCanvasProps, RState>(props) {
    private val canvasRef = createRef<HTMLCanvasElement>()
    private lateinit var canvas: HTMLCanvasElement
    private lateinit var drawContext: DrawContext
    private var animationHandle = 0
    private var prevTime = Double.NaN
    private var prevX = 0.0
    private var prevY = 0.0

    override fun RBuilder.render() {
        canvas(props.classes) {
            attrs {
                width = "800"
                height = "600"
                ref = canvasRef
            }
        }
    }

    override fun componentDidMount() {
        canvas = canvasRef.current!!
        drawContext = DrawContext(canvas)
        canvas.onmousedown = this::handleMouseDown
        canvas.onmousemove = this::handleMouseMove
        draw()
        requestAnimation()
    }

    override fun componentWillUnmount() {
        cancelAnimation()
    }
    
    private fun requestAnimation() {
        if (!props.rotate) {
            cancelAnimation()
            return
        }
        if (animationHandle != 0) return
        animationHandle = window.requestAnimationFrame { nowTime ->
            animationHandle = 0
            if (prevTime.isNaN()) prevTime = nowTime
            val dt = (nowTime - prevTime) / 1000 // in seconds
            drawContext.viewParameters.rotate(dt * 0.6, dt * 0.9)
            draw()
            prevTime = nowTime
            requestAnimation()
        }
    }

    private fun cancelAnimation() {
        if (animationHandle == 0) return
        window.cancelAnimationFrame(animationHandle)
        animationHandle = 0
        prevTime = Double.NaN
    }

    override fun componentDidUpdate(prevProps: PolyCanvasProps, prevState: RState, snapshot: Any) {
        draw()
        requestAnimation()
    }

    private fun draw() {
        drawContext.viewParameters.viewScale = props.viewScale
        drawContext.drawScene(props.poly, props.style)
    }

    private fun savePrevMouseEvent(e: MouseEvent) {
        prevX = e.offsetX
        prevY = e.offsetY
    }

    private fun handleMouseDown(e: MouseEvent) {
        if (e.isLeftButtonPressed()) {
            savePrevMouseEvent(e)
            cancelAnimation()
            props.onRotateChange(false)
        }
    }

    private fun handleMouseMove(e: MouseEvent) {
        if (e.isLeftButtonPressed()) {
            val scale = 2 * PI / canvas.height
            drawContext.viewParameters.rotate((e.offsetX - prevX) * scale, (e.offsetY - prevY) * scale)
            savePrevMouseEvent(e)
            draw()
        }
    }
}