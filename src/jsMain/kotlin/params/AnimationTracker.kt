package polyhedra.js.params

import kotlinx.browser.*
import polyhedra.common.*
import kotlin.math.*

class AnimationTracker(override val params: Param) : Param.Context(Param.UpdateType.AnimationEffects) {
    private var prevTime = Double.NaN
    private var animationHandle = 0

    private var dirtyAnimationsList = true
    private val animationsList = ArrayList<Animation>()
    private var affectedDependencies: List<Param.Dependency> = emptyList()

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
        params.visitActiveAnimations { animationsList += it }
        affectedDependencies = animationsList.map { it.param }.collectAffectedDependencies(Param.UpdateType.ValueAnimation)
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
        if (dirtyAnimationsList) {
            rebuildAnimationsList()
        }
        animationHandle = 0
        if (prevTime.isNaN()) {
            prevTime = nowTime
            requestAnimation()
            return@af
        }
        val dt = (nowTime - prevTime) / 1000 // in seconds
        prevTime = nowTime
        animationsList.forEach { it.update(dt) }
        affectedDependencies.forEach { it.update() }
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