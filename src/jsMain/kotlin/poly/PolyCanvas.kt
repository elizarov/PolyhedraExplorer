package polyhedra.js.poly

import kotlinx.browser.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import polyhedra.common.*
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
                ref = canvasRef
            }
        }
    }

    private val drawFun = { draw() }

    override fun componentDidMount() {
        canvas = canvasRef.current
        canvas.onmousedown = this::handleMouseDown
        canvas.onmousemove = this::handleMouseMove
        DrawContext(canvas, props.params) {
            drawContext = it
            draw()
            requestAnimation()
        }
        ResizeTracker.add(drawFun)
    }

    override fun componentWillUnmount() {
        cancelAnimation()
        ResizeTracker.remove(drawFun)
        drawContext.destroy()
    }
    
    private fun requestAnimation() {
        if (!props.params.animation.rotate.value) {
            cancelAnimation()
            return
        }
        if (animationHandle != 0) return
        animationHandle = window.requestAnimationFrame { nowTime ->
            animationHandle = 0
            if (prevTime.isNaN()) prevTime = nowTime
            val dt = (nowTime - prevTime) / 1000 // in seconds
            val a = 2 * PI * props.params.animation.rotationAngle.value / 360
            drawContext.view.rotate(dt * cos(a), dt * sin(a))
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
        resizeCanvasIfNeeded(canvas.clientWidth, canvas.clientHeight)
        drawContext.drawScene(props.poly)
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
            cancelAnimation()
            props.params.animation.rotate.value = false
        }
    }

    private fun handleMouseMove(e: MouseEvent) {
        if (e.isLeftButtonPressed()) {
            val scale = 2 * PI / canvas.height
            drawContext.view.rotate((e.offsetX - prevX) * scale, (e.offsetY - prevY) * scale)
            savePrevMouseEvent(e)
            draw()
        }
    }
}