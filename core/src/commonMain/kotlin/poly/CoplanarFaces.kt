package polyhedra.core.poly

import polyhedra.core.transform.TransformCache
import polyhedra.model.api.MAX_POLYHEDRON_EDGES
import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.math.abs
import kotlin.math.floor

private object CoplanarFacesKey

/** Materializes only the final display mesh; transforms continue to consume source topology. */
fun Polyhedron.withCoplanarFaces(): Polyhedron {
    if (coplanarFaces.isNotEmpty()) return this
    TransformCache[this, CoplanarFacesKey]?.let { return it }
    val patches = computeCoplanarFaces()
    val result = if (patches.isEmpty()) this else {
        val vertices = vs.map { MutableVertex(it.id, it, it.kind) }
        val faces = fs.map { MutableFace(it.id, it.fvs.map { v -> vertices[v.id] }, it.kind) }
        Polyhedron(vertices, faces, faceKindSources, resolvedFaces, resolvedTopologyProvenance, patches)
    }
    TransformCache[this, CoplanarFacesKey] = Result.success(result)
    return result
}

private data class PlaneBucket(val x: Long, val y: Long, val z: Long, val d: Long)
private class FacePlaneGroup(val normal: Vec3, val distance: Double, val faces: MutableList<Face>)

private fun Polyhedron.coplanarGroups(tolerance: Double): List<FacePlaneGroup> {
    val angularTolerance = EPS * 32.0
    val buckets = hashMapOf<PlaneBucket, MutableList<FacePlaneGroup>>()
    val groups = arrayListOf<FacePlaneGroup>()
    for (face in fs) {
        if (!face.isPlanar) continue
        // Orient by the largest normal component, including planes passing through the origin.
        val components = listOf(face.x, face.y, face.z)
        val maximum = components.maxOf { abs(it) }
        val largest = components.first { abs(it) >= maximum - angularTolerance * 4.0 }
        val normal = if (largest < 0) -face else face
        val distance = normal * face.fvs.first()
        val key = PlaneBucket(floor(normal.x / angularTolerance).toLong(),
            floor(normal.y / angularTolerance).toLong(), floor(normal.z / angularTolerance).toLong(),
            floor(distance / tolerance).toLong())
        var group: FacePlaneGroup? = null
        search@ for (dx in -1L..1L) for (dy in -1L..1L) for (dz in -1L..1L) for (dd in -1L..1L) {
            group = buckets[PlaneBucket(key.x + dx, key.y + dy, key.z + dz, key.d + dd)]
                ?.firstOrNull { (it.normal - normal).norm <= angularTolerance &&
                    abs(it.distance - distance) <= tolerance }
            if (group != null) break@search
        }
        if (group == null) {
            group = FacePlaneGroup(normal, distance, arrayListOf())
            groups += group
            buckets.getOrPut(key, ::arrayListOf) += group
        }
        group.faces += face
    }
    return groups.filter { it.faces.size > 1 }
}

/** All triangles use a common line arrangement, so shared cells are geometrically identical. */
fun Polyhedron.computeCoplanarFaces(): List<CoplanarFacePatch> {
    val tolerance = maxOf(circumradius * EPS * 32.0, 1e-12)
    return coplanarGroups(tolerance).flatMap { group ->
        val polygons = group.faces.flatMap { face ->
            val resolved = resolvedFaces[face.id]
            resolved.triangles.map { triangle ->
                PlanarOverlaySource(listOf(triangle.a, triangle.b, triangle.c)
                    .map { resolved.vertices[it].position }, face.id)
            }
        }
        val patches = planarOverlay(polygons, group.normal, tolerance)
        if (patches.none { it.sourceFaceIds.size > 1 }) emptyList() else patches.map { patch ->
            val normal = (patch.vertices[1] - patch.vertices[0]) cross (patch.vertices[2] - patch.vertices[0])
            if (normal * fs[patch.sourceFaceIds.first()] < 0.0) {
                patch.copy(vertices = patch.vertices.asReversed())
            } else patch
        }
    }
}

