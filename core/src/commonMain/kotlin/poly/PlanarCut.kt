package polyhedra.core.poly

import polyhedra.model.util.*
import kotlin.math.abs

/** A line within a face plane, represented by a perpendicular cutting plane. */
internal data class PlanarCut(val normal: Vec3, val distance: Double)

/** Canonical line orientation and scale-aware deduplication shared with physical resolution. */
internal fun MutableList<PlanarCut>.addPlanarCut(
    planeNormal: Vec3, a: Vec3, b: Vec3, tolerance: Double,
) {
    val direction = b - a
    if (direction.norm <= tolerance) return
    var normal = (direction cross planeNormal).unit
    var distance = normal * a
    if (distance < -tolerance || abs(distance) <= tolerance &&
        listOf(normal.x, normal.y, normal.z).firstOrNull { abs(it) > EPS }?.let { it < 0.0 } == true
    ) {
        normal = normal * -1.0
        distance = -distance
    }
    if (none { (it.normal - normal).norm <= EPS * 32.0 && abs(it.distance - distance) <= tolerance }) {
        add(PlanarCut(normal, distance))
    }
}

internal fun List<Vec3>.partitionBy(cuts: List<PlanarCut>, tolerance: Double): List<List<Vec3>> {
    var polygons = listOf(this)
    for (cut in cuts.sortedWith(compareBy(PlanarCut::distance, { it.normal.x }, { it.normal.y }, { it.normal.z }))) {
        polygons = polygons.flatMap { polygon -> polygon.splitBy(cut, tolerance) }
    }
    return polygons
}

internal fun List<Vec3>.splitBy(cut: PlanarCut, tolerance: Double): List<List<Vec3>> {
    val distances = map { point -> cut.normal * point - cut.distance }
    if (distances.none { it > tolerance } || distances.none { it < -tolerance }) return listOf(this)

    fun clipped(positive: Boolean): List<Vec3> {
        val result = ArrayList<Vec3>()
        for (index in indices) {
            val a = this[index]
            val b = this[(index + 1) % size]
            val da = distances[index]
            val db = distances[(index + 1) % size]
            val aInside = if (positive) da >= -tolerance else da <= tolerance
            val bInside = if (positive) db >= -tolerance else db <= tolerance
            if (aInside) result += a
            if (aInside != bInside && abs(da - db) > tolerance) {
                result += a + (b - a) * (da / (da - db))
            }
        }
        return result.cleanPolygon(tolerance)
    }

    return listOf(clipped(true), clipped(false)).filter { it.size >= 3 }
}

private fun List<Vec3>.cleanPolygon(tolerance: Double): List<Vec3> {
    val result = ArrayList<Vec3>()
    for (point in this) {
        if (result.isEmpty() || (result.last() - point).norm > tolerance) result += point
    }
    if (result.size > 1 && (result.first() - result.last()).norm <= tolerance) result.removeLast()
    var changed = true
    while (changed && result.size > 3) {
        changed = false
        for (index in result.indices) {
            val previous = result[(index + result.size - 1) % result.size]
            val current = result[index]
            val next = result[(index + 1) % result.size]
            if (((current - previous) cross (next - current)).norm <= tolerance *
                maxOf((current - previous).norm, (next - current).norm)
            ) {
                result.removeAt(index)
                changed = true
                break
            }
        }
    }
    return result
}
