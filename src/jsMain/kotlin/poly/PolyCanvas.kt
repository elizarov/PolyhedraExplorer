/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import kotlinx.browser.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import polyhedra.common.poly.*
import polyhedra.common.util.*
import polyhedra.js.params.*
import polyhedra.js.util.*
import react.*
import react.dom.*
import kotlin.math.*

external interface PolyCanvasProps : RProps {
    var classes: String?
    var poly: Polyhedron
    var params: RenderParams
    var faceContextSink: (FaceContext) -> Unit
    var resetPopup: () -> Unit
}

fun RBuilder.polyCanvas(classes: String? = null, handler: PolyCanvasProps.() -> Unit) {
    child<PolyCanvasProps, PolyCanvas> {
        attrs {
            this.classes = classes
            handler()
        }
    }
}

private const val MIN_MOUSE_MOVE_DISTANCE = 3.0

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class PolyCanvas(props: PolyCanvasProps) : RPureComponent<PolyCanvasProps, RState>(props) {
    private val fpsRef = createRef<HTMLDivElement>()
    private lateinit var canvas: HTMLCanvasElement
    private lateinit var drawContext: DrawContext
    private var prevX = 0.0
    private var prevY = 0.0
    private var isRotating = false
    private var currentTouchId: Int? = null

    private val canvasRef = createRef<HTMLCanvasElement>()
    private var drawCount = 0
    private var fpsTimeout = 0

    override fun RBuilder.render() {
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

    override fun componentDidMount() {
        canvas = canvasRef.current
        canvas.onmousedown = this::handleMouseDown
        canvas.onmouseup = this::handleMouseUp
        canvas.onmousemove = this::handleMouseMove
        canvas.onwheel = this::handleWheel
        canvas.addTouchListener("touchstart", this::handleTouchStart)
        canvas.addTouchListener("touchend", this::handleTouchEndOrCancel)
        canvas.addTouchListener("touchcancel", this::handleTouchEndOrCancel)
        canvas.addTouchListener("touchmove", this::handleTouchMove)
        drawContext = DrawContext(canvas, props.params, ::draw)
        props.faceContextSink(drawContext.faces)
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

    private fun savePrevPointerEvent(x: Double, y: Double) {
        prevX = x
        prevY = y
    }

    private fun distanceFromPrevMouseEvent(x: Double, y: Double) =
        norm(prevX - x, prevY - y)

    private fun handlePointerDown(x: Double, y: Double) {
        savePrevPointerEvent(x, y)
        isRotating = false
    }

    private fun handlePointerUp() {
        if (!isRotating) {
            props.resetPopup()
        } else {
            isRotating = false
        }
    }

    private fun handlePointerMove(x: Double, y: Double, shift: Boolean) {
        if (!isRotating && distanceFromPrevMouseEvent(x, y) < MIN_MOUSE_MOVE_DISTANCE) return
        if (!isRotating) {
            isRotating = true
            props.params.animationParams?.animatedRotation?.updateValue(false)
            savePrevPointerEvent(x, y)
            return
        }
        val h = canvas.clientHeight
        val w = canvas.clientWidth
        // prev pos
        val x1 = prevX
        val y1 = prevY
        if (shift) {
            // z-rotation
            // center pos
            val x0 = 0.5 * w
            val y0 = 0.5 * h
            // prev pos from center
            val x10 = x1 - x0
            val y10 = y1 - y0
            val n10 = norm(x10, y10)
            // new pos from center
            val x20 = x - x0
            val y20 = y - y0
            val n20 = norm(x20, y20)
            // compute rotation angle
            val sin = (x10 * y20 - y10 * x20) / n10 / n20
            val cos = (x10 * x20 + y10 * y20) / n10 / n20
            val a = atan2(sin, cos)
            props.params.view.rotate.rotate(0.0, 0.0, -a, Param.TargetValue)
        } else {
            // x-y rotation
            val scale = 2 * PI / minOf(h, w)
            val dx = x - x1
            val dy = y - y1
            props.params.view.rotate.rotate(dy * scale, dx * scale, 0.0, Param.TargetValue)
        }
        savePrevPointerEvent(x, y)
    }

    private fun handleMouseDown(e: MouseEvent) {
        if (!e.isLeftButtonEvent()) return
        handlePointerDown(e.offsetX, e.offsetY)
    }

    private fun handleMouseUp(e: MouseEvent) {
        if (!e.isLeftButtonEvent()) return
        handlePointerUp()
    }

    private fun handleMouseMove(e: MouseEvent) {
        if (!e.isLeftButtonPressed()) return
        handlePointerMove(e.offsetX, e.offsetY, e.shiftKey)
    }

    private fun handleWheel(e: WheelEvent) {
        if (!e.ctrlKey) return
        e.preventDefault()
        val dScale = e.deltaY / 50
        val scale = props.params.view.scale
        scale.updateValue(scale.value - dScale, Param.TargetValue)
    }

    private fun handleTouchStart(e: TouchEvent) {
        e.preventDefault()
        if (currentTouchId != null) return
        val touch = e.touches[0]!!
        currentTouchId = touch.identifier
        handlePointerDown(touch.clientX.toDouble(), touch.clientY.toDouble())
    }

    private fun handleTouchEndOrCancel(e: TouchEvent) {
        e.preventDefault()
        if (currentTouchId == null) return
        for (i in 0 until e.touches.length) {
            val touch = e.touches[i]!!
            if (touch.identifier == currentTouchId) {
                currentTouchId = null
                handlePointerUp()
                return
            }
        }
    }

    private fun handleTouchMove(e: TouchEvent) {
        e.preventDefault()
        for (i in 0 until e.touches.length) {
            val touch = e.touches[i]!!
            if (touch.identifier == currentTouchId) {
                handlePointerMove(touch.clientX.toDouble(), touch.clientY.toDouble(), e.shiftKey)
                return
            }
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

private fun HTMLCanvasElement.addTouchListener(type: String, handler: (TouchEvent) -> Unit) {
    @Suppress("UNCHECKED_CAST")
    addEventListener(type, handler as (Event) -> Unit)
}