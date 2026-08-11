/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import polyhedra.model.poly.FEV
import polyhedra.web.catalog.Transform

class Indicator<T>(
    val classes: String,
    val text: String,
    val tooltip: String,
)

class IndicatorMessage<T>(
    val indicator: Indicator<T>,
    val value: T,
)

operator fun <T> Indicator<T>.invoke(value: T) = IndicatorMessage(this, value)
operator fun Indicator<Unit>.invoke() = IndicatorMessage(this, Unit)

val TransformFailed = Indicator<Transform>("emoji", "❌", "{} transformation has failed")
val InvalidGeometry = Indicator<String>("emoji", "⚠️", "This setting produces invalid geometry: {}")
val SomeFacesNotPlanar = Indicator<Unit>("emoji", "⚠️", "Some faces are not planar, apply canonical transformation")
val FaceNotPlanar = Indicator<Unit>("emoji", "⚠️", "Face is not planar")
val TransformIsId = Indicator<Transform>("fa fa-recycle", "", "{} transformation is not doing anything here")
val TransformNotApplicable = Indicator<Transform>("emoji", "🛑", "{} transformation is not applicable")
val TooLarge = Indicator<FEV>("fa fa-ban", "", "Polyhedron is too large to display ({})")
