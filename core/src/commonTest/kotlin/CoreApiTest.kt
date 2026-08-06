package polyhedra.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import polyhedra.model.poly.FEV
import polyhedra.model.poly.fev
import polyhedra.core.transform.isCanonical
import polyhedra.core.api.*
import polyhedra.model.api.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreApiTest {
    @Test
    fun evaluatesCompleteTransformPipeline() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("C", listOf("t"), "c"),
            )
        )

        assertEquals(14, response.poly.fs.size)
        assertEquals(36, response.poly.es.size)
        assertEquals(24, response.poly.vs.size)
        assertEquals(listOf("t"), response.validTransformTags)
        assertEquals(2, response.availableOrbitTransforms.size)
        assertEquals(null, response.error)
    }

    @Test
    fun recognizesTransformedPolyhedronAsCatalogSeed() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("I", listOf("t"), "c"),
                detectSeed = true,
            )
        )

        assertEquals("tI", response.recognizedSeedTag)
        assertEquals("Truncated Icosahedron", response.polyName)
    }

    @Test
    fun recognizesCatalogSeedReachedThroughEquivalentConstruction() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("O", listOf("a"), "c"),
                detectSeed = true,
            )
        )

        assertEquals("aC", response.recognizedSeedTag)
    }

    @Test
    fun suggestsSnubDodecahedronForDualPentagonalHexecontahedron() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("dsD", listOf("d"), "c"),
                detectSeed = true,
            )
        )

        assertEquals("sD", response.recognizedSeedTag)
    }

    @Test
    fun recognizesProperChiralityOfPentagonalHexecontahedronFromDualSnubIcosahedron() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("I", listOf("s", "d"), "c"),
                detectSeed = true,
            )
        )

        assertEquals("dsD'", response.recognizedSeedTag)

        val flippedResponse = evaluateCore(
            CoreRequest(
                state = CoreState("I", listOf("s'", "d"), "c"),
                detectSeed = true,
            )
        )
        assertEquals("dsD", flippedResponse.recognizedSeedTag)
    }

    @Test
    fun recognizesFlippedSnubTransformsAsFlippedCatalogSeeds() = runTest {
        for ((seedTag, recognizedSeedTag, polyName) in listOf(
            Triple("C", "sC'", "Snub' Cube"),
            Triple("D", "sD'", "Snub' Dodecahedron"),
        )) {
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState(seedTag, listOf("s'"), "c"),
                    detectSeed = true,
                )
            )

            assertEquals(recognizedSeedTag, response.recognizedSeedTag)
            assertEquals(polyName, response.polyName)
            assertEquals(listOf("s'"), response.validTransformTags)
            assertNull(response.error)
        }
    }

    @Test
    fun skipsCatalogDetectionUnlessExplicitlyRequested() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("I", listOf("t"), "c"),
                detectSeed = false,
            )
        )

        assertEquals(null, response.recognizedSeedTag)
    }

    @Test
    fun producesTopologyAnimationInsideCore() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("T", listOf("t"), "c"),
                previousState = CoreState("T", emptyList(), "c"),
                animationDuration = 0.5,
            )
        )

        val animation = response.animation.single()
        assertEquals(0.5, animation.duration)
        assertTrue(animation.previousFraction <= 0.001)
        assertTrue(animation.targetFraction >= 0.999)
    }

    @Test
    fun responseRoundTripsAcrossJsonBoundary() = runTest {
        val response = evaluateCore(CoreRequest(CoreState("O", listOf("d"), "m")))
        val encoded = CoreJson.encodeToString(response)
        val decoded = CoreJson.decodeFromString<CoreResponse>(encoded)

        assertEquals(response.polyName, decoded.polyName)
        assertEquals(response.poly.fev(), decoded.poly.fev())
        assertNotNull(decoded.poly)
    }

    @Test
    fun evaluatesNewConwayTransformsWithChiralityAndMonotonicProgress() = runTest {
        val progress = mutableListOf<CoreProgress>()
        val response = evaluateCore(
            CoreRequest(CoreState("T", listOf("p'", "w'", "q"), "c")),
            progress::add,
        )

        assertNull(response.error)
        assertEquals(listOf("p'", "w'", "q"), response.validTransformTags)
        assertEquals("Quinto Whirl' Propeller' Tetrahedron", response.polyName)
        assertEquals(FEV(496, 1260, 766), response.poly.fev())
        assertTrue(response.poly.isCanonical())
        assertStageProgress(progress, lastTransformIndex = 2)
    }

    @Test
    fun chiralityFlipDoesNotInterpolateThroughCollapsedGeometry() = runTest {
        for ((defaultTag, flippedTag) in listOf("p" to "p'", "w" to "w'")) {
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState("C", listOf(flippedTag), "c"),
                    previousState = CoreState("C", listOf(defaultTag), "c"),
                    animationDuration = 0.5,
                )
            )

            assertNull(response.error)
            assertTrue(response.animation.isEmpty(), "$defaultTag -> $flippedTag")
        }
    }

    @Test
    fun evaluatesCantellateChamferSnubCanonicalChain() = runTest {
        val progress = mutableListOf<CoreProgress>()
        val response = evaluateCore(
            CoreRequest(
                CoreState(
                    seedTag = "tC",
                    transformTags = listOf("e", "c", "s", "o"),
                    scaleTag = "c",
                )
            ),
            progress::add,
        )

        assertEquals(null, response.error, "The complete transform chain must succeed")
        assertEquals(listOf("e", "c", "s", "o"), response.validTransformTags)
        assertEquals(1730, response.poly.fs.size)
        assertEquals(2880, response.poly.es.size)
        assertEquals(1152, response.poly.vs.size)
        assertTrue(response.poly.isCanonical(), "The output must satisfy the canonical representation invariants")
        assertStageProgress(progress, lastTransformIndex = 3)
        assertTrue(
            progress.any { it.transformIndex == 3 && it.done in 1..99 },
            "Canonicalization must report intermediate progress on the Canonical stage",
        )
    }

    private fun assertStageProgress(progress: List<CoreProgress>, lastTransformIndex: Int) {
        assertEquals((0..lastTransformIndex).toList(), progress.map(CoreProgress::transformIndex).distinct())
        for (transformIndex in 0..lastTransformIndex) {
            val stageProgress = progress.filter { it.transformIndex == transformIndex }.map(CoreProgress::done)
            assertEquals(0, stageProgress.first(), "Stage $transformIndex must announce itself before starting")
            assertEquals(100, stageProgress.last(), "Stage $transformIndex must report completion")
            assertTrue(
                stageProgress.zipWithNext().all { (previous, next) -> next >= previous },
                "Stage $transformIndex progress must be monotonic: $stageProgress",
            )
        }
    }
}
