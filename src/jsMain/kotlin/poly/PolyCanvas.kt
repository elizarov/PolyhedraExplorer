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
    private val ongoingTouches = ArrayList<OngoingTouch>()
    private var prevDist = 0.0
    private var prevAngle = 0.0

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
        canvas.addTouchListener("touchend", this::handleTouchEnd)
        canvas.addTouchListener("touchcancel", this::handleTouchCancel)
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

    private fun savePrevPointerLocation(x: Double, y: Double) {
        prevX = x
        prevY = y
    }

    private fun distanceFromPrevMouseEvent(x: Double, y: Double) =
        norm(prevX - x, prevY - y)

    private fun handlePointerDown(x: Double, y: Double) {
        savePrevPointerLocation(x, y)
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
            savePrevPointerLocation(x, y)
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
            rotate(0.0, 0.0, -a)
        } else {
            // x-y rotation
            val scale = 2 * PI / minOf(h, w)
            val dx = x - x1
            val dy = y - y1
            rotate(dy * scale, dx * scale, 0.0)
        }
        savePrevPointerLocation(x, y)
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
        scale(-e.deltaY / 50)
    }

    private fun scale(dScale: Double) {
        val scale = props.params.view.scale
        scale.updateValue(scale.value + dScale, Param.TargetValue)
    }

    private fun rotate(x: Double, y: Double, z: Double) {
        props.params.view.rotate.rotate(x, y, z, Param.TargetValue)
    }

    private inline fun withTouchMidpoint(handle: (x: Double, y: Double) -> Unit) {
        var x = 0.0
        var y = 0.0
        val n = ongoingTouches.size.coerceAtMost(2)
        for (i in 0 until n) {
            val t = ongoingTouches[i]
            x += t.x
            y += t.y
        }
        handle(x / n, y / n)
    }

    private inline fun withTouchDistanceAngle(handle: (dist: Double, angle: Double) -> Unit) {
        if (ongoingTouches.size < 2) return
        val t1 = ongoingTouches[0]
        val t2 = ongoingTouches[1]
        val dx = (t2.x - t1.x).toDouble()
        val dy = (t2.y - t1.y).toDouble()
        val dist = norm(dx, dy)
        val angle = atan2(dy, dx)
        handle(dist, angle)
    }

    private fun handleTouchStart(e: TouchEvent) {
        e.preventDefault()
        e.withChangedTouches { touch ->
            ongoingTouches += OngoingTouch(touch.identifier, touch.clientX, touch.clientY)
            withTouchMidpoint { x, y ->
                handlePointerDown(x, y)
            }
            withTouchDistanceAngle { dist, angle ->
                prevDist = dist
                prevAngle = angle
            }
        }
    }

    private fun handleTouchEnd(e: TouchEvent) {
        e.preventDefault()
        e.withChangedTouches { touch ->
            val t = ongoingTouches.find { it.id == touch.identifier }
            if (t != null) {
                ongoingTouches.remove(t)
                if (ongoingTouches.isEmpty()) {
                    handlePointerUp()
                }
                withTouchMidpoint { x, y ->
                    savePrevPointerLocation(x, y)
                }
            }
        }
    }

    private fun handleTouchCancel(e: TouchEvent) {
        e.preventDefault()
    }

    private fun handleTouchMove(e: TouchEvent) {
        e.preventDefault()
        e.withChangedTouches { touch ->
            val t = ongoingTouches.find { it.id == touch.identifier }
            if (t != null) {
                t.x = touch.clientX
                t.y = touch.clientY
                withTouchMidpoint { x, y ->
                    handlePointerMove(x, y, e.shiftKey)
                }
                withTouchDistanceAngle { dist, angle ->
                    val dDist = dist - prevDist
                    val dAngle = angle - prevAngle
                    scale(dDist / 200)
                    rotate(0.0, 0.0, -dAngle)
                    prevDist = dist
                    prevAngle = angle
                }
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

private class OngoingTouch(val id: Int, var x: Int, var y: Int)

private inline fun TouchEvent.withChangedTouches(handle: (Touch) -> Unit) {
    for (i in 0 until changedTouches.length) {
        handle(changedTouches[i]!!)
    }
}