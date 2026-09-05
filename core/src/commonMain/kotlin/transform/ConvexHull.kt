package polyhedra.core.transform

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import polyhedra.core.poly.compound
import polyhedra.core.poly.convexHull
import polyhedra.core.poly.isConvexGeometry
import polyhedra.model.api.*
import polyhedra.model.poly.*

/** One envelope around all members, optionally retained alongside the authoritative source. */
@Serializable
class ConvexHull(val combineOriginal: Boolean = false) : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.ConvexHull)
    override val tweaks: Map<TransformTweak, Double>
        get() = if (combineOriginal) mapOf(TransformTweak.CombineOriginal to 1.0) else emptyMap()
    override val support: TransformSupport
        get() = TransformSupport(outputPolicy = if (combineOriginal) {
            TransformOutputPolicy.RenderableImmersion
        } else TransformOutputPolicy.EmbeddedBoundary)

    override fun isIdentityTransform(poly: Polyhedron): Boolean =
        !combineOriginal && !poly.isCompound && poly.fs.all { it.isConvex } && poly.isConvexGeometry

    override fun transform(poly: Polyhedron): Polyhedron =
        if (isIdentityTransform(poly)) poly else finish(poly, convexHull(poly.vs))

    @Transient
    override val asyncTransform: AsyncTransform = { poly, progress ->
        if (isIdentityTransform(poly)) poly else finish(poly, convexHull(poly.vs, progress))
    }

    private fun finish(poly: Polyhedron, hull: Polyhedron): Polyhedron {
        val edges = hull.es.size + if (combineOriginal) poly.es.size else 0
        if (edges > MAX_POLYHEDRON_EDGES) throw TransformApplicabilityException(
            CoreIssueCode.TooLarge,
            "Convex hull result has $edges edges; limit is $MAX_POLYHEDRON_EDGES",
            FEV(
                hull.fs.size + if (combineOriginal) poly.fs.size else 0,
                edges,
                hull.vs.size + if (combineOriginal) poly.vs.size else 0,
            ),
        )
        return if (combineOriginal) compound(listOf(poly, hull)) else hull
    }

    override fun toString(): String = "Convex hull"
}
