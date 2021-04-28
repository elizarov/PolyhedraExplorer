/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.poly

import polyhedra.common.poly.*
import polyhedra.common.transform.*

class Indicator<T>(
    val classes: String,
    val text: String,
    val tooltip: String
)

class IndicatorMessage<T>(
    val indicator: Indicator<T>,
    val value: T
)

operator fun <T> Indicator<T>.invoke(value: T) = IndicatorMessage(this, value)
operator fun Indicator<Unit>.invoke() = IndicatorMessage(this, Unit)

val TransformFailed = Indicator<Transform>("emoji", "❌", "{} Transformation has failed")
val SomeFacesNotPlanar = Indicator<Unit>("emoji", "⚠️", "Some faces are not planar, apply canonical transformation")
val FaceNotPlanar = Indicator<Unit>("emoji", "⚠️", "Face is not planar")
val TransformIsId = Indicator<Transform>("fa fa-recycle", "", "{} transformation is not doing anything here")
val TransformNotApplicable = Indicator<Transform>("emoji", "\uD83D\uDED1", "{} transformation is not applicable")
val TooLarge = Indicator<FEV>("fa fa-ban", "", "Polyhedron is too large to display ({})")