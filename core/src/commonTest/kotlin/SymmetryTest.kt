/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core

import polyhedra.core.poly.*
import polyhedra.core.transform.Transform
import polyhedra.core.transform.transformed
import polyhedra.model.api.PointGroup
import polyhedra.model.api.PointGroupFamily
import polyhedra.model.api.PointGroupSuffix
import polyhedra.model.poly.FEV
import kotlin.test.Test
import kotlin.test.assertEquals

class SymmetryTest {
    @Test
    fun catalogSymmetryMatchesDeclaredPointGroupsAndKinds() {
        val tetrahedralTags = setOf("T", "tT", "dtT")
        val octahedralTags = setOf(
            "C", "O", "C2T", "aC", "tC", "tO", "eC", "bC", "sC", "sC'",
            "daC", "dtC", "dtO", "deC", "dbC", "dsC", "dsC'",
        )
        for (seed in Seeds) {
            val expectedFamily = when (seed.tag) {
                in tetrahedralTags -> PointGroupFamily.Tetrahedral
                in octahedralTags -> PointGroupFamily.Octahedral
                else -> PointGroupFamily.Icosahedral
            }
            val symmetry = seed.poly.analyzeSymmetry()
            val expectedPlanes = when {
                seed.chirality != null -> 0
                expectedFamily == PointGroupFamily.Tetrahedral -> 6
                expectedFamily == PointGroupFamily.Octahedral -> 9
                else -> 15
            }
            val expectedSuffix = when {
                seed.chirality != null -> null
                expectedFamily == PointGroupFamily.Tetrahedral -> PointGroupSuffix.Diagonal
                else -> PointGroupSuffix.Horizontal
            }
            val expectedPointGroup = PointGroup(expectedFamily, suffix = expectedSuffix)

            assertEquals(expectedPointGroup, symmetry.pointGroup, seed.tag)
            assertEquals(
                FEV(seed.poly.faceKinds.size, seed.poly.edgeKinds.size, seed.poly.vertexKinds.size),
                symmetry.orbitCounts,
                seed.tag,
            )
            assertEquals(expectedPlanes, symmetry.reflectionPlaneNormals.size, seed.tag)
            assertEquals(expectedAxisCount(symmetry.pointGroup), symmetry.rotationAxisDirections.size, seed.tag)
        }
    }

    @Test
    fun classifiesPolyhedralSymmetriesAndMirrorPlanes() {
        assertSymmetry(
            Seed.Tetrahedron.tag,
            PointGroup(PointGroupFamily.Tetrahedral, suffix = PointGroupSuffix.Diagonal),
            6,
            FEV(1, 1, 1),
        )
        assertSymmetry(
            Seed.Cube.tag,
            PointGroup(PointGroupFamily.Octahedral, suffix = PointGroupSuffix.Horizontal),
            9,
            FEV(1, 1, 1),
        )
        assertSymmetry(
            Seed.Icosahedron.tag,
            PointGroup(PointGroupFamily.Icosahedral, suffix = PointGroupSuffix.Horizontal),
            15,
            FEV(1, 1, 1),
        )
        assertSymmetry(
            Seed.SnubCube.tag,
            PointGroup(PointGroupFamily.Octahedral),
            0,
            FEV(3, 3, 1),
        )
    }

    @Test
    fun classifiesAxialFamilySymmetriesAndMirrorPlanes() {
        assertSymmetry(
            "P7",
            PointGroup(PointGroupFamily.Dihedral, 7, PointGroupSuffix.Horizontal),
            8,
            FEV(2, 2, 1),
        )
        assertSymmetry(
            "A7",
            PointGroup(PointGroupFamily.Dihedral, 7, PointGroupSuffix.Diagonal),
            7,
            FEV(2, 3, 1),
        )
        assertSymmetry(
            "Y7",
            PointGroup(PointGroupFamily.Cyclic, 7, PointGroupSuffix.Vertical),
            7,
            FEV(2, 2, 2),
        )
        assertSymmetry(
            "B7",
            PointGroup(PointGroupFamily.Dihedral, 7, PointGroupSuffix.Horizontal),
            8,
            FEV(1, 2, 2),
        )
    }

    @Test
    fun classifiesStarFamilySymmetriesFromAuthoritativeTopology() {
        assertSymmetry(
            "SP7_2",
            PointGroup(PointGroupFamily.Dihedral, 7, PointGroupSuffix.Horizontal),
            8,
            FEV(2, 2, 1),
        )
        assertSymmetry(
            "SA7_2",
            PointGroup(PointGroupFamily.Dihedral, 7, PointGroupSuffix.Horizontal),
            8,
            FEV(2, 3, 1),
        )
        assertSymmetry(
            "SA7_3",
            PointGroup(PointGroupFamily.Dihedral, 7, PointGroupSuffix.Diagonal),
            7,
            FEV(2, 3, 1),
        )
        assertSymmetry(
            "SY7_2",
            PointGroup(PointGroupFamily.Cyclic, 7, PointGroupSuffix.Vertical),
            7,
            FEV(2, 2, 2),
        )
        assertSymmetry(
            "SB7_2",
            PointGroup(PointGroupFamily.Dihedral, 7, PointGroupSuffix.Horizontal),
            8,
            FEV(1, 2, 2),
        )
    }

    @Test
    fun reportsStrengthenedGeometryInsteadOfInheritedKinds() {
        val symmetry = Seed.Tetrahedron.poly.transformed(Transform.Snub).analyzeSymmetry()

        assertEquals(
            PointGroup(PointGroupFamily.Icosahedral, suffix = PointGroupSuffix.Horizontal),
            symmetry.pointGroup,
        )
        assertEquals(FEV(1, 1, 1), symmetry.orbitCounts)
        assertEquals(15, symmetry.reflectionPlaneNormals.size)
        assertEquals(31, symmetry.rotationAxisDirections.size)
    }

    private fun assertSymmetry(
        seedTag: String,
        pointGroup: PointGroup,
        reflectionPlanes: Int,
        orbitCounts: FEV,
    ) {
        val symmetry = requireNotNull(seedTag.toSeedOrNull()).poly.analyzeSymmetry()
        assertEquals(pointGroup, symmetry.pointGroup, seedTag)
        assertEquals(orbitCounts, symmetry.orbitCounts, seedTag)
        assertEquals(reflectionPlanes, symmetry.reflectionPlaneNormals.size, seedTag)
        assertEquals(expectedAxisCount(pointGroup), symmetry.rotationAxisDirections.size, seedTag)
    }

    private fun expectedAxisCount(pointGroup: PointGroup): Int = when (pointGroup.family) {
        PointGroupFamily.Cyclic -> 1
        PointGroupFamily.Dihedral -> requireNotNull(pointGroup.fold) + 1
        PointGroupFamily.Tetrahedral -> 7
        PointGroupFamily.Octahedral -> 13
        PointGroupFamily.Icosahedral -> 31
    }
}
