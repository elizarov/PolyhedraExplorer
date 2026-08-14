package polyhedra.model.poly

import polyhedra.model.util.*
import kotlin.math.abs

class FaceRim(f: Face)  {
    val maxRim: Double
    val rimDir: List<Vec3>

    init {
        val n = f.size
        val ev = List(n) { i ->
            val j = (i + 1) % n
            f[j] - f[i]
        }
        // A non-planar face is inset in its average plane. Keeping all rim directions tangent to
        // that plane gives a stable, deterministic offset while preserving the original vertices'
        // out-of-plane coordinates.
        val projectedEv = ev.map { edge -> edge - f * (edge * f) }
        val projectedLength = projectedEv.map { edge -> edge.norm }
        val projectedEvu = projectedEv.mapIndexed { i, edge ->
            if (projectedLength[i] < EPS) Vec3.ZERO else edge / projectedLength[i]
        }
        val inward = projectedEvu.map { edge -> edge cross f }
        val cornerDenominator = List(n) { i ->
            val k = (i + n - 1) % n
            1.0 + inward[k] * inward[i]
        }

        if (projectedLength.any { it < EPS } || cornerDenominator.any { it < EPS }) {
            // A collapsed projected edge or a 180-degree corner has no unique finite inset.
            // Disable its rim safely instead of feeding infinities or NaNs to the renderer.
            maxRim = 0.0
            rimDir = List(n) { Vec3.ZERO }
        } else {
            // Intersect the two adjacent edge lines after each has moved inward by one unit.
            // Scaling this direction by r therefore keeps both adjacent inset edges exactly r
            // away from their original edges, including for scalene and non-tangential faces.
            rimDir = List(n) { i ->
                val k = (i + n - 1) % n
                (inward[k] + inward[i]) / cornerDenominator[i]
            }

            // The fixed-topology inset remains valid until its first shrinking edge collapses.
            val edgeCollapseLimit = (0 until n).mapNotNull { i ->
                val j = (i + 1) % n
                val shrinkRate = (rimDir[i] - rimDir[j]) * projectedEvu[i]
                if (shrinkRate > EPS) projectedLength[i] / shrinkRate else null
            }.minOrNull() ?: 0.0
            maxRim = safeInsetLimit(f, rimDir, edgeCollapseLimit)
        }
    }

    val borderNorm = List(f.size) { i ->
        val j = (i + 1) % f.size
        (f[j] cross f[i]).unit
    }
}

/** Intersects the face's edge lines after moving each edge inward by its own distance. */
fun Face.insetVertices(edgeInsets: List<Double>): List<Vec3> {
    require(edgeInsets.size == size)
    val projectedEdges = fvs.indices.map { index ->
        val vector = fvs[(index + 1) % size] - fvs[index]
        val projected = vector - this * (vector * this)
        require(projected.norm > EPS) { "Face $id has a collapsed projected edge" }
        projected.unit
    }
    val inward = projectedEdges.map { edge -> edge cross this }
    return fvs.indices.map { index ->
        val previous = (index + size - 1) % size
        val first = inward[previous]
        val second = inward[index]
        val cosine = first * second
        val denominator = 1.0 - cosine * cosine
        require(abs(denominator) > EPS) { "Face $id has no finite inset at vertex $index" }
        val firstWeight = (edgeInsets[previous] - cosine * edgeInsets[index]) / denominator
        val secondWeight = (edgeInsets[index] - cosine * edgeInsets[previous]) / denominator
        fvs[index] + first * firstWeight + second * secondWeight
    }
}

/**
 * Convex insets first fail when an edge collapses. A concave inset can fail earlier when a reflex
 * corner reaches another edge, so search the interval up to the first edge collapse and keep only
 * the initial simple-polygon range. This also works for a non-planar face because triangulation and
 * rim construction use the same average-plane projection.
 */
private fun safeInsetLimit(face: Face, directions: List<Vec3>, edgeCollapseLimit: Double): Double {
    if (!edgeCollapseLimit.isFinite() || edgeCollapseLimit <= EPS) return 0.0
    fun isValid(rim: Double): Boolean {
        val inset = face.fvs.mapIndexed { index, vertex -> vertex + directions[index] * rim }
        return runCatching { triangulateFace(inset, face) }.isSuccess
    }

    var previous = 0.0
    val samples = 64
    for (sample in 1..samples) {
        val candidate = edgeCollapseLimit * sample / samples
        if (isValid(candidate)) {
            previous = candidate
            continue
        }
        var valid = previous
        var invalid = candidate
        repeat(32) {
            val middle = (valid + invalid) / 2.0
            if (isValid(middle)) valid = middle else invalid = middle
        }
        return valid
    }
    return edgeCollapseLimit
}
