/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.model.api

import kotlinx.serialization.Serializable
import polyhedra.model.poly.FEV
import polyhedra.model.util.MutableVec3

@Serializable
enum class PointGroupFamily {
    Cyclic,
    Dihedral,
    Tetrahedral,
    Octahedral,
    Icosahedral,
}

@Serializable
enum class PointGroupSuffix(val notation: String) {
    Horizontal("h"),
    Vertical("v"),
    Diagonal("d"),
    ImproperRotation("S"),
}

/** The full Schoenflies point group of a polyhedron. */
@Serializable
data class PointGroup(
    val family: PointGroupFamily,
    val fold: Int? = null,
    val suffix: PointGroupSuffix? = null,
) {
    init {
        val axial = family == PointGroupFamily.Cyclic || family == PointGroupFamily.Dihedral
        require(if (axial) fold != null && fold >= 1 else fold == null)
        require(
            suffix in when (family) {
                PointGroupFamily.Cyclic -> setOf(
                    null,
                    PointGroupSuffix.Horizontal,
                    PointGroupSuffix.Vertical,
                    PointGroupSuffix.ImproperRotation,
                )
                PointGroupFamily.Dihedral -> setOf(
                    null,
                    PointGroupSuffix.Horizontal,
                    PointGroupSuffix.Diagonal,
                )
                PointGroupFamily.Tetrahedral -> setOf(
                    null,
                    PointGroupSuffix.Horizontal,
                    PointGroupSuffix.Diagonal,
                )
                PointGroupFamily.Octahedral, PointGroupFamily.Icosahedral -> setOf(
                    null,
                    PointGroupSuffix.Horizontal,
                )
            }
        )
    }

    val symbol: String
        get() = when (family) {
            PointGroupFamily.Cyclic -> if (suffix == PointGroupSuffix.ImproperRotation) "S" else "C"
            PointGroupFamily.Dihedral -> "D"
            PointGroupFamily.Tetrahedral -> "T"
            PointGroupFamily.Octahedral -> "O"
            PointGroupFamily.Icosahedral -> "I"
        }

    val subscript: String?
        get() = when (family) {
            PointGroupFamily.Cyclic -> when (suffix) {
                PointGroupSuffix.ImproperRotation -> (2 * requireNotNull(fold)).toString()
                else -> requireNotNull(fold).toString() + (suffix?.notation ?: "")
            }
            PointGroupFamily.Dihedral -> requireNotNull(fold).toString() + (suffix?.notation ?: "")
            PointGroupFamily.Tetrahedral,
            PointGroupFamily.Octahedral,
            PointGroupFamily.Icosahedral -> suffix?.notation
        }

    val notation: String
        get() = symbol + (subscript?.let { "_$it" } ?: "")

    val fullName: String
        get() = when (family) {
            PointGroupFamily.Cyclic -> when (suffix) {
                null -> "${requireNotNull(fold)}-fold cyclic point group"
                PointGroupSuffix.Vertical -> "${requireNotNull(fold)}-fold pyramidal point group"
                PointGroupSuffix.Horizontal -> "${requireNotNull(fold)}-fold cyclic point group with a horizontal mirror"
                PointGroupSuffix.ImproperRotation -> "${2 * requireNotNull(fold)}-fold improper-rotation point group"
                PointGroupSuffix.Diagonal -> error("Invalid cyclic point-group suffix")
            }
            PointGroupFamily.Dihedral -> when (suffix) {
                null -> "${requireNotNull(fold)}-fold chiral dihedral point group"
                PointGroupSuffix.Horizontal -> "${requireNotNull(fold)}-fold prismatic point group"
                PointGroupSuffix.Diagonal -> "${requireNotNull(fold)}-fold antiprismatic point group"
                else -> error("Invalid dihedral point-group suffix")
            }
            PointGroupFamily.Tetrahedral -> when (suffix) {
                null -> "Chiral tetrahedral point group"
                PointGroupSuffix.Diagonal -> "Full tetrahedral point group"
                PointGroupSuffix.Horizontal -> "Pyritohedral point group"
                else -> error("Invalid tetrahedral point-group suffix")
            }
            PointGroupFamily.Octahedral -> when (suffix) {
                null -> "Chiral octahedral point group"
                PointGroupSuffix.Horizontal -> "Full octahedral point group"
                else -> error("Invalid octahedral point-group suffix")
            }
            PointGroupFamily.Icosahedral -> when (suffix) {
                null -> "Chiral icosahedral point group"
                PointGroupSuffix.Horizontal -> "Full icosahedral point group"
                else -> error("Invalid icosahedral point-group suffix")
            }
        }
}

/** Symmetry information derived from the current geometry in the Wasm core. */
@Serializable
data class CoreSymmetry(
    val pointGroup: PointGroup,
    val orbitCounts: FEV,
    val reflectionPlaneNormals: List<MutableVec3>,
    val rotationAxisDirections: List<MutableVec3>,
)
