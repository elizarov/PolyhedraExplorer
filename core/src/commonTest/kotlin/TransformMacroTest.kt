import polyhedra.common.poly.fev
import polyhedra.common.poly.geometryFingerprint
import polyhedra.common.util.runSynchronously
import polyhedra.core.api.CoreRequest
import polyhedra.core.api.CoreState
import polyhedra.core.api.TransformMacros
import polyhedra.core.api.evaluateCore
import polyhedra.core.api.findTransformPrefixReplacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransformMacroTest {
    @Test
    fun everyMacroMatchesItsExpandedTransformSequence() {
        runSynchronously {
            for (macro in TransformMacros) {
                val macroResponse = evaluateCore(CoreRequest(CoreState("C", listOf(macro.tag), "c")))
                val expandedResponse = evaluateCore(CoreRequest(CoreState("C", macro.expansionTags, "c")))

                assertNull(macroResponse.error, macro.name)
                assertNull(expandedResponse.error, "Expanded ${macro.name}")
                assertEquals(listOf(macro.tag), macroResponse.validTransformTags, macro.name)
                assertEquals("${macro.name} Cube", macroResponse.polyName, macro.name)
                assertEquals(expandedResponse.poly.fev(), macroResponse.poly.fev(), "${macro.name} F/E/V")
                assertTrue(
                    expandedResponse.poly.geometryFingerprint().matches(macroResponse.poly.geometryFingerprint()),
                    "${macro.name} geometry",
                )
            }
        }
    }

    @Test
    fun compositionAwareRectificationProducesRegularCantellatedAndBevelledGeometry() {
        runSynchronously {
            val cantellated = evaluateCore(
                CoreRequest(CoreState("C", listOf("a", "a"), "c"), detectSeed = true)
            )
            val bevelled = evaluateCore(
                CoreRequest(CoreState("C", listOf("a", "t"), "c"), detectSeed = true)
            )

            assertEquals("eC", cantellated.recognizedSeedTag)
            assertEquals("bC", bevelled.recognizedSeedTag)
        }
    }

    @Test
    fun findsEquivalentSingleOperationsAtTheDisplayedPrefix() {
        for (macro in TransformMacros) {
            val match = findTransformPrefixReplacement(listOf("c") + macro.expansionTags)
            assertEquals(macro.tag, match?.replacementTag, macro.name)
            assertEquals(1, match?.startIndex, macro.name)
            assertNull(
                findTransformPrefixReplacement(listOf(macro.tag)),
                "Do not suggest an existing ${macro.name} macro",
            )
        }

        val nestedOrtho = findTransformPrefixReplacement(listOf("t", "d", "e", "d"))
        assertEquals("O", nestedOrtho?.replacementTag)
        assertEquals(1, nestedOrtho?.startIndex)
    }

    @Test
    fun simplifiesDualNeedleToTruncated() {
        // Logical order is reversed for display, so these tags render as "Dual Needle".
        val replacement = findTransformPrefixReplacement(listOf("N", "d"))

        assertEquals("t", replacement?.replacementTag)
        assertEquals(0, replacement?.startIndex)

        runSynchronously {
            val dualNeedle = evaluateCore(CoreRequest(CoreState("C", listOf("N", "d"), "c")))
            val truncated = evaluateCore(CoreRequest(CoreState("C", listOf("t"), "c")))
            assertTrue(
                dualNeedle.poly.geometryFingerprint().matches(truncated.poly.geometryFingerprint()),
                "Dual Needle and Truncated should be geometrically congruent",
            )
        }
    }

    @Test
    fun prefersTheLongestReplaceableDisplayedPrefix() {
        val replacement = findTransformPrefixReplacement(listOf("c", "d", "d", "t"))

        assertEquals("t", replacement?.replacementTag)
        assertEquals(1, replacement?.startIndex)
    }

    @Test
    fun prefersTheLongestFormalReplacementEvenWhenItExposesCompositionFusion() {
        val replacement = findTransformPrefixReplacement(listOf("a", "d", "d", "t"))

        assertEquals("b", replacement?.replacementTag)
        assertEquals(0, replacement?.startIndex)
    }
}
