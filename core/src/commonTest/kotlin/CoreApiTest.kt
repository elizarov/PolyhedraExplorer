import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import polyhedra.common.poly.fev
import polyhedra.common.transform.isCanonical
import polyhedra.common.util.runSynchronously
import polyhedra.core.api.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoreApiTest {
    @Test
    fun evaluatesCompleteTransformPipeline() {
        runSynchronously {
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState("C", listOf("t"), "c"),
                )
            )

            assertEquals(14, response.poly.fs.size)
            assertEquals(36, response.poly.es.size)
            assertEquals(24, response.poly.vs.size)
            assertEquals(listOf("t"), response.validTransformTags)
            assertEquals(2, response.availableDrops.size)
            assertEquals(null, response.error)
        }
    }

    @Test
    fun recognizesTransformedPolyhedronAsCatalogSeed() {
        runSynchronously {
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState("I", listOf("t"), "c"),
                    detectSeed = true,
                )
            )

            assertEquals("tI", response.recognizedSeedTag)
            assertEquals("Truncated Icosahedron", response.polyName)
        }
    }

    @Test
    fun recognizesCatalogSeedReachedThroughEquivalentConstruction() {
        runSynchronously {
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState("O", listOf("a"), "c"),
                    detectSeed = true,
                )
            )

            assertEquals("aC", response.recognizedSeedTag)
        }
    }

    @Test
    fun suggestsSnubDodecahedronForDualPentagonalHexecontahedron() {
        runSynchronously {
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState("dsD", listOf("d"), "c"),
                    detectSeed = true,
                )
            )

            assertEquals("sD", response.recognizedSeedTag)
        }
    }

    @Test
    fun recognizesPentagonalHexecontahedronFromDualSnubIcosahedron() {
        runSynchronously {
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState("I", listOf("s", "d"), "c"),
                    detectSeed = true,
                )
            )

            assertEquals("dsD", response.recognizedSeedTag)
        }
    }

    @Test
    fun skipsCatalogDetectionUnlessExplicitlyRequested() {
        runSynchronously {
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState("I", listOf("t"), "c"),
                    detectSeed = false,
                )
            )

            assertEquals(null, response.recognizedSeedTag)
        }
    }

    @Test
    fun producesTopologyAnimationInsideCore() {
        runSynchronously {
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
    }

    @Test
    fun responseRoundTripsAcrossJsonBoundary() {
        runSynchronously {
            val response = evaluateCore(CoreRequest(CoreState("O", listOf("d"), "m")))
            val encoded = CoreJson.encodeToString(response)
            val decoded = CoreJson.decodeFromString<CoreResponse>(encoded)

            assertEquals(response.polyName, decoded.polyName)
            assertEquals(response.poly.fev(), decoded.poly.fev())
            assertNotNull(decoded.poly)
        }
    }

    @Test
    fun evaluatesCantellateChamferSnubCanonicalChain() {
        runSynchronously {
            val progress = mutableListOf<Int>()
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
            assertTrue(progress.zipWithNext().all { (previous, next) -> next >= previous })
            assertEquals(100, progress.last())
        }
    }
}
