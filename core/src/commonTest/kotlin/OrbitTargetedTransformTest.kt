package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.KisFace
import polyhedra.core.transform.Transform
import polyhedra.core.transform.TruncateVertex
import polyhedra.core.transform.availableOrbitTransforms
import polyhedra.core.transform.kisFaces
import polyhedra.core.transform.toTransformOrNull
import polyhedra.core.transform.transformed
import polyhedra.core.transform.truncateVertices
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.fev
import polyhedra.model.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrbitTargetedTransformTest {
    @Test
    fun parsesConcreteTargetTagsAndReportsTheirNames() {
        val kis = "k[${FaceKind(1)}]".toTransformOrNull()
        val truncate = "t[${VertexKind(1)}]".toTransformOrNull()

        assertIs<KisFace>(kis)
        assertEquals(FaceKind(1), kis.kind)
        assertEquals("Kis ${FaceKind(1)}", kis.toString())
        assertIs<TruncateVertex>(truncate)
        assertEquals(VertexKind(1), truncate.kind)
        assertEquals("Truncate B", truncate.toString())
        assertNull("k[A]".toTransformOrNull())
        assertNull("t[${FaceKind(0)}]".toTransformOrNull())
    }

    @Test
    fun exposesTargetedKisAndTruncateOnlyForMultipleOrbits() {
        val regular = Seed.Cube.poly.availableOrbitTransforms
        assertFalse(regular.any { it is KisFace || it is TruncateVertex })

        val multiFace = Seeds.first { it.poly.faceKinds.size > 1 }.poly
        val kisFaces = multiFace.availableOrbitTransforms.filterIsInstance<KisFace>()
        assertEquals(multiFace.faceKinds.keys, kisFaces.mapTo(linkedSetOf(), KisFace::kind))

        val multiVertex = Seeds.first { it.poly.vertexKinds.size > 1 }.poly
        val truncateVertices = multiVertex.availableOrbitTransforms.filterIsInstance<TruncateVertex>()
        assertEquals(multiVertex.vertexKinds.keys, truncateVertices.mapTo(linkedSetOf(), TruncateVertex::kind))
    }

    @Test
    fun kisingOneFaceOrbitBuildsAPyramidOnEveryFaceInThatOrbit() {
        val input = Seed.TruncatedCube.poly
        val kind = input.faceKinds.keys.first()
        val selectedFaces = input.fs.filter { it.kind == kind }
        val selectedSides = selectedFaces.sumOf { it.fvs.size }

        val result = input.transformed(KisFace(kind))
        result.validate()

        assertEquals(input.vs.size + selectedFaces.size, result.vs.size)
        assertEquals(input.es.size + selectedSides, result.es.size)
        assertEquals(input.fs.size - selectedFaces.size + selectedSides, result.fs.size)
    }

    @Test
    fun truncatingOneVertexOrbitCutsEveryVertexInThatOrbit() {
        val input = Seeds.first { it.poly.vertexKinds.size > 1 }.poly
        val kind = input.vertexKinds.keys.first()
        val selectedVertices = input.vs.filter { it.kind == kind }
        val selectedDegree = selectedVertices.sumOf { it.directedEdges.size }

        val result = input.transformed(TruncateVertex(kind))
        result.validate()

        assertEquals(input.vs.size + selectedDegree - selectedVertices.size, result.vs.size)
        assertEquals(input.es.size + selectedDegree, result.es.size)
        assertEquals(input.fs.size + selectedVertices.size, result.fs.size)
    }

    @Test
    fun targetingEveryOrbitMatchesTheExistingFullTransformGeometry() {
        val input = Seed.TruncatedCube.poly
        val targetedKis = input.kisFaces(input.faceKinds.keys)
        val macroKis = input.transformed(Transform.Dual, Transform.Truncated, Transform.Dual)
        assertEquals(macroKis.fev(), targetedKis.fev())
        assertTrue(macroKis.geometryFingerprint().matches(targetedKis.geometryFingerprint()))

        val targetedTruncation = input.truncateVertices(input.vertexKinds.keys)
        val fullTruncation = input.transformed(Transform.Truncated)
        assertEquals(fullTruncation.fev(), targetedTruncation.fev())
        assertTrue(fullTruncation.geometryFingerprint().matches(targetedTruncation.geometryFingerprint()))
    }

    @Test
    fun everyCatalogOrbitProducesAValidSelectiveTransform() {
        for (seed in Seeds) {
            val poly = seed.poly
            if (poly.faceKinds.size > 1) {
                testParameter("$seed kis face", poly.faceKinds.keys) { kind ->
                    poly.transformed(KisFace(kind)).validate()
                }
            }
            if (poly.vertexKinds.size > 1) {
                testParameter("$seed truncate vertex", poly.vertexKinds.keys) { kind ->
                    poly.transformed(TruncateVertex(kind)).validate()
                }
            }
        }
    }

    @Test
    fun evaluatesConcreteTargetedTransformsThroughTheCoreContract() = runTest {
        val faceKind = Seed.TruncatedCube.poly.faceKinds.keys.first()
        val initial = evaluateCore(CoreRequest(CoreState("tC", emptyList(), "c")))
        assertEquals(
            Seed.TruncatedCube.poly.faceKinds.keys.mapTo(linkedSetOf()) { KisFace(it).tag },
            initial.availableOrbitTransforms.single().filterTo(linkedSetOf()) { it.startsWith("k[") },
        )
        val response = evaluateCore(
            CoreRequest(CoreState("tC", listOf(KisFace(faceKind).tag), "c")),
        )

        assertNull(response.error)
        assertEquals(listOf(KisFace(faceKind).tag), response.validTransformTags)
        assertEquals("Kis $faceKind Truncated cube", response.polyName)
    }

    @Test
    fun targetedKisZetaRemainsConvexAfterSnubAndCanonicalization() = runTest {
        evaluateCore(
            CoreRequest(CoreState("sD", listOf("k", "s", "o", "k[${FaceKind(4)}]"), "c")),
        )
        val response = evaluateCore(
            CoreRequest(CoreState("sD", listOf("k", "s", "o", "k[${FaceKind(5)}]"), "c")),
        )

        assertNull(response.error)
        response.poly.validate()
        assertTrue(
            response.poly.nonPlanarFaceKinds.isEmpty(),
            "Unexpected non-planar orbits: ${response.poly.nonPlanarFaceKinds}",
        )
        response.poly.assertConvex()
    }

    @Test
    fun growingTargetedKisMeshDoesNotInterpolateUnrelatedMeshIndices() = runTest {
        val baseTags = listOf("k", "s", "o")
        val baseState = CoreState("sD", baseTags, "c")
        val previousTag = "k[${FaceKind(4)}]"
        val currentTag = "k[${FaceKind(5)}]"
        val response = evaluateCore(
            CoreRequest(
                state = baseState.copy(transformTags = baseTags + currentTag),
                previousState = baseState.copy(transformTags = baseTags + previousTag),
                animationDuration = 0.5,
            ),
        )
        assertTrue(
            response.animation.isEmpty(),
            "$previousTag -> $currentTag must not pair unrelated vertices by index",
        )
    }
}

private fun Polyhedron.assertConvex() {
    for (face in fs) {
        val furthest = vs.maxOf { vertex -> vertex * face - face.d }
        assertTrue(furthest <= EPS, "$face cuts through the mesh by $furthest")
    }
}
