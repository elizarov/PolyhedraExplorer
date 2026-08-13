package polyhedra.model.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import polyhedra.model.poly.FEV
import polyhedra.model.poly.Polyhedron

@Serializable
data class CoreState(
    val seedTag: String,
    val transformTags: List<String>,
    val scaleTag: String,
)

@Serializable
data class CoreRequest(
    val state: CoreState,
    val previousState: CoreState? = null,
    val animationDuration: Double? = null,
    val detectSeed: Boolean = false,
    val calculateTweakRanges: Boolean = true,
    /** Presentation-space rim width; omitted when no polygonal rims are requested. */
    val rimWidth: Double? = null,
)

data class CoreProgress(
    val transformIndex: Int,
    val done: Int,
)

@Serializable
enum class CoreIssueCode {
    TransformFailed,
    InvalidGeometry,
    TransformNotApplicable,
    TransformIsIdentity,
    TooLarge,
    SomeFacesNotPlanar,
    GeometryContractNotSatisfied,
    SelfIntersection,
    NonPlanarSelfIntersection,
    DisconnectedMaterial,
    ScaleNotApplicable,
}

@Serializable
data class CoreIssue(
    val code: CoreIssueCode,
    val transformTag: String? = null,
    val fev: FEV? = null,
    val detail: String? = null,
    val requiredContract: PolyhedronContract? = null,
    val actualContract: PolyhedronContract? = null,
)

@Serializable
data class CoreAnimationStep(
    val duration: Double,
    val previousPoly: Polyhedron,
    val previousFraction: Double,
    val targetPoly: Polyhedron,
    val targetFraction: Double,
)

@Serializable
data class CoreTransformTweakSnap(
    val label: String,
    val value: Double,
)

@Serializable
data class CoreTransformTweakOption(
    val value: Int,
    val fev: FEV,
)

@Serializable
data class CoreTransformTweakRange(
    val tweak: TransformTweak,
    val min: Double,
    val max: Double,
    val snaps: List<CoreTransformTweakSnap> = emptyList(),
    val options: List<CoreTransformTweakOption> = emptyList(),
)

@Serializable
data class CoreResponse(
    val poly: Polyhedron,
    val polyName: String,
    val symmetry: CoreSymmetry,
    val recognizedSeedTag: String? = null,
    val transformedPolys: List<Polyhedron>,
    val validTransformTags: List<String>,
    val availableOrbitTransforms: List<List<String>>,
    val warnings: List<CoreIssue?>,
    /** Geometry-safe tweak ranges for each logical transform, including a failing transform. */
    val transformTweakRanges: List<List<CoreTransformTweakRange>> = emptyList(),
    val errorIndex: Int? = null,
    val error: CoreIssue? = null,
    val animation: List<CoreAnimationStep> = emptyList(),
    val geometryAnalysis: CoreGeometryAnalysis? = null,
    val resolvedRims: List<polyhedra.model.poly.ResolvedRimGeometry> = emptyList(),
)

val CoreJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
