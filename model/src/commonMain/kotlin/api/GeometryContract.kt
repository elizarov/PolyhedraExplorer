package polyhedra.model.api

import kotlinx.serialization.Serializable

/** Existing transform results and all arrangement algorithms share this edge-count policy. */
const val MAX_POLYHEDRON_EDGES = (1 shl 15) - 1

/** Ordered guarantees supplied by a polyhedron geometry stage. */
@Serializable
enum class PolyhedronContract {
    AbstractSurface,
    RenderableImmersion,
    EmbeddedBoundary,
}

/** Supported zero-measure and transverse contacts in a renderable immersion. */
@Serializable
enum class SurfaceIntersectionClass {
    SelfCrossingFace,
    IntersectingFaces,
    SingularContact,
}

/** Compact worker-boundary summary; detailed arrangement records remain on the polyhedron. */
@Serializable
data class CoreGeometryAnalysis(
    val strongestContract: PolyhedronContract,
    val intersectionCounts: Map<SurfaceIntersectionClass, Int> = emptyMap(),
) {
    val hasIntersections: Boolean
        get() = intersectionCounts.values.any { it > 0 }
}

/** A point on one directed segment of an authoritative source-face boundary. */
@Serializable
data class SourceSegmentPoint(
    val sourceFaceId: Int,
    val sourceSegmentIndex: Int,
    val parameter: Double,
) {
    init {
        require(sourceFaceId >= 0)
        require(sourceSegmentIndex >= 0)
        require(parameter.isFinite() && parameter in 0.0..1.0)
    }
}

/** Serializable many-to-many source mapping shared by resolved vertices, edges, and faces. */
@Serializable
data class ResolvedElementProvenance(
    val sourceVertexIds: List<Int> = emptyList(),
    val sourceEdgeIds: List<Int> = emptyList(),
    val sourceFaceIds: List<Int> = emptyList(),
    val sourceCellIds: List<Int> = emptyList(),
    val sourceSegmentPoints: List<SourceSegmentPoint> = emptyList(),
)

/** Many-to-many source mapping for the final topology emitted by Resolve. */
@Serializable
data class ResolvedTopologyProvenance(
    val vertices: List<ResolvedElementProvenance>,
    val edges: List<ResolvedElementProvenance>,
    val faces: List<ResolvedElementProvenance>,
)
