package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.transform.isCanonical
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanonicalRobustnessTest {
    private data class Case(
        val seedTag: String,
        val transformTags: List<String>,
    ) {
        val notation: String
            get() = "s($seedTag)t(${transformTags.joinToString(",")})"
    }

    @Test
    fun canonicalizesResearchRegressions() = runTest {
        for (case in cases) {
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState(case.seedTag, case.transformTags + "o", "c"),
                    calculateTweakRanges = false,
                )
            )
            assertNull(response.error, case.notation)
            assertTrue(response.poly.isCanonical(), case.notation)
        }
    }

    private companion object {
        val cases = listOf(
            Case("I", listOf("c", "N", "a[C]~d=0.25")),
            Case("dtT", listOf("k")),
            Case("A8", listOf("a", "t")),
            Case("dtO", listOf("a[B]", "e")),
            Case("daD", listOf("N", "k")),
            Case("dtI", listOf("a[B]", "e")),
            Case("eC", listOf("k", "k")),
            Case("dtT", listOf("t[B]", "N")),
            Case("tC", listOf("N")),
            Case("P5", listOf("m", "k")),
            Case("A5", listOf("k", "j", "c")),
            Case("C", listOf("z", "N")),
            Case("A8", listOf("N")),
            Case("bD", listOf("N")),
            Case("deC", listOf("t", "N")),
            Case("A5", listOf("m")),
            Case("D", listOf("c", "N")),
            Case("P3", listOf("N")),
            Case("P8", listOf("N")),
            Case("D", listOf("t[A]", "N")),
            Case("C", listOf("t", "N")),
            Case("tT", listOf("t[A]", "t", "t")),
            Case("B8", listOf("z", "t")),
            Case("Y4", listOf("O~c=0.25", "k", "j", "e")),
        )
    }
}
