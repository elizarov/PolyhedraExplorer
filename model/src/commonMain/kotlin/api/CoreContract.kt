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
)

data class CoreProgress(
    val transformIndex: Int,
    val done: Int,
)

@Serializable
enum class CoreIssueCode {
    TransformFailed,
    TransformNotApplicable,
    TransformIsIdentity,
    TooLarge,
    SomeFacesNotPlanar,
}

@Serializable
data class CoreIssue(
    val code: CoreIssueCode,
    val transformTag: String? = null,
    val fev: FEV? = null,
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
data class CoreResponse(
    val poly: Polyhedron,
    val polyName: String,
    val recognizedSeedTag: String? = null,
    val transformedPolys: List<Polyhedron>,
    val validTransformTags: List<String>,
    val availableOrbitTransforms: List<List<String>>,
    val warnings: List<CoreIssue?>,
    val errorIndex: Int? = null,
    val error: CoreIssue? = null,
    val animation: List<CoreAnimationStep> = emptyList(),
)

val CoreJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
