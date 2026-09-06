package polyhedra.core.transform

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import polyhedra.core.poly.validateRenderableImmersion
import polyhedra.core.poly.withGeometricKinds
import polyhedra.core.util.OperationProgressContext
import polyhedra.core.util.reportProgress
import polyhedra.core.util.subrange
import polyhedra.model.api.TransformId
import polyhedra.model.api.TransformOperation
import polyhedra.model.poly.Polyhedron
import kotlin.math.abs
import kotlin.math.roundToLong

/** Reconnects the current vertex positions into full-symmetry closed surfaces or compounds. */
@Serializable
class Faceted : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Faceted)

    @Transient
    override val support = TransformSupport(outputPolicy = TransformOutputPolicy.RenderableImmersion)

    override fun transform(poly: Polyhedron): Polyhedron = poly.faceted()

    @Transient
    override val asyncTransform: AsyncTransform = { poly, progress -> poly.facetedAsync(progress = progress) }
}

fun Polyhedron.faceted(result: Int = 1): Polyhedron =
    stellationCandidates(ConstellationOperation.Facet).selected("Faceting", result)

internal suspend fun Polyhedron.facetedAsync(
    result: Int = 1,
    progress: OperationProgressContext? = null,
): Polyhedron = stellationCandidatesAsync(ConstellationOperation.Facet, progress).selected("Faceting", result)

internal fun Polyhedron.buildGenericFacetingCandidates(
    tolerance: Double,
    progress: OperationProgressContext?,
): List<StellationCandidate> {
    val surfaces = enumerateSymmetricFacetings(tolerance, progress?.subrange(0, 70))
    val sourceKey = facetingSurfaceKey(tolerance)
    val candidates = surfaces.mapIndexedNotNull { index, surface ->
        val candidate = runCatching {
            surface.validateRenderableImmersion()
            if (!surface.hasIntegralOriginWinding() || surface.facetingSurfaceKey(tolerance) == sourceKey) {
                null
            } else StellationCandidate(surface.withGeometricKinds())
        }.getOrNull()
        progress?.subrange(70, 99)?.reportProgress(index + 1, surfaces.size)
        candidate
    }.distinctBy { it.poly.facetingSurfaceKey(tolerance) }
        .sortedWith(compareBy<StellationCandidate>(
            { abs(it.poly.fs.size - fs.size) },
            { abs(it.poly.es.size - es.size) },
            { it.poly.fs.size },
            { it.poly.es.size },
            { it.poly.facetingSurfaceKey(tolerance).toString() },
        ))
    progress?.reportProgress(100)
    return candidates
}

/** Faces, not just the wireframe: Great icosahedron and Stellated dodecahedron share their edges. */
private fun Polyhedron.facetingSurfaceKey(tolerance: Double): List<String> {
    val points = vs.map { point ->
        listOf(point.x, point.y, point.z).joinToString(",") { (it / tolerance).roundToLong().toString() }
    }
    return fs.map { face ->
        val circuit = face.fvs.map { points[it.id] }
        listOf(circuit, circuit.asReversed()).minOf { order ->
            order.indices.minOf { start ->
                order.indices.joinToString(";") { order[(start + it) % order.size] }
            }
        }
    }.sorted()
}
