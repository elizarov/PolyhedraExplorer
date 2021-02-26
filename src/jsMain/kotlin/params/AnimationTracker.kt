package polyhedra.js.params

import kotlinx.browser.*
import polyhedra.common.*
import kotlin.math.*

class AnimationTracker(override val params: Param) : Param.Context(Param.UpdateType.ActiveAnimationsList) {
    private var prevTime = Double.NaN
    private var animationHandle = 0
    private val animations = ArrayList<Animation>()
    private val affectedContexts = ArrayList<Param.Context>() // in reverse order

    fun start() {
        setupAndUpdate()
    }

    override fun update() {
        animations.clear()
        affectedContexts.clear()
        val affectedThis = ArrayList<Param.Context>()
        val affectedSet = HashSet<Param.Context>()
        params.visitActiveAnimations { p, a ->
            animations += a
            p.visitAffectedContexts(Param.UpdateType.ValueAnimation) { affectedThis += it }
            // the last affected should be root, start from it
            for (i in affectedThis.size - 1 downTo 0) {
                val c = affectedThis[i]
                if (affectedSet.add(c)) affectedContexts += c
            }
            affectedThis.clear()
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
            animation.update(dt)
        }
        for (i in affectedContexts.size - 1 downTo 0) {
            affectedContexts[i].update()
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