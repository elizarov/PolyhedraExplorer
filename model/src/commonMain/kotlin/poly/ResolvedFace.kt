package polyhedra.model.poly

import kotlinx.serialization.Serializable
import polyhedra.model.api.ResolvedElementProvenance
import polyhedra.model.util.MutableVec3

@Serializable
data class ResolvedFaceVertex(
    val position: MutableVec3,
    val provenance: ResolvedElementProvenance,
)

@Serializable
data class ResolvedFaceEdge(
    val a: Int,
    val b: Int,
    val provenance: ResolvedElementProvenance,
    val internalToFill: Boolean,
)

@Serializable
data class ResolvedFaceTriangle(
    val a: Int,
    val b: Int,
    val c: Int,
    val sourceCellId: Int,
)

@Serializable
data class ResolvedFaceCell(
    val id: Int,
    val winding: Int,
    /** Counter-clockwise when viewed along the source face's outward normal. */
    val boundary: List<Int>,
    val triangles: List<ResolvedFaceTriangle>,
)

/** Derived nonzero-winding presentation geometry for one authoritative source face. */
@Serializable
data class ResolvedFaceGeometry(
    val sourceFaceId: Int,
    val sourceFaceKind: FaceKind,
    val sourceBoundarySelfIntersects: Boolean,
    val vertices: List<ResolvedFaceVertex>,
    val cells: List<ResolvedFaceCell>,
    val edges: List<ResolvedFaceEdge>,
) {
    val triangles: List<ResolvedFaceTriangle>
        get() = cells.flatMap(ResolvedFaceCell::triangles)
}
