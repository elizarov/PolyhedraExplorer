package polyhedra.web.poly

import polyhedra.model.poly.ResolvedRimCycle
import polyhedra.model.poly.ResolvedRimRegion
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import polyhedra.model.util.unit
import kotlin.math.abs

@JsModule("earcut")
@JsNonModule
private external object EarcutModule {
    @JsName("default")
    fun triangulate(
        vertices: Array<Double>,
        holeIndices: Array<Int> = definedExternally,
        dimensions: Int = definedExternally,
    ): Array<Int>
}

internal data class TriangulatedRimRegion(
    val vertices: List<Vec3>,
    val triangles: List<Int>,
    val cycles: List<ResolvedRimCycle>,
    val normal: Vec3,
    val triangulationPatch: Boolean,
)

/** Triangulates one already-resolved planar region; no Boolean geometry is repeated here. */
internal fun ResolvedRimRegion.triangulate(normal: Vec3): TriangulatedRimRegion {
    val cycles = listOf(outer) + holes
    val vertices = cycles.flatMap(ResolvedRimCycle::vertices)
    val holes = Array(this.holes.size) { index ->
        cycles.take(index + 1).sumOf { cycle -> cycle.vertices.size }
    }
    val sourceNormal = normal.unit
    val planeNormal = if (triangulationPatch) {
        val origin = outer.vertices.first()
        val edge = outer.vertices[1] - origin
        val raw = outer.vertices.asSequence().drop(2)
            .map { point -> edge cross (point - origin) }
            .firstOrNull { candidate -> candidate.norm > 1e-12 }
        requireNotNull(raw) { "Non-planar rim patch has no finite plane" }
        val unit = raw.unit
        if (unit * sourceNormal >= 0.0) unit else unit * -1.0
    } else {
        sourceNormal
    }
    val dropAxis = when {
        abs(planeNormal.x) >= abs(planeNormal.y) && abs(planeNormal.x) >= abs(planeNormal.z) -> 0
        abs(planeNormal.y) >= abs(planeNormal.z) -> 1
        else -> 2
    }
    val coordinates = Array(vertices.size * 2) { 0.0 }
    for ((index, point) in vertices.withIndex()) {
        val (first, second) = when (dropAxis) {
            0 -> point.y to point.z
            1 -> point.x to point.z
            else -> point.x to point.y
        }
        coordinates[index * 2] = first
        coordinates[index * 2 + 1] = second
    }
    val triangles = EarcutModule.triangulate(coordinates, holes, 2).toList()
    require(triangles.size % 3 == 0) { "Rim triangulator returned an incomplete triangle" }
    require(triangles.all { index -> index in vertices.indices }) { "Rim triangulator returned an invalid index" }
    return TriangulatedRimRegion(vertices, triangles, cycles, planeNormal, triangulationPatch)
}
