/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import kotlinx.browser.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import polyhedra.common.poly.*
import polyhedra.js.params.*
import polyhedra.js.util.*
import react.*
import react.dom.*
import kotlin.math.*

external interface PolyCanvasProps : RProps {
    var classes: String?
    var poly: Polyhedron
    var params: RenderParams
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

    override fun componentDidMount() {
        canvas = canvasRef.current
        canvas.onmousedown = this::handleMouseDown
        canvas.onmousemove = this::handleMouseMove
        canvas.onwheel = this::handleWheel
        drawContext = DrawContext(canvas, props.params, ::draw)
        ResizeTracker.add(requestRedrawFun)
        requestFpsTimeout()
    }

    override fun componentWillUnmount() {
        cancelFpsTimeout()
        ResizeTracker.remove(requestRedrawFun)
        drawContext.destroy()
    }

    private val requestRedrawFun: () -> Unit = {
        props.params.notifyUpdated(Param.TargetValue)
    }

    private fun draw() {
        resizeCanvasIfNeeded(canvas.clientWidth, canvas.clientHeight)
        drawContext.drawScene()
        drawCount++
    }

    private fun resizeCanvasIfNeeded(clientWidth: Int, clientHeight: Int) {
        val dpr = window.devicePixelRatio
        val width = (clientWidth * dpr).toInt()
        val height = (clientHeight * dpr).toInt()
        if (canvas.width == width && canvas.height == height) return
        canvas.width = width
        canvas.height = height
    }

    private fun savePrevMouseEvent(e: MouseEvent) {
        prevX = e.offsetX
        prevY = e.offsetY
    }

    private fun handleMouseDown(e: MouseEvent) {
        if (e.isLeftButtonPressed()) {
            savePrevMouseEvent(e)
            props.params.animationParams?.animatedRotation?.updateValue(false)
        }
    }

    private fun handleMouseMove(e: MouseEvent) {
        if (e.isLeftButtonPressed()) {
            val scale = 2 * PI / minOf(canvas.height, canvas.width)
            props.params.view.rotate.rotate(
                (e.offsetY - prevY) * scale, (e.offsetX - prevX) * scale, 0.0, Param.TargetValue
            )
            savePrevMouseEvent(e)
        }
    }

    private fun handleWheel(e: WheelEvent) {
        if (!e.ctrlKey) return
        e.preventDefault()
        val dScale = e.deltaY / 100
        val scale = props.params.view.scale
        scale.updateValue(scale.value - dScale, Param.TargetValue)
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