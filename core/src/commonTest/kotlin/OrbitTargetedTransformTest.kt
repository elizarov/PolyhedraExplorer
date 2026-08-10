package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.KisFace
import polyhedra.core.transform.RectifyVertex
import polyhedra.core.transform.Transform
import polyhedra.core.transform.TruncateVertex
import polyhedra.core.transform.availableOrbitTransforms
import polyhedra.core.transform.kisFaces
import polyhedra.core.transform.rectifyVertices
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
        val rectify = "a[${VertexKind(1)}]".toTransformOrNull()

        assertIs<KisFace>(kis)
        assertEquals(FaceKind(1), kis.kind)
        assertEquals("Kis ${FaceKind(1)}", kis.toString())
        assertIs<TruncateVertex>(truncate)
        assertEquals(VertexKind(1), truncate.kind)
        assertEquals("Truncate B", truncate.toString())
        assertIs<RectifyVertex>(rectify)
        assertEquals(VertexKind(1), rectify.kind)
        assertEquals("Rectify B", rectify.toString())
        assertNull("k[A]".toTransformOrNull())
        assertNull("t[${FaceKind(0)}]".toTransformOrNull())
        assertNull("a[${FaceKind(0)}]".toTransformOrNull())
    }

    @Test
    fun exposesTargetedKisAndTruncateOnlyForMultipleOrbits() {
        val regular = Seed.Cube.poly.availableOrbitTransforms
        assertFalse(regular.any { it is KisFace || it is TruncateVertex || it is RectifyVertex })

        val multiFace = Seeds.first { it.poly.faceKinds.size > 1 }.poly
        val kisFaces = multiFace.availableOrbitTransforms.filterIsInstance<KisFace>()
        assertEquals(multiFace.faceKinds.keys, kisFaces.mapTo(linkedSetOf(), KisFace::kind))

        val multiVertex = Seeds.first { it.poly.vertexKinds.size > 1 }.poly
        val truncateVertices = multiVertex.availableOrbitTransforms.filterIsInstance<TruncateVertex>()
        assertEquals(multiVertex.vertexKinds.keys, truncateVertices.mapTo(linkedSetOf(), TruncateVertex::kind))
        val rectifyVertices = multiVertex.availableOrbitTransforms.filterIsInstance<RectifyVertex>()
        assertEquals(multiVertex.vertexKinds.keys, rectifyVertices.mapTo(linkedSetOf(), RectifyVertex::kind))
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
    fun rectifyingOneVertexOrbitMovesItsIncidentEdgesToSharedMidpoints() {
        val input = Seeds.first { it.poly.vertexKinds.size > 1 }.poly
        val kind = input.vertexKinds.keys.first()
        val selectedVertices = input.vs.filter { it.kind == kind }
        val incidentEdges = input.es.filter { it.a.kind == kind || it.b.kind == kind }

        val result = input.transformed(RectifyVertex(kind))
        result.validate()

        assertEquals(input.vs.size - selectedVertices.size + incidentEdges.size, result.vs.size)
        assertEquals(input.fs.size + selectedVertices.size, result.fs.size)
        assertEquals(result.fs.size + result.vs.size - 2, result.es.size)
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

        val targetedRectification = input.rectifyVertices(input.vertexKinds.keys)
        val fullRectification = input.transformed(Transform.Rectified)
        assertEquals(fullRectification.fev(), targetedRectification.fev())
        assertTrue(fullRectification.geometryFingerprint().matches(targetedRectification.geometryFingerprint()))
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
                testParameter("$seed rectify vertex", poly.vertexKinds.keys) { kind ->
                    poly.transformed(RectifyVertex(kind)).validate()
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
    fun changingTargetOrbitAnimatesOldTargetOutThenNewTargetIn() = runTest {
        val vertexSeed = Seeds.first { it.poly.vertexKinds.size > 1 }
        val vertexKinds = vertexSeed.poly.vertexKinds.keys.toList()
        val cases = listOf(
            vertexSeed to listOf(TruncateVertex(vertexKinds[0]), TruncateVertex(vertexKinds[1])),
            vertexSeed to listOf(RectifyVertex(vertexKinds[0]), RectifyVertex(vertexKinds[1])),
        )

        for ((seed, transforms) in cases) {
            val baseState = CoreState(seed.tag, emptyList(), "c")
            val response = evaluateCore(
                CoreRequest(
                    state = baseState.copy(transformTags = listOf(transforms[1].tag)),
                    previousState = baseState.copy(transformTags = listOf(transforms[0].tag)),
                    animationDuration = 0.5,
                ),
            )

            assertEquals(2, response.animation.size, transforms.joinToString(" -> "))
            assertEquals(listOf(0.5, 0.5), response.animation.map { it.duration })
            assertTrue(response.animation.all { it.previousPoly.hasSameTopology(it.targetPoly) })
            assertTrue(response.animation[0].targetFraction >= 0.999, "old target must animate out")
            assertTrue(response.animation[1].previousFraction <= 0.001, "new target must animate in")
        }
    }

    @Test
    fun targetedKisNeverProducesAnimationKeyframes() = runTest {
        val seed = Seed.TruncatedCube
        val kinds = seed.poly.faceKinds.keys.toList()
        val baseState = CoreState(seed.tag, emptyList(), "c")
        val firstState = baseState.copy(transformTags = listOf(KisFace(kinds[0]).tag))
        val secondState = baseState.copy(transformTags = listOf(KisFace(kinds[1]).tag))

        for ((previous, current) in listOf(
            baseState to firstState,
            firstState to baseState,
            firstState to secondState,
        )) {
            val response = evaluateCore(
                CoreRequest(current, previous, animationDuration = 0.5),
            )
            assertTrue(response.animation.isEmpty(), "$previous -> $current")
        }
    }

    @Test
    fun selectiveTruncateAndRectifyAnimateInAndOut() = runTest {
        val vertexSeed = Seeds.first { it.poly.vertexKinds.size > 1 }
        val cases = listOf(
            vertexSeed to TruncateVertex(vertexSeed.poly.vertexKinds.keys.first()),
            vertexSeed to RectifyVertex(vertexSeed.poly.vertexKinds.keys.first()),
        )

        for ((seed, transform) in cases) {
            val baseState = CoreState(seed.tag, emptyList(), "c")
            val transformedState = baseState.copy(transformTags = listOf(transform.tag))
            val animateIn = evaluateCore(
                CoreRequest(transformedState, baseState, animationDuration = 0.5),
            ).animation.single()
            val animateOut = evaluateCore(
                CoreRequest(baseState, transformedState, animationDuration = 0.5),
            ).animation.single()

            assertEquals(0.5, animateIn.duration, "$transform in")
            assertEquals(0.5, animateOut.duration, "$transform out")
            assertTrue(animateIn.previousPoly.hasSameTopology(animateIn.targetPoly), "$transform in topology")
            assertTrue(animateOut.previousPoly.hasSameTopology(animateOut.targetPoly), "$transform out topology")
        }
    }

    @Test
    fun truncateAndRectifyOnSameOrbitAnimateAsOneCutDepthTransition() = runTest {
        val seed = Seeds.first { it.poly.vertexKinds.size > 1 }
        val kind = seed.poly.vertexKinds.keys.first()
        val truncateState = CoreState(seed.tag, listOf(TruncateVertex(kind).tag), "c")
        val rectifyState = CoreState(seed.tag, listOf(RectifyVertex(kind).tag), "c")

        for ((previous, current) in listOf(
            truncateState to rectifyState,
            rectifyState to truncateState,
        )) {
            val animation = evaluateCore(
                CoreRequest(current, previous, animationDuration = 0.5),
            ).animation.single()

            assertEquals(0.5, animation.duration)
            assertTrue(animation.previousPoly.hasSameTopology(animation.targetPoly))
            assertTrue(animation.previousFraction <= 0.001)
            assertTrue(animation.targetFraction >= 0.999)
        }
    }
}

private fun Polyhedron.assertConvex() {
    for (face in fs) {
        val furthest = vs.maxOf { vertex -> vertex * face - face.d }
        assertTrue(furthest <= EPS, "$face cuts through the mesh by $furthest")
    }
}
