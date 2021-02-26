package polyhedra.js.params

import kotlinx.browser.*
import polyhedra.common.*
import kotlin.math.*

class AnimationTracker(override val params: Param) : Param.Context(Param.UpdateType.ActiveAnimationsList) {
    private var prevTime = Double.NaN
    private var animationHandle = 0
    private val animations = ArrayList<Animation>()

    fun start() {
        setupAndUpdate()
    }

    override fun update() {
        animations.clear()
        params.visitActiveAnimations {
            animations += it
        }
        requestAnimation()
    }

    private fun requestAnimation() {
        if (animations.isEmpty()) {
            cancelAnimation()
            return
        }
        if (animationHandle != 0) return
        animationHandle = window.requestAnimationFrame(animationFun)
    }

    private val animationFun: (Double) -> Unit = af@{ nowTime ->
        animationHandle = 0
        if (prevTime.isNaN()) {
            prevTime = nowTime
            requestAnimation()
            return@af
        }
        val dt = (nowTime - prevTime) / 1000 // in seconds
        prevTime = nowTime
        for (animation in animations) {
            // :todo: move efficient impl for multiple animations
            animation.update(dt)
        }
        if (animations.any { it.isOver }) {
            update()
        } else {
            requestAnimation()
        }
    }

    private fun cancelAnimation() {
        prevTime = Double.NaN
        if (animationHandle == 0) return
        window.cancelAnimationFrame(animationHandle)
        animationHandle = 0
    }
}