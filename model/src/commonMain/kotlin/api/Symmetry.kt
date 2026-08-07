/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.model.api

import kotlinx.serialization.Serializable
import polyhedra.model.poly.FEV
import polyhedra.model.util.MutableVec3

@Serializable
enum class SymmetryFamily {
    Cyclic,
    Dihedral,
    Tetrahedral,
    Octahedral,
    Icosahedral,
}

/** The proper-rotation symmetry class of a polyhedron. */
@Serializable
data class SymmetryGroup(
    val family: SymmetryFamily,
    val fold: Int? = null,
) {
    init {
        val axial = family == SymmetryFamily.Cyclic || family == SymmetryFamily.Dihedral
        require(if (axial) fold != null && fold >= 1 else fold == null)
    }

    val compactName: String
        get() = when (family) {
            SymmetryFamily.Cyclic -> "C${requireNotNull(fold)}"
            SymmetryFamily.Dihedral -> "D${requireNotNull(fold)}"
            SymmetryFamily.Tetrahedral -> "T"
            SymmetryFamily.Octahedral -> "O"
            SymmetryFamily.Icosahedral -> "I"
        }

    val fullName: String
        get() = when (family) {
            SymmetryFamily.Cyclic -> "${requireNotNull(fold)}-fold cyclic symmetry"
            SymmetryFamily.Dihedral -> "${requireNotNull(fold)}-fold dihedral symmetry"
            SymmetryFamily.Tetrahedral -> "Tetrahedral symmetry"
            SymmetryFamily.Octahedral -> "Octahedral symmetry"
            SymmetryFamily.Icosahedral -> "Icosahedral symmetry"
        }
}

/** Symmetry information derived from the current geometry in the Wasm core. */
@Serializable
data class CoreSymmetry(
    val group: SymmetryGroup,
    val orbitCounts: FEV,
    val reflectionPlaneNormals: List<MutableVec3>,
    val rotationAxisDirections: List<MutableVec3>,
)
