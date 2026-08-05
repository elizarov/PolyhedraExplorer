/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Canvas
import org.jetbrains.compose.web.dom.Div
import org.w3c.dom.*
import org.w3c.dom.events.*
import polyhedra.common.poly.Polyhedron
import polyhedra.common.util.norm
import polyhedra.js.params.Param
import polyhedra.js.util.ResizeTracker
import polyhedra.js.util.isLeftButtonEvent
import polyhedra.js.util.isLeftButtonPressed
import kotlin.math.PI
import kotlin.math.atan2

private const val MIN_MOUSE_MOVE_DISTANCE = 3.0

@Composable
fun PolyCanvas(
    classes: String? = null,
    params: RenderParams,
    @Suppress("UNUSED_PARAMETER") poly: Polyhedron,
    faceContextSink: (FaceContext) -> Unit,
    resetPopup: () -> Unit,
) {
    val controller = remember(params) { PolyCanvasController(params) }
    controller.faceContextSink = faceContextSink
    controller.resetPopup = resetPopup

    Canvas(attrs = {
        classes?.let { classes(*it.split(' ').toTypedArray()) }
        ref { canvas ->
            controller.mount(canvas)
            onDispose { controller.destroy() }
        }
    })
    Div(attrs = {
        classes("fps")
        ref { element ->
            controller.fpsElement = element
            onDispose { controller.fpsElement = null }
        }
    })
}

private class PolyCanvasController(private val params: RenderParams) {
    var faceContextSink: (FaceContext) -> Unit = {}
    var resetPopup: () -> Unit = {}
    var fpsElement: HTMLDivElement? = null

    private lateinit var canvas: HTMLCanvasElement
    private lateinit var drawContext: DrawContext
    private var mounted = false
    private var prevX = 0.0
    private var prevY = 0.0
    private var isRotating = false
    private val ongoingTouches = ArrayList<OngoingTouch>()
    private var prevDist = 0.0
    private var prevAngle = 0.0
    private var drawCount = 0
    private var fpsTimeout = 0

    fun mount(canvas: HTMLCanvasElement) {
        if (mounted) return
        mounted = true
        this.canvas = canvas
        canvas.onmousedown = ::handleMouseDown
        canvas.onmouseup = ::handleMouseUp
        canvas.onmousemove = ::handleMouseMove
        canvas.onwheel = ::handleWheel
        canvas.addTouchListener("touchstart", ::handleTouchStart)
        canvas.addTouchListener("touchend", ::handleTouchEnd)
        canvas.addTouchListener("touchcancel", ::handleTouchCancel)
        canvas.addTouchListener("touchmove", ::handleTouchMove)
        drawContext = DrawContext(canvas, params, ::draw)
        faceContextSink(drawContext.faces)
        ResizeTracker.add(requestRedraw)
        requestFpsTimeout()
        requestRedraw()
    }

    fun destroy() {
        if (!mounted) return
        mounted = false
        cancelFpsTimeout()
        ResizeTracker.remove(requestRedraw)
        drawContext.destroy()
    }

    private val requestRedraw: () -> Unit = {
        params.notifyUpdated(Param.TargetValue)
    }

    private fun draw() {
        resizeCanvasIfNeeded(canvas.clientWidth, canvas.clientHeight)
        drawContext.drawScene()
        drawCount++
    }

    private fun resizeCanvasIfNeeded(clientWidth: Int, clientHeight: Int) {
        val width = (clientWidth * window.devicePixelRatio).toInt()
        val height = (clientHeight * window.devicePixelRatio).toInt()
        if (canvas.width == width && canvas.height == height) return
        canvas.width = width
        canvas.height = height
    }

    private fun savePrevPointerLocation(x: Double, y: Double) {
        prevX = x
        prevY = y
    }

    private fun handlePointerDown(x: Double, y: Double) {
        savePrevPointerLocation(x, y)
        isRotating = false
    }

    private fun handlePointerUp() {
        if (!isRotating) resetPopup() else isRotating = false
    }

    private fun handlePointerMove(x: Double, y: Double, shift: Boolean) {
        if (!isRotating && norm(prevX - x, prevY - y) < MIN_MOUSE_MOVE_DISTANCE) return
        if (!isRotating) {
            isRotating = true
            params.animationParams?.animatedRotation?.updateValue(false)
            savePrevPointerLocation(x, y)
            return
        }
        val height = canvas.clientHeight
        val width = canvas.clientWidth
        if (shift) {
            val x1 = prevX - 0.5 * width
            val y1 = prevY - 0.5 * height
            val x2 = x - 0.5 * width
            val y2 = y - 0.5 * height
            val n1 = norm(x1, y1)
            val n2 = norm(x2, y2)
            rotate(0.0, 0.0, -atan2((x1 * y2 - y1 * x2) / n1 / n2, (x1 * x2 + y1 * y2) / n1 / n2))
        } else {
            val scale = 2 * PI / minOf(height, width)
            rotate((y - prevY) * scale, (x - prevX) * scale, 0.0)
        }
        savePrevPointerLocation(x, y)
    }

