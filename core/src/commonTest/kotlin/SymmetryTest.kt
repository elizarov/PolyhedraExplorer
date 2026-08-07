/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core

import polyhedra.core.poly.*
import polyhedra.core.transform.Transform
import polyhedra.core.transform.transformed
import polyhedra.model.api.SymmetryFamily
import polyhedra.model.api.SymmetryGroup
import polyhedra.model.poly.FEV
import kotlin.test.Test
import kotlin.test.assertEquals

class SymmetryTest {
    @Test
    fun catalogSymmetryMatchesDeclaredClassesAndKinds() {
        val tetrahedralTags = setOf("T", "tT", "dtT")
        val octahedralTags = setOf(
            "C", "O", "aC", "tC", "tO", "eC", "bC", "sC", "sC'",
            "daC", "dtC", "dtO", "deC", "dbC", "dsC", "dsC'",
        )
        for (seed in Seeds) {
            val expectedFamily = when (seed.tag) {
                in tetrahedralTags -> SymmetryFamily.Tetrahedral
                in octahedralTags -> SymmetryFamily.Octahedral
                else -> SymmetryFamily.Icosahedral
            }
            val symmetry = seed.poly.analyzeSymmetry()
            val expectedPlanes = when {
                seed.chirality != null -> 0
                expectedFamily == SymmetryFamily.Tetrahedral -> 6
                expectedFamily == SymmetryFamily.Octahedral -> 9
                else -> 15
            }

            assertEquals(SymmetryGroup(expectedFamily), symmetry.group, seed.tag)
            assertEquals(
                FEV(seed.poly.faceKinds.size, seed.poly.edgeKinds.size, seed.poly.vertexKinds.size),
                symmetry.orbitCounts,
                seed.tag,
            )
            assertEquals(expectedPlanes, symmetry.reflectionPlaneNormals.size, seed.tag)
            assertEquals(expectedAxisCount(symmetry.group), symmetry.rotationAxisDirections.size, seed.tag)
        }
    }

    @Test
    fun classifiesPolyhedralSymmetriesAndMirrorPlanes() {
        assertSymmetry(Seed.Tetrahedron.tag, SymmetryGroup(SymmetryFamily.Tetrahedral), 6, FEV(1, 1, 1))
        assertSymmetry(Seed.Cube.tag, SymmetryGroup(SymmetryFamily.Octahedral), 9, FEV(1, 1, 1))
        assertSymmetry(Seed.Icosahedron.tag, SymmetryGroup(SymmetryFamily.Icosahedral), 15, FEV(1, 1, 1))
        assertSymmetry(Seed.SnubCube.tag, SymmetryGroup(SymmetryFamily.Octahedral), 0, FEV(3, 3, 1))
    }

    @Test
    fun classifiesAxialFamilySymmetriesAndMirrorPlanes() {
        assertSymmetry("P7", SymmetryGroup(SymmetryFamily.Dihedral, 7), 8, FEV(2, 2, 1))
        assertSymmetry("A7", SymmetryGroup(SymmetryFamily.Dihedral, 7), 7, FEV(2, 3, 1))
        assertSymmetry("Y7", SymmetryGroup(SymmetryFamily.Cyclic, 7), 7, FEV(2, 2, 2))
        assertSymmetry("B7", SymmetryGroup(SymmetryFamily.Dihedral, 7), 8, FEV(1, 2, 2))
    }

    @Test
    fun reportsStrengthenedGeometryInsteadOfInheritedKinds() {
        val symmetry = Seed.Tetrahedron.poly.transformed(Transform.Snub).analyzeSymmetry()

        assertEquals(SymmetryGroup(SymmetryFamily.Icosahedral), symmetry.group)
        assertEquals(FEV(1, 1, 1), symmetry.orbitCounts)
        assertEquals(15, symmetry.reflectionPlaneNormals.size)
        assertEquals(31, symmetry.rotationAxisDirections.size)
    }

    private fun assertSymmetry(
        seedTag: String,
        group: SymmetryGroup,
        reflectionPlanes: Int,
        orbitCounts: FEV,
    ) {
        val symmetry = requireNotNull(seedTag.toSeedOrNull()).poly.analyzeSymmetry()
        assertEquals(group, symmetry.group, seedTag)
        assertEquals(orbitCounts, symmetry.orbitCounts, seedTag)
        assertEquals(reflectionPlanes, symmetry.reflectionPlaneNormals.size, seedTag)
        assertEquals(expectedAxisCount(group), symmetry.rotationAxisDirections.size, seedTag)
    }

    private fun expectedAxisCount(group: SymmetryGroup): Int = when (group.family) {
        SymmetryFamily.Cyclic -> 1
        SymmetryFamily.Dihedral -> requireNotNull(group.fold) + 1
        SymmetryFamily.Tetrahedral -> 7
        SymmetryFamily.Octahedral -> 13
        SymmetryFamily.Icosahedral -> 31
    }
}
