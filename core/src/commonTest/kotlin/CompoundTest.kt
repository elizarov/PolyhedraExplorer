package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.*
import polyhedra.model.api.*
import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.test.*

class CompoundTest {
    @Test
    fun regularSeedsHaveIndependentRegularMembersAndFullSymmetry() {
        val expected = listOf(
            Triple(Seed.TwoTetrahedra, 2, "O"),
            Triple(Seed.FiveTetrahedra, 5, "I"),
            Triple(Seed.TenTetrahedra, 10, "I"),
            Triple(Seed.FiveCubes, 5, "I"),
            Triple(Seed.FiveOctahedra, 5, "I"),
        )
        for ((seed, count, _) in expected) {
            val poly = seed.poly
            poly.validateRenderableImmersion()
            assertEquals(seed.fev, poly.fev(), seed.tag)
            assertEquals(count, poly.components.size, seed.tag)
            for (member in poly.componentPolyhedra()) {
                member.validateProperGeometry()
                assertTrue(member.es.all { kotlin.math.abs(it.len - member.es.first().len) < 1e-7 }, seed.tag)
            }
            val symmetry = poly.analyzeSymmetry()
            val orbits = when (seed) {
                Seed.TenTetrahedra -> FEV(2, 2, 2)
                Seed.FiveCubes -> FEV(1, 1, 2)
                Seed.FiveOctahedra -> FEV(2, 1, 1)
                else -> FEV(1, 1, 1)
            }
            assertEquals(orbits, symmetry.orbitCounts, seed.tag)
            assertEquals(if (seed == Seed.TwoTetrahedra) PointGroupFamily.Octahedral else PointGroupFamily.Icosahedral,
                symmetry.pointGroup.family, seed.tag)
            assertEquals(if (seed == Seed.FiveTetrahedra) 0 else if (seed == Seed.TwoTetrahedra) 9 else 15,
                symmetry.reflectionPlaneNormals.size, seed.tag)
        }
    }

    @Test
    fun dualAndSerializationPreserveCoincidentButDistinctVertices() {
        val cubes = Seed.FiveCubes.poly
        val roundTrip = kotlinx.serialization.json.Json.decodeFromString<Polyhedron>(
            kotlinx.serialization.json.Json.encodeToString(Polyhedron.serializer(), cubes),
        )
        assertEquals(5, roundTrip.components.size)
        assertEquals(FEV(30, 60, 40), roundTrip.fev())
        val dual = roundTrip.dual()
        assertEquals(FEV(40, 60, 30), dual.fev())
        assertEquals(5, dual.components.size)
        assertEquals(FEV(2, 1, 1), dual.analyzeSymmetry().orbitCounts)
        for ((tag, dualTag) in listOf("C2T" to "C2T", "C5T" to "C5T'", "C10T" to "C10T")) {
            assertEquals(dualTag, tag.toSeedOrNull()!!.poly.dual().recognizedSeedOrNull()?.tag, tag)
        }
    }

    @Test
    fun localPrimitivesRunOnAllRegularCompounds() = runTest {
        val operations = listOf("d", "t", "a", "e", "b", "s", "s'", "c", "o", "p", "p'", "w", "w'", "q",
            "k", "j", "N", "z", "O", "m", "g", "g'")
        for (seed in Seeds.filter { it.type == SeedType.RegularCompounds && it.chirality != Chirality.Flipped }) {
            for (operation in operations) {
                val response = evaluateCore(CoreRequest(CoreState(seed.tag, listOf(operation), "c")))
                assertNull(response.error, "${seed.tag}/$operation: ${response.error}")
                assertEquals(seed.poly.components.size, response.poly.components.size, "${seed.tag}/$operation")
                response.poly.validateRenderableImmersion()
            }
        }
    }

    @Test
    fun stellationFindsClassicalCompoundsGeometrically() = runTest {
        for (seed in listOf(Seed.Octahedron, Seed.Icosahedron, Seed.RhombicTriacontahedron)) {
            val candidates = seed.poly.stellationCandidatesAsync(ConstellationOperation.Stellate)
            println("${seed.tag} stellations: ${candidates.map { Triple(it.fev, it.poly.components.size, it.poly.recognizedSeedOrNull()?.tag) }}")
            val expected = when (seed) {
                Seed.Octahedron -> listOf("C2T")
                Seed.RhombicTriacontahedron -> listOf("C5C")
                else -> listOf("C5T", "C10T", "C5O")
            }
            for (tag in expected) assertTrue(candidates.any { it.poly.recognizedSeedOrNull()?.tag?.removeSuffix("'") == tag }, tag)
        }
    }

