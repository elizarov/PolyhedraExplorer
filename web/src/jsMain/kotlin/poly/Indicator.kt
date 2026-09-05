/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import polyhedra.model.api.CoreGeometryAnalysis
import polyhedra.model.api.SurfaceIntersectionClass
import polyhedra.model.poly.FEV
import polyhedra.web.catalog.Transform

class Indicator<T>(
    val classes: String,
    val text: String,
    val tooltip: String,
    val symbol: IndicatorSymbol? = null,
)

enum class IndicatorSymbol { Warning, Pentagram }

class IndicatorMessage<T>(
    val indicator: Indicator<T>,
    val value: T,
)

operator fun <T> Indicator<T>.invoke(value: T) = IndicatorMessage(this, value)
operator fun Indicator<Unit>.invoke() = IndicatorMessage(this, Unit)

val TransformFailed = Indicator<Transform>("emoji", "❌", "{} transformation has failed")
val InvalidGeometry = Indicator<String>("", "", "This setting produces invalid geometry: {}", IndicatorSymbol.Warning)
val SomeFacesNotPlanar = Indicator<Unit>("", "", "Some faces are not planar, apply canonical transformation", IndicatorSymbol.Warning)
val FaceNotPlanar = Indicator<Unit>("", "", "Face is not planar", IndicatorSymbol.Warning)
val TransformIsId = Indicator<Transform>("fa fa-recycle", "", "{} transformation is not doing anything here")
val TransformNotApplicable = Indicator<Transform>("emoji", "🛑", "{} transformation is not applicable")
val TooLarge = Indicator<FEV>("fa fa-ban", "", "Polyhedron is too large to display ({})")
val ImmersedSurface = Indicator<String>("", "", "{}. Click to add Resolved", IndicatorSymbol.Pentagram)

fun CoreGeometryAnalysis.toIntersectionIndicatorOrNull(): IndicatorMessage<String>? {
    if (!hasIntersections) return null
    val details = buildList {
        intersectionCounts[SurfaceIntersectionClass.SelfCrossingFace]?.takeIf { it > 0 }?.let {
            add("Self-crossing source-face contacts: $it")
        }
        intersectionCounts[SurfaceIntersectionClass.IntersectingFaces]?.takeIf { it > 0 }?.let {
            add("Crossings between face surfaces: $it")
        }
        intersectionCounts[SurfaceIntersectionClass.SingularContact]?.takeIf { it > 0 }?.let {
            add("Singular surface contacts: $it")
        }
    }
    return ImmersedSurface(details.joinToString("; "))
}
