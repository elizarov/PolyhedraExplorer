/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.common.poly.*
import polyhedra.common.transform.*

class Indicator<T>(
    val text: String,
    val tooltip: String
)

class IndicatorMessage<T>(
    val indicator: Indicator<T>,
    val value: T
)

operator fun <T> Indicator<T>.invoke(value: T) = IndicatorMessage(this, value)
operator fun Indicator<Unit>.invoke() = IndicatorMessage(this, Unit)

val TransformFailed = Indicator<Transform>("❌", "{} Transformation has failed")
val SomeFacesNotPlanar = Indicator<Unit>("⚠️", "Some faces are not planar, apply canonical transformation")
val FaceNotPlanar = Indicator<Unit>("⚠️", "Face is not planar")
val TransformIsId = Indicator<Transform>("♻️", "{} transformation is not doing anything here")
val TransformNotApplicable = Indicator<Transform>("\uD83D\uDED1", "{} transformation is not applicable")
val TooLarge = Indicator<FEV>("\uD83D\uDCA3", "Polyhedron is too large to display ({})")