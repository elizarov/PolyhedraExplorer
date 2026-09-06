package polyhedra.model.poly

import kotlinx.serialization.Serializable
import polyhedra.model.util.MutableVec3

/** One convex cell of a coplanar face overlay. Winding is that of the first source face. */
@Serializable
data class CoplanarFacePatch(
    val vertices: List<MutableVec3>,
    /** All source faces covering this cell, sorted by ID; not just a pair. */
    val sourceFaceIds: List<Int>,
    /** When rim boundaries are included, the sources whose rim covers this cell. */
    val rimFaceIds: List<Int> = emptyList(),
)
