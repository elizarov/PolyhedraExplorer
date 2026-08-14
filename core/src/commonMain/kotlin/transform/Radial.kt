package polyhedra.core.transform

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import polyhedra.model.api.TransformId
import polyhedra.model.api.TransformOperation
import polyhedra.model.api.TransformTweak
import polyhedra.model.poly.*
import polyhedra.model.util.EPS
import polyhedra.model.util.Vec3
import polyhedra.model.util.averagePlane
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

@Serializable
data class RadialVertex(
    val kind: VertexKind,
    val radius: Double = 1.0,
) : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Radial, target = kind)
    override val tweaks: Map<TransformTweak, Double>
        get() = mapOf(TransformTweak.Radius to radius)
    @Transient
    override val support = TransformSupport(
        faceRequirement = FaceRequirement.TargetSimplePlanar,
        outputPolicy = TransformOutputPolicy.RenderableImmersion,
        local = true,
    )

    override fun transform(poly: Polyhedron): Polyhedron = poly.radialVertices(kind, radius)
    override fun isApplicable(poly: Polyhedron): Boolean = poly.canMoveRadially(kind)
    override fun isIdentityTransform(poly: Polyhedron): Boolean = radius == 1.0
    override fun toString(): String = "Radial $kind"
}

@Serializable
data class StellateFace(
    val kind: FaceKind,
    val radius: Double = 1.0,
) : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.StellateFace, target = kind)
    override val tweaks: Map<TransformTweak, Double>
        get() = mapOf(TransformTweak.Radius to radius)
    @Transient
    override val support = TransformSupport(
        faceRequirement = FaceRequirement.TargetSimplePlanar,
        outputPolicy = TransformOutputPolicy.RenderableImmersion,
        local = true,
    )

    override fun transform(poly: Polyhedron): Polyhedron = poly.stellateFaces(kind, radius)
    override fun isApplicable(poly: Polyhedron): Boolean = poly.canStellateFaces(kind)
    override fun toString(): String = "Stellate $kind"
}

fun Polyhedron.canMoveRadially(kind: VertexKind): Boolean {
    val vertices = vs.filter { vertex -> vertex.kind == kind }
    if (vertices.isEmpty()) return false
    val radiusTolerance = EPS * circumradius
    return vertices.all { vertex ->
        vertex.x.isFinite() && vertex.y.isFinite() && vertex.z.isFinite() && vertex.norm > radiusTolerance &&
            vertex.directedEdges.none { edge -> edge.b.kind == kind } &&
            vertex.directedEdges.all { edge ->
                val face = edge.r
                face.size == 3 && face.isPlanar &&
                    !resolvedFaces[face.id].sourceBoundarySelfIntersects
            }
    }
}

fun Polyhedron.canStellateFaces(kind: FaceKind): Boolean = kind in stellatableFaceKinds()

fun Polyhedron.radialVertices(
    kind: VertexKind,
    radius: Double,
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null,
): Polyhedron {
    require(radius.isFinite() && radius > 0.0) { "Radial radius must be finite and positive" }
    require(canMoveRadially(kind)) { "Radial $kind requires isolated vertices with simple triangular neighbors" }
    if (radius == 1.0 && scale == null && forceFaceKinds == null) return this
    return transformedPolyhedron(
        RadialVertex::class,
        kind to radius,
        scale,
        forceFaceKinds,
    ) {
        for (source in vs) {
            vertex(if (source.kind == kind) source * radius else source, source.kind)
        }
        faces(fs)
        faceKindSources(faceKindSources)
    }
}

fun Polyhedron.stellateFaces(kind: FaceKind, radius: Double): Polyhedron {
    require(radius.isFinite() && radius > 0.0) { "Stellate-face radius must be finite and positive" }
    val kis = kisFacesWithApexKinds(setOf(kind))
    val apexKind = requireNotNull(kis.apexKinds[kind]) { "Kis did not create an apex orbit for $kind" }
    require(kis.poly.canMoveRadially(apexKind)) { "The Kis apex orbit for $kind is not radially movable" }
    return if (radius == 1.0) kis.poly else kis.poly.radialVertices(apexKind, radius)
}

/**
 * Finds positive radii where a triangle introduced by Stellate face becomes coplanar with another
 * output face. Each introduced triangle has two fixed base vertices and one radial apex, so every
 * coplanarity equation is at most quadratic in the radius. Candidate roots are validated against
 * the complete face planes to discard equations that align only one vertex.
 */