    @Test
    fun compoundRecognitionDistinguishesTheTwoChiralArrangements() {
        val source = Seed.FiveTetrahedra.poly
        assertEquals("C5T", source.recognizedSeedOrNull()?.tag)
        assertEquals("C5T'", source.reflected().recognizedSeedOrNull()?.tag)
        val misaligned = compound(source.componentPolyhedra().mapIndexed { index, member ->
            if (index == 0) member.reflected() else member
        })
        assertNull(misaligned.recognizedSeedOrNull())
    }

    @Test
    fun coincidentTransformedMembersShareOrbitsWithoutInflatingPointGroup() {
        val result = Seed.TwoTetrahedra.poly.rectified()
        assertEquals(2, result.components.size)
        val symmetry = result.analyzeSymmetry()
        assertEquals(PointGroupFamily.Octahedral, symmetry.pointGroup.family)
        assertEquals(FEV(1, 1, 1), symmetry.orbitCounts)
        assertEquals(24, result.geometricOrbitDetails().properOperationCount)
    }

    @Test
    fun greateningRetainsCompoundCandidates() = runTest {
        val candidates = Seed.Icosahedron.poly.stellationCandidatesAsync(ConstellationOperation.Greaten)
        println("I greatenings: ${candidates.map { Triple(it.fev, it.poly.components.size, it.poly.recognizedSeedOrNull()?.tag) }}")
        assertTrue(candidates.any { it.poly.isCompound })
        candidates.forEach { it.poly.validateRenderableImmersion() }
    }

    @Test
    fun starConstructionAcceptsCompoundSourcesAndRepeatedPlanes() = runTest {
        for (seed in listOf(Seed.TwoTetrahedra, Seed.FiveTetrahedra, Seed.TenTetrahedra, Seed.FiveCubes, Seed.FiveOctahedra)) {
            for (operation in ConstellationOperation.entries) {
                val candidates = seed.poly.stellationCandidatesAsync(operation)
                println("${seed.tag} $operation: ${candidates.size}")
                candidates.forEach { it.poly.validateRenderableImmersion() }
                if (operation == ConstellationOperation.Greaten && seed != Seed.TwoTetrahedra) {
                    assertTrue(candidates.isNotEmpty(), seed.tag)
                }
            }
        }
    }

    @Test
    fun everyAdvertisedOrbitActionRunsOnCompoundMembers() = runTest {
        for (seed in listOf(Seed.FiveTetrahedra, Seed.FiveCubes, Seed.FiveOctahedra)) {
            for (prefix in listOf("t", "k")) {
                val initial = evaluateCore(CoreRequest(CoreState(seed.tag, listOf(prefix), "c")))
                for (tag in initial.availableOrbitTransforms.last()) {
                    val response = evaluateCore(CoreRequest(CoreState(seed.tag, listOf(prefix, tag), "c")))
                    assertNull(response.error, "${seed.tag}/$tag: ${response.error}")
                    response.poly.validateRenderableImmersion()
                    assertEquals(seed.poly.components.size, response.poly.components.size)
                }
            }
        }
    }

    @Test
    fun embeddedValidationUsesLocalTriangleScale() {
        val cube = Seed.Cube.poly
        val tiny = polyhedron {
            cube.vs.forEach { vertex(it * 1e-5 + Vec3(3.0, 0.0, 0.0)) }
            cube.fs.forEach { face(it) }
        }
        compound(listOf(cube, tiny)).validateProperGeometry()
        val face = cube.fs.first()
        val intersecting = polyhedron {
            cube.vs.forEach { vertex(it * 1e-5 + face.unit * face.d) }
            cube.fs.forEach { face(it) }
        }
        assertFailsWith<IllegalArgumentException> {
            compound(listOf(cube, intersecting)).validateProperGeometry()
        }
    }
}
