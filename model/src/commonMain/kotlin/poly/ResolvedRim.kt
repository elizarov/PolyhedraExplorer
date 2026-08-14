package polyhedra.model.poly

import kotlinx.serialization.Serializable
import polyhedra.model.util.MutableVec3

/** One directed occurrence in an authoritative source-face boundary. */
@Serializable
data class SourceEdgeOccurrence(
    val sourceFaceId: Int,
    val sourceSegmentIndex: Int,
)

/** One oriented rim boundary; outer cycles are CCW and hole cycles are clockwise. */
@Serializable
data class ResolvedRimCycle(
    val vertices: List<MutableVec3>,
    /** Sources of the segment beginning at the corresponding vertex. */
    val segmentSources: List<List<SourceEdgeOccurrence>>,
) {
    init {
        require(vertices.size >= 3)
        require(segmentSources.size == vertices.size)
    }
}

/** One connected rim region with one outer cycle and zero or more holes. */
@Serializable
data class ResolvedRimRegion(
    val outer: ResolvedRimCycle,
    val holes: List<ResolvedRimCycle> = emptyList(),
    val sourceEdges: List<SourceEdgeOccurrence>,
    /** True when the region is a clipped patch of one non-planar face triangle. */
    val triangulationPatch: Boolean = false,
)

/** A zero-width source-boundary cover used by a higher-winding hidden face. */
@Serializable
data class ResolvedRimWall(
    val a: MutableVec3,
    val b: MutableVec3,
    val sourceEdges: List<SourceEdgeOccurrence>,
)

/** Polygonal hidden-face rim geometry. Triangulation deliberately belongs to each consumer. */
@Serializable
data class ResolvedRimGeometry(
    val sourceFaceId: Int,
    val sourceFaceKind: FaceKind,
    /** Width actually used after clamping to [maximumWidth]. */
    val width: Double,
    val regions: List<ResolvedRimRegion>,
    /** Largest selectable width before the rim completely covers the source-face fill. */
    val maximumWidth: Double = width,
    /** Flat source-boundary covers that have thickness but no top-surface rim. */
    val boundaryWalls: List<ResolvedRimWall> = emptyList(),
)
