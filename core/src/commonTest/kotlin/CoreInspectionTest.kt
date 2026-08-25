package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.formatCoreProgress
import polyhedra.core.api.inspectCompactConfiguration
import polyhedra.core.api.parseCompactCoreConfiguration
import polyhedra.model.api.CoreProgress
import polyhedra.model.api.CoreState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreInspectionTest {
    @Test
    fun compactConfigurationAcceptsACompleteUrlAndPreservesUrlData() {
        val parsed = parseCompactCoreConfiguration(
            "http://127.0.0.1:8765/#/a(+%CE%B1)s(eC)t(G~l=3,d)v(fw(0.03333333)fr(0.02))",
        )

        assertTrue(parsed.normalized.contains("a(+α)"))
        assertEquals(CoreState("eC", listOf("G~l=3", "d"), "c"), parsed.state)
        assertEquals(0.02, parsed.rimWidth)
        assertEquals(0.03333333, parsed.faceWidth)
    }

    @Test
    fun reportIncludesCoreTimingAndExplicitOrbitMemberships() = runTest {
        val inspection = inspectCompactConfiguration(
            "s(C)",
            calculateTweakRanges = false,
            detectSeed = false,
        )

        assertTrue(inspection.report.contains("Core construction:"))
        assertTrue(inspection.report.contains("Face orbits (1)"))
        assertTrue(inspection.report.contains("members=0-5"))
        assertTrue(inspection.report.contains("Kind/orbit consistency: OK"))
    }

    @Test
    fun progressLineIdentifiesTheActiveTransform() {
        assertEquals(
            "Core [########--------]  50% 2/2  d",
            formatCoreProgress(CoreProgress(transformIndex = 1, done = 50), listOf("G~l=3", "d"), barWidth = 16),
        )
    }
}
