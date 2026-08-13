package polyhedra.core.transform

import polyhedra.model.api.PolyhedronContract
import polyhedra.model.poly.Polyhedron
import polyhedra.model.util.EPS
import kotlin.math.abs

/** Geometric face data that an operation reads from its authoritative input surface. */
enum class FaceRequirement {
    Any,
    Planar,
    NonSingularPlanar,
    SimplePlanar,
    TargetSimplePlanar,
}

/** Additional topological construction assumed by an operation. */
enum class TopologyRequirement {
    OrientedMap,
    FacePlanes,
    CanonicalizableSphere,
    LocalDisk,
    PlanarArrangement,
    FacePlaneConstellation,
}

enum class TransformOutputPolicy {
    Preserve,
    RenderableImmersion,
    EmbeddedBoundary,
}

/** Machine-readable operation domain; individual target checks remain operation-specific. */
data class TransformSupport(
    val inputContract: PolyhedronContract = PolyhedronContract.RenderableImmersion,
    val faceRequirement: FaceRequirement = FaceRequirement.Any,
    val topologyRequirement: TopologyRequirement = TopologyRequirement.OrientedMap,
    val outputPolicy: TransformOutputPolicy = TransformOutputPolicy.EmbeddedBoundary,
    val local: Boolean = false,
)

data class TransformApplicability(
    val support: TransformSupport,
    val outputContract: PolyhedronContract,
    val rejectionReason: String? = null,
) {
    val isApplicable: Boolean
        get() = rejectionReason == null
}

internal fun Polyhedron.hasNonSingularFacePlanes(): Boolean {
    val tolerance = EPS * circumradius
    return fs.all { face -> face.isPlanar && abs(face.d) > tolerance }
}
