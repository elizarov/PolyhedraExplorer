package polyhedra.js.poly

import kotlinx.browser.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import polyhedra.common.*
import polyhedra.js.params.*
import polyhedra.js.util.*
import react.*
import react.dom.*
import kotlin.math.*

external interface PolyCanvasProps : RProps {
    var classes: String?
    var poly: Polyhedron
    var params: PolyParams
}

fun RBuilder.polyCanvas(classes: String? = null, handler: PolyCanvasProps.() -> Unit) {
    child<PolyCanvasProps, PolyCanvas> {
        attrs {
            this.classes = classes
            handler()
        }
    }
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class PolyCanvas(props: PolyCanvasProps) : RPureComponent<PolyCanvasProps, RState>(props) {
    private val fpsRef = createRef<HTMLDivElement>()
    private lateinit var canvas: HTMLCanvasElement
    private lateinit var drawContext: DrawContext
    private var prevX = 0.0
    private var prevY = 0.0

    private val canvasRef = createRef<HTMLCanvasElement>()
    private var drawCount = 0
    private var fpsTimeout = 0

    override fun RBuilder.render() {
        div("fps-container") {
            canvas(props.classes) {
                attrs {
                    ref = canvasRef
                }
            }
            div("fps") {
                attrs {
                    ref = fpsRef
                }
            }
        }
    }

    private val drawFun = { draw() }

    override fun componentDidMount() {
        canvas = canvasRef.current
        canvas.onmousedown = this::handleMouseDown
        canvas.onmousemove = this::handleMouseMove
        drawContext = DrawContext(canvas, props.params, drawFun)
        ResizeTracker.add(drawFun)
        draw()
        requestFpsTimeout()
    }

    override fun componentWillUnmount() {
        cancelFpsTimeout()
        ResizeTracker.remove(drawFun)
        drawContext.destroy()
    }

    override fun componentDidUpdate(prevProps: PolyCanvasProps, prevState: RState, snapshot: Any) {
        draw()
    }

    private fun draw() {
        resizeCanvasIfNeeded(canvas.clientWidth, canvas.clientHeight)
        drawContext.drawScene(props.poly)
        drawCount++
    }

    private fun resizeCanvasIfNeeded(clientWidth: Int, clientHeight: Int) {
        if (canvas.width == clientWidth && canvas.height == clientHeight) return
        canvas.width = clientWidth
        canvas.height = clientHeight
    }

    private fun savePrevMouseEvent(e: MouseEvent) {
        prevX = e.offsetX
        prevY = e.offsetY
    }

    private fun handleMouseDown(e: MouseEvent) {
        if (e.isLeftButtonPressed()) {
            savePrevMouseEvent(e)
            props.params.animation.animatedRotation.updateValue(false)
        }
    }

    private fun handleMouseMove(e: MouseEvent) {
        if (e.isLeftButtonPressed()) {
            val scale = 2 * PI / minOf(canvas.height, canvas.width)
            props.params.view.rotate.rotate(
                (e.offsetY - prevY) * scale, (e.offsetX - prevX) * scale, 0.0, Param.UpdateType.ValueUpdate
            )
            savePrevMouseEvent(e)
        }
    }

    private fun requestFpsTimeout() {
        if (fpsTimeout != 0) return
        fpsTimeout = window.setTimeout(fpsTimeoutFun, 1000)
    }

    private val fpsTimeoutFun: () -> Unit = {
        fpsRef.current.textContent = if (drawCount == 0) "" else "$drawCount fps"
        fpsTimeout = 0
        drawCount = 0
        requestFpsTimeout()
    }

    private fun cancelFpsTimeout() {
        if (fpsTimeout == 0) return
        window.clearTimeout(fpsTimeout)
        fpsTimeout = 0
    }
}