fun Polyhedron.stellateFaceCoplanarRadii(kind: FaceKind): List<Double> {
    val kis = kisFacesWithApexKinds(setOf(kind))
    val apexKind = requireNotNull(kis.apexKinds[kind])
    val poly = kis.poly
    val movingVertexIds = poly.vs.filter { vertex -> vertex.kind == apexKind }.mapTo(hashSetOf()) { it.id }
    val newTriangles = poly.fs
        .filter { face -> face.size == 3 && face.fvs.count { it.id in movingVertexIds } == 1 }
        .distinctBy { face -> face.kind }
    val algebraTolerance = EPS * max(poly.circumradius, 1.0).let { it * it * it } * 64.0
    val candidates = ArrayList<Double>()

    fun pointAt(vertex: Vertex, radius: Double): Vec3 =
        if (vertex.id in movingVertexIds) vertex * radius else vertex

    fun coplanarity(first: Face, point: Vertex, radius: Double): Double {
        val fixed = first.fvs.filter { vertex -> vertex.id !in movingVertexIds }
        val apex = first.fvs.single { vertex -> vertex.id in movingVertexIds }
        val u = fixed[0]
        val v = fixed[1]
        return (v - u) * ((apex * radius - u) cross (pointAt(point, radius) - u))
    }

    fun addCandidate(value: Double) {
        if (!value.isFinite() || value <= EPS * 64.0) return
        if (candidates.none { existing -> abs(existing - value) <= 1e-8 * max(1.0, value) }) {
            candidates += value
        }
    }

    fun coincides(first: Face, second: Face, radius: Double): Boolean {
        val firstPoints = first.fvs.map { vertex -> pointAt(vertex, radius) }
        val secondPoints = second.fvs.map { vertex -> pointAt(vertex, radius) }
        val firstPlane = firstPoints.averagePlane()
        val secondPlane = secondPoints.averagePlane()
        if (!firstPlane.d.isFinite() || !secondPlane.d.isFinite()) return false
        val coordinateScale = max(poly.circumradius * max(1.0, radius), 1.0)
        val planeTolerance = EPS * coordinateScale * 64.0
        if (firstPoints.any { point -> abs(firstPlane * point - firstPlane.d) > planeTolerance } ||
            secondPoints.any { point -> abs(secondPlane * point - secondPlane.d) > planeTolerance }
        ) return false
        val normalDot = firstPlane * secondPlane
        if (1.0 - abs(normalDot) > EPS * 64.0) return false
        val offsetDifference = if (normalDot >= 0.0) {
            abs(firstPlane.d - secondPlane.d)
        } else {
            abs(firstPlane.d + secondPlane.d)
        }
        return offsetDifference <= planeTolerance
    }

    fun addRoots(f0: Double, f1: Double, f2: Double, roots: MutableList<Double>) {
        val quadratic = (f2 - 2.0 * f1 + f0) / 2.0
        val linear = f1 - quadratic - f0
        if (abs(quadratic) <= algebraTolerance) {
            if (abs(linear) > algebraTolerance) roots += -f0 / linear
            return
        }
        val discriminant = linear * linear - 4.0 * quadratic * f0
        val discriminantTolerance = algebraTolerance * max(1.0, linear * linear)
        if (discriminant < -discriminantTolerance) return
        val rootDiscriminant = sqrt(max(0.0, discriminant))
        roots += (-linear - rootDiscriminant) / (2.0 * quadratic)
        if (rootDiscriminant > algebraTolerance) {
            roots += (-linear + rootDiscriminant) / (2.0 * quadratic)
        }
    }

    for (first in newTriangles) {
        val firstVertexIds = first.fvs.mapTo(hashSetOf()) { vertex -> vertex.id }
        for (second in poly.fs) {
            if (second.id == first.id) continue
            val roots = ArrayList<Double>()
            for (point in second.fvs) {
                if (point.id in firstVertexIds) continue
                val f0 = coplanarity(first, point, 0.0)
                val f1 = coplanarity(first, point, 1.0)
                val f2 = coplanarity(first, point, 2.0)
                addRoots(f0, f1, f2, roots)
            }
            for (radius in roots) {
                if (radius.isFinite() && radius > EPS * 64.0 && coincides(first, second, radius)) {
                    addCandidate(radius)
                }
            }
        }
    }
    return candidates.sorted()
}
