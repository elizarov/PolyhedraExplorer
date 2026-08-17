package polyhedra.model.poly

import kotlinx.serialization.Serializable
import polyhedra.model.util.MutableVec3
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

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
)

/** Whether [point], projected in the common plane, belongs to this resolved rim union. */
fun ResolvedRimGeometry.containsProjected(
    point: Vec3,
    normal: Vec3,
    tolerance: Double,
): Boolean = regions.any { region ->
    val cycles = listOf(region.outer) + region.holes
    cycles.any { cycle -> cycle.isOnBoundary(point, tolerance) } ||
        (region.outer.windsAround(point, normal) &&
            region.holes.none { hole -> hole.windsAround(point, normal) })
}

private fun ResolvedRimCycle.isOnBoundary(point: Vec3, tolerance: Double): Boolean =
    vertices.indices.any { index ->
        val first = vertices[index] - point
        val second = vertices[(index + 1) % vertices.size] - point
        (first cross second).norm <= tolerance * maxOf(first.norm, second.norm, 1.0) &&
            first * second <= tolerance * tolerance
    }

private fun ResolvedRimCycle.windsAround(point: Vec3, normal: Vec3): Boolean {
    var winding = 0.0
    for (index in vertices.indices) {
        val first = vertices[index] - point
        val second = vertices[(index + 1) % vertices.size] - point
        winding += atan2((first cross second) * normal, first * second)
    }
    return abs(winding) > PI
}
