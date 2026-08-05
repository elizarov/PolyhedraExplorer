import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import polyhedra.common.poly.fev
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
}
