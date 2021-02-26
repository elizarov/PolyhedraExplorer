package polyhedra.js.params

import kotlinx.browser.*
import polyhedra.common.*
import kotlin.math.*

class AnimationTracker(override val params: Param) : Param.Context(Param.UpdateType.AnimationEffects) {
    private var prevTime = Double.NaN
    private var animationHandle = 0

    private var dirtyAnimationsList = true
    private val animationsList = ArrayList<Animation>()
    private val affectedContexts = ArrayList<Param.Context>() // in reverse order

    fun start() {
        setupAndUpdate()
    }

    override fun update() {
        dirtyAnimationsList = true
        requestAnimation()
    }

    private fun rebuildAnimationsList() {
        dirtyAnimationsList = false
        animationsList.clear()
        affectedContexts.clear()
        val affectedThis = ArrayList<Param.Context>()
        val affectedSet = HashSet<Param.Context>()
        params.visitActiveAnimations { p, a ->
            animationsList += a
            p.visitAffectedContexts(Param.UpdateType.ValueAnimation) { affectedThis += it }
            // the last affected should be root, start from it
            for (i in affectedThis.size - 1 downTo 0) {
                val c = affectedThis[i]
                if (affectedSet.add(c)) affectedContexts += c
            }
            affectedThis.clear()
        }
    }

    private fun requestAnimation() {
        if (animationsList.isEmpty() && !dirtyAnimationsList) {
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
        if (dirtyAnimationsList) {
            rebuildAnimationsList()
        }
        for (animation in animationsList) {
            animation.update(dt)
        }
        for (i in affectedContexts.size - 1 downTo 0) {
            affectedContexts[i].update()
        }
        if (animationsList.any { it.isOver }) {
            dirtyAnimationsList = true
        }
        requestAnimation()
    }

    private fun cancelAnimation() {
        prevTime = Double.NaN
        if (animationHandle == 0) return
        window.cancelAnimationFrame(animationHandle)
        animationHandle = 0
    }
}