internal data class PlanarOverlaySource(val vertices: List<Vec3>, val sourceId: Int)
private data class PointBucket(val x: Long, val y: Long, val z: Long)

/** Overlay of convex polygons in one plane; edge/point contacts never acquire overlap area. */
internal fun planarOverlay(
    polygons: List<PlanarOverlaySource>, normal: Vec3, tolerance: Double,
): List<CoplanarFacePatch> {
    val cuts = arrayListOf<PlanarCut>()
    for (polygon in polygons) for (i in polygon.vertices.indices) {
        cuts.addPlanarCut(normal, polygon.vertices[i], polygon.vertices[(i + 1) % polygon.vertices.size], tolerance)
    }
    val points = arrayListOf<MutableVec3>()
    val buckets = hashMapOf<PointBucket, MutableList<Int>>()
    fun pointId(point: Vec3): Int {
        val key = PointBucket(floor(point.x / tolerance).toLong(), floor(point.y / tolerance).toLong(),
            floor(point.z / tolerance).toLong())
        for (dx in -1L..1L) for (dy in -1L..1L) for (dz in -1L..1L) {
            for (id in buckets[PointBucket(key.x + dx, key.y + dy, key.z + dz)].orEmpty()) {
                if ((points[id] - point).norm <= tolerance) return id
            }
        }
        val id = points.size
        points += point.toMutableVec3()
        buckets.getOrPut(key, ::arrayListOf) += id
        return id
    }
    data class Cell(val cycle: List<Int>, val sources: MutableSet<Int>)
    val cells = linkedMapOf<List<Int>, Cell>()
    for (source in polygons) for (polygon in source.vertices.partitionBy(cuts, tolerance)) {
        val area2 = polygon.indices.sumOf { i ->
            (polygon[i] cross polygon[(i + 1) % polygon.size]) * normal
        }
        if (abs(area2) <= tolerance * tolerance) continue
        val ids = (if (area2 > 0) polygon else polygon.asReversed()).map(::pointId)
        if (ids.distinct().size != ids.size) continue
        cells.getOrPut(ids.sorted()) { Cell(ids, linkedSetOf()) }.sources += source.sourceId
        require(cells.size <= MAX_POLYHEDRON_EDGES * 8) { "Coplanar face arrangement exceeds the presentation limit" }
    }
    return cells.values.map { cell -> CoplanarFacePatch(cell.cycle.map(points::get), cell.sources.sorted()) }
}

/** Refines the same full-face cells by rim boundaries once, independent of visibility toggles. */
fun Polyhedron.coplanarRimFaces(rims: List<ResolvedRimGeometry>): List<CoplanarFacePatch> {
    if (coplanarFaces.isEmpty()) return emptyList()
    val byFace = rims.associateBy(ResolvedRimGeometry::sourceFaceId)
    val tolerance = maxOf(circumradius * EPS * 32.0, 1e-12)
    return coplanarFaces.flatMap { patch ->
        val normal = fs[patch.sourceFaceIds.first()]
        val cuts = arrayListOf<PlanarCut>()
        for (id in patch.sourceFaceIds) for (region in byFace[id]?.regions.orEmpty()) {
            for (cycle in listOf(region.outer) + region.holes) for (i in cycle.vertices.indices) {
                cuts.addPlanarCut(normal, cycle.vertices[i], cycle.vertices[(i + 1) % cycle.vertices.size], tolerance)
            }
        }
        patch.vertices.partitionBy(cuts, tolerance).map { polygon ->
            val center = polygon.reduce { a, b -> a + b } / polygon.size.toDouble()
            CoplanarFacePatch(polygon.map(Vec3::toMutableVec3), patch.sourceFaceIds,
                patch.sourceFaceIds.filter { byFace[it]?.containsProjected(center, normal, tolerance) == true })
        }
    }
}
