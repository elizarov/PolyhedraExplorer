package polyhedra.core.transform

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import polyhedra.model.api.TransformId
import polyhedra.model.api.TransformOperation
import polyhedra.model.api.CoreIssueCode
import polyhedra.model.poly.Polyhedron

/** Symmetric greatening: facet the polar dual, then reciprocate the selected closed result. */
@Serializable
class Greatened : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Greatened)

    @Transient
    override val support = TransformSupport(
        faceRequirement = FaceRequirement.Planar,
        topologyRequirement = TopologyRequirement.FacePlaneConstellation,
        outputPolicy = TransformOutputPolicy.RenderableImmersion,
    )

    override fun transform(poly: Polyhedron): Polyhedron = poly.greatened()

    @Transient
    override val asyncTransform: AsyncTransform = { poly, _ -> poly.greatenedAsync() }
}

/** Main-line stellation: add a complete next stratum of the source face-plane arrangement. */
@Serializable
class Stellated : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Stellated)

    @Transient
    override val support = TransformSupport(
        faceRequirement = FaceRequirement.Planar,
        topologyRequirement = TopologyRequirement.FacePlaneConstellation,
        outputPolicy = TransformOutputPolicy.RenderableImmersion,
    )

    override fun transform(poly: Polyhedron): Polyhedron = poly.stellated()

    @Transient
    override val asyncTransform: AsyncTransform = { poly, _ -> poly.stellatedAsync() }
}

fun Polyhedron.greatened(result: Int = 1): Polyhedron =
    stellationCandidates(ConstellationOperation.Greaten).selected("Greatening", result)

fun Polyhedron.stellated(result: Int = 1): Polyhedron =
    stellationCandidates(ConstellationOperation.Stellate).selected("Stellation", result)

internal suspend fun Polyhedron.greatenedAsync(result: Int = 1): Polyhedron =
    stellationCandidatesAsync(ConstellationOperation.Greaten).selected("Greatening", result)

internal suspend fun Polyhedron.stellatedAsync(result: Int = 1): Polyhedron =
    stellationCandidatesAsync(ConstellationOperation.Stellate).selected("Stellation", result)

private fun List<StellationCandidate>.selected(operation: String, result: Int): Polyhedron {
    if (result < 1) throw TransformApplicabilityException(
        CoreIssueCode.TransformNotApplicable,
        "$operation Result must be a positive integer",
    )
    return getOrNull(result - 1)?.poly
        ?: throw TransformApplicabilityException(
            CoreIssueCode.TransformNotApplicable,
            "$operation Result $result is unavailable; found $size result(s)",
        )
}
