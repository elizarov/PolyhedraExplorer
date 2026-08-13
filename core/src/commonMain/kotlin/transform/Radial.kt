package polyhedra.core.transform

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import polyhedra.model.api.TransformId
import polyhedra.model.api.TransformOperation
import polyhedra.model.api.TransformTweak
import polyhedra.model.poly.*
import polyhedra.model.util.EPS
import polyhedra.model.util.norm
import polyhedra.model.util.times

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

fun Polyhedron.canStellateFaces(kind: FaceKind): Boolean = runCatching {
    val result = kisFacesWithApexKinds(setOf(kind))
    val apexKind = result.apexKinds.getValue(kind)
    result.poly.canMoveRadially(apexKind)
}.getOrDefault(false)

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