    private fun handleMouseDown(event: MouseEvent) {
        if (event.isLeftButtonEvent()) handlePointerDown(event.offsetX, event.offsetY)
    }

    private fun handleMouseUp(event: MouseEvent) {
        if (event.isLeftButtonEvent()) handlePointerUp()
    }

    private fun handleMouseMove(event: MouseEvent) {
        if (event.isLeftButtonPressed()) handlePointerMove(event.offsetX, event.offsetY, event.shiftKey)
    }

    private fun handleWheel(event: WheelEvent) {
        if (!event.ctrlKey) return
        event.preventDefault()
        scale(-event.deltaY / 50)
    }

    private fun scale(delta: Double) {
        val scale = params.view.scale
        scale.updateValue(scale.value + delta, Param.TargetValue)
    }

    private fun rotate(x: Double, y: Double, z: Double) {
        params.view.rotate.rotate(x, y, z, Param.TargetValue)
    }

    private inline fun withTouchMidpoint(handle: (Double, Double) -> Unit) {
        val count = ongoingTouches.size.coerceAtMost(2)
        if (count == 0) return
        var x = 0.0
        var y = 0.0
        repeat(count) {
            x += ongoingTouches[it].x
            y += ongoingTouches[it].y
        }
        handle(x / count, y / count)
    }

    private inline fun withTouchDistanceAngle(handle: (Double, Double) -> Unit) {
        if (ongoingTouches.size < 2) return
        val first = ongoingTouches[0]
        val second = ongoingTouches[1]
        val dx = (second.x - first.x).toDouble()
        val dy = (second.y - first.y).toDouble()
        handle(norm(dx, dy), atan2(dy, dx))
    }

    private fun handleTouchStart(event: TouchEvent) {
        event.preventDefault()
        event.withChangedTouches { touch ->
            ongoingTouches += OngoingTouch(touch.identifier, touch.clientX, touch.clientY)
            withTouchMidpoint(::handlePointerDown)
            withTouchDistanceAngle { distance, angle ->
                prevDist = distance
                prevAngle = angle
            }
        }
    }

    private fun handleTouchEnd(event: TouchEvent) {
        event.preventDefault()
        event.withChangedTouches { touch ->
            ongoingTouches.find { it.id == touch.identifier }?.let { ongoingTouches.remove(it) }
            if (ongoingTouches.isEmpty()) handlePointerUp()
            withTouchMidpoint(::savePrevPointerLocation)
        }
    }

    private fun handleTouchCancel(event: TouchEvent) {
        event.preventDefault()
    }

    private fun handleTouchMove(event: TouchEvent) {
        event.preventDefault()
        event.withChangedTouches { touch ->
            ongoingTouches.find { it.id == touch.identifier }?.let {
                it.x = touch.clientX
                it.y = touch.clientY
                withTouchMidpoint { x, y -> handlePointerMove(x, y, event.shiftKey) }
                withTouchDistanceAngle { distance, angle ->
                    scale((distance - prevDist) / 200)
                    rotate(0.0, 0.0, -(angle - prevAngle))
                    prevDist = distance
                    prevAngle = angle
                }
            }
        }
    }

    private fun requestFpsTimeout() {
        if (fpsTimeout == 0) fpsTimeout = window.setTimeout(fpsTick, 1000)
    }

    private val fpsTick: () -> Unit = {
        fpsElement?.textContent = if (drawCount == 0) "" else "$drawCount fps"
        fpsTimeout = 0
        drawCount = 0
        if (mounted) requestFpsTimeout()
    }

    private fun cancelFpsTimeout() {
        if (fpsTimeout != 0) window.clearTimeout(fpsTimeout)
        fpsTimeout = 0
    }
}

private fun HTMLCanvasElement.addTouchListener(type: String, handler: (TouchEvent) -> Unit) {
    @Suppress("UNCHECKED_CAST")
    addEventListener(type, handler as (Event) -> Unit)
}

private class OngoingTouch(val id: Int, var x: Int, var y: Int)

private inline fun TouchEvent.withChangedTouches(handle: (Touch) -> Unit) {
    for (index in 0 until changedTouches.length) changedTouches[index]?.let(handle)
}
