/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.params

import kotlinx.browser.*
import polyhedra.js.util.*

class AnimationTracker(private val rootParams: Param) {
    private var prevTime = Double.NaN
    private var animationHandle = 0

    fun start() {
        rootParams.onNotifyUpdated(Param.AnyUpdate, ::requestAnimationFrame)
        requestAnimationFrame()
    }

    private fun requestAnimationFrame() {
        if (animationHandle != 0) return
        animationHandle = window.requestAnimationFrame(animationFun)
    }

    private val animationFun: (Double) -> Unit = af@{ nowTime ->
        animationHandle = 0
        val dt = if (prevTime.isNaN()) 0.0 else (nowTime - prevTime) / 1000 // in seconds
        prevTime = nowTime
        rootParams.performUpdate(null, dt)
        if (animationHandle == 0) {
            // no further request
            prevTime = Double.NaN
        }
    }
}