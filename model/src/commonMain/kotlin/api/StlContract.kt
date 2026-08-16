package polyhedra.model.api

import kotlinx.serialization.Serializable
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.util.MutableVec3

const val MAX_STL_INPUT_TRIANGLES = 250_000
const val MAX_STL_CANDIDATE_PAIRS = 2_000_000
const val MAX_STL_ARRANGEMENT_FRAGMENTS = 1_000_000
const val MAX_STL_FINAL_TRIANGLES = 500_000
const val MAX_STL_WORKING_MEMORY_BYTES = 256L * 1024 * 1024
const val MAX_STL_ELAPSED_MILLISECONDS = 60_000L
const val STL_COORDINATE_PRECISION = 8

@Serializable
data class CoreStlTriangle(
    val a: Int,
    val b: Int,
    val c: Int,
    /** Logical presentation surface; negative means the core must infer coplanar groups. */
    val surface: Int = -1,
    /** Independently closed presentation solid; negative means the input is one global soup. */
    val solid: Int = -1,
)

@Serializable
data class CoreStlRequest(
    val vertices: List<MutableVec3> = emptyList(),
    val triangles: List<CoreStlTriangle> = emptyList(),
    /** Authoritative geometry used when the core must construct the printable presentation. */
    val presentation: CoreStlPresentation? = null,
)

@Serializable
data class CoreStlPresentation(
    val poly: Polyhedron,
    val hiddenFaceKinds: List<FaceKind> = emptyList(),
    val scale: Double,
    val width: Double,
    val rim: Double,
    val expand: Double,
)

@Serializable
enum class CoreStlStage {
    Input,
    BroadPhase,
    Arrangement,
    Quantization,
    Validation,
}

@Serializable
enum class CoreStlErrorKind {
    InvalidInput,
    Topology,
    Limit,
}

@Serializable
data class CoreStlError(
    val stage: CoreStlStage,
    val reason: String,
    val limitName: String? = null,
    val limit: Long? = null,
    val observed: Long? = null,
    val kind: CoreStlErrorKind = CoreStlErrorKind.InvalidInput,
)

@Serializable
data class CoreStlResponse(
    val vertices: List<MutableVec3> = emptyList(),
    val triangles: List<CoreStlTriangle> = emptyList(),
    val error: CoreStlError? = null,
)
