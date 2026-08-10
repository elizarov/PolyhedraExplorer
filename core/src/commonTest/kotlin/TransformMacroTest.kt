package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.poly.geometryFingerprint
import polyhedra.model.poly.fev
import polyhedra.core.api.evaluateCore
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.TransformMacros
import polyhedra.model.api.findTransformPrefixReplacement
import polyhedra.model.api.parseTransformTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransformMacroTest {
    private fun replacement(vararg tags: String) =
        findTransformPrefixReplacement(tags.map { tag -> requireNotNull(tag.parseTransformTag()) })

    private fun replacement(tags: List<String>) =
        findTransformPrefixReplacement(tags.map { tag -> requireNotNull(tag.parseTransformTag()) })

    @Test
    fun parameterOnlyChangeDoesNotSuggestReplacingAnOperationWithItself() {
        assertNull(replacement("t~d=0.7"))
        assertNull(replacement("g'~r=0.8"))
    }

    @Test
    fun everyMacroMatchesItsExpandedTransformSequence() = runTest {
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

    @Test
    fun compositionAwareRectificationProducesRegularCantellatedAndBevelledGeometry() = runTest {
        val cantellated = evaluateCore(
            CoreRequest(CoreState("C", listOf("a", "a"), "c"), detectSeed = true)
        )
        val bevelled = evaluateCore(
            CoreRequest(CoreState("C", listOf("a", "t"), "c"), detectSeed = true)
        )

        assertEquals("eC", cantellated.recognizedSeedTag)
        assertEquals("bC", bevelled.recognizedSeedTag)
    }

    @Test
    fun findsEquivalentSingleOperationsAtTheDisplayedPrefix() {
        for (macro in TransformMacros) {
            val match = replacement(listOf("c") + macro.expansionTags)
            assertEquals(macro.tag, match?.replacementTag, macro.name)
            assertEquals(1, match?.startIndex, macro.name)
            assertNull(
                replacement(macro.tag),
                "Do not suggest an existing ${macro.name} macro",
            )
        }

        val nestedOrtho = replacement("t", "d", "e", "d")
        assertEquals("O", nestedOrtho?.replacementTag)
        assertEquals(1, nestedOrtho?.startIndex)
    }

    @Test
    fun simplifiesDualNeedleToTruncated() = runTest {
        // Logical order is reversed for display, so these tags render as "Dual Needle".
        val replacement = replacement("N", "d")

        assertEquals("t", replacement?.replacementTag)
        assertEquals(0, replacement?.startIndex)

        val dualNeedle = evaluateCore(CoreRequest(CoreState("C", listOf("N", "d"), "c")))
        val truncated = evaluateCore(CoreRequest(CoreState("C", listOf("t"), "c")))
        assertTrue(
            dualNeedle.poly.geometryFingerprint().matches(truncated.poly.geometryFingerprint()),
            "Dual Needle and Truncated should be geometrically congruent",
        )
    }

    @Test
    fun prefersTheLongestReplaceableDisplayedPrefix() {
        val replacement = replacement("c", "d", "d", "t")

        assertEquals("t", replacement?.replacementTag)
        assertEquals(1, replacement?.startIndex)
    }

    @Test
    fun prefersTheLongestFormalReplacementEvenWhenItExposesCompositionFusion() {
        val replacement = replacement("a", "d", "d", "t")

        assertEquals("b", replacement?.replacementTag)
        assertEquals(0, replacement?.startIndex)
    }

    @Test
    fun prefixReplacementPreservesSnubAndGyroChirality() = runTest {
        val flippedGyro = replacement("d", "s'", "d")
        val flippedSnub = replacement("d", "g'", "d")

        assertEquals("g'", flippedGyro?.replacementTag)
        assertEquals("s'", flippedSnub?.replacementTag)

        val macro = evaluateCore(CoreRequest(CoreState("C", listOf("g'"), "c")))
        val expanded = evaluateCore(CoreRequest(CoreState("C", listOf("d", "s'", "d"), "c")))
        assertEquals("Gyro' Cube", macro.polyName)
        assertTrue(
            macro.poly.geometryFingerprint().matches(expanded.poly.geometryFingerprint()),
            "Flipped Gyro must match its chirality-preserving expansion",
        )
    }
}
