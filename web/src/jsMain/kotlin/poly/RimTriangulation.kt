package polyhedra.web.poly

import polyhedra.model.poly.ResolvedRimCycle
import polyhedra.model.poly.ResolvedRimRegion
import polyhedra.model.util.Vec3
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
)

/** Triangulates one already-resolved planar region; no Boolean geometry is repeated here. */
internal fun ResolvedRimRegion.triangulate(normal: Vec3): TriangulatedRimRegion {
    val cycles = listOf(outer) + holes
    val vertices = cycles.flatMap(ResolvedRimCycle::vertices)
    val holes = Array(this.holes.size) { index ->
        cycles.take(index + 1).sumOf { cycle -> cycle.vertices.size }
    }
    val dropAxis = when {
        abs(normal.x) >= abs(normal.y) && abs(normal.x) >= abs(normal.z) -> 0
        abs(normal.y) >= abs(normal.z) -> 1
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
    return TriangulatedRimRegion(vertices, triangles, cycles)
}
