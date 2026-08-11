package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.Seeds
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.TransformMacros
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeedConstructionTest {
    @Test
    fun everyDiagramEdgeConstructsItsCatalogTarget() = runTest {
        for (edge in DIAGRAM_CONSTRUCTION_EDGES) {
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState(edge.sourceTag, edge.transformTags, "c"),
                    detectSeed = true,
                )
            )

            assertNull(response.error, edge.description)
            assertEquals(edge.transformTags, response.validTransformTags, edge.description)
            assertEquals(edge.targetTag, response.recognizedSeedTag, edge.description)
        }
    }

    @Test
    fun diagramReachesEveryFixedSeedFromTetrahedronUsingOnlyPrimitives() {
        assertEquals(
            49,
            DIAGRAM_CONSTRUCTION_EDGES.size,
            "Keep the documented directed-edge count current",
        )
        val macroTags = TransformMacros.mapTo(mutableSetOf()) { it.tag }
        assertTrue(
            DIAGRAM_CONSTRUCTION_EDGES.flatMap { it.transformTags }.none { it in macroTags },
            "The seed diagram must expand every macro into primitive transforms",
        )

        val reachable = diagramPathsFromTetrahedron().keys
        assertEquals(Seeds.mapTo(mutableSetOf()) { it.tag }, reachable)
    }

    @Test
    fun everyFixedSeedCanBeConstructedAlongItsFullDiagramPath() = runTest {
        val paths = diagramPathsFromTetrahedron()
        for (seed in Seeds) {
            val transformTags = paths.getValue(seed.tag)
            if (transformTags.isEmpty()) continue // Tetrahedron is the one basic seed.
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState("T", transformTags, "c"),
                    detectSeed = true,
                )
            )

            assertNull(response.error, "T + ${transformTags.joinToString(" -> ")} -> ${seed.tag}")
            assertEquals(seed.tag, response.recognizedSeedTag, "Full diagram path to ${seed.tag}")
        }
    }
}

private fun diagramPathsFromTetrahedron(): Map<String, List<String>> {
    val paths = mutableMapOf("T" to emptyList<String>())
    do {
        val previousSize = paths.size
        for (edge in DIAGRAM_CONSTRUCTION_EDGES) {
            val sourcePath = paths[edge.sourceTag] ?: continue
            paths.putIfAbsent(edge.targetTag, sourcePath + edge.transformTags)
        }
    } while (paths.size != previousSize)
    return paths
}

private data class ConstructionEdge(
    val sourceTag: String,
    val transformTags: List<String>,
    val targetTag: String,
) {
    val description: String get() = "$sourceTag + ${transformTags.joinToString(" -> ")} -> $targetTag"
}

private fun edge(sourceTag: String, vararg construction: String): ConstructionEdge =
    ConstructionEdge(sourceTag, construction.dropLast(1), construction.last())

/** Exact primitive-transform edges drawn in docs/seeds.md. */
private val DIAGRAM_CONSTRUCTION_EDGES = listOf(
    // Platonic foundation: one basic seed reaches the other four.
    edge("T", "a", "O"),
    edge("O", "d", "C"),
    edge("T", "s", "I"),
    edge("I", "d", "D"),

    // Archimedean constructions.
    edge("T", "t", "tT"),
    edge("C", "a", "aC"),
    edge("C", "t", "tC"),
    edge("O", "t", "tO"),
    edge("C", "a", "a", "eC"),
    edge("C", "a", "t", "bC"),
    edge("C", "s", "sC"),
    edge("C", "s'", "sC'"),
    edge("D", "a", "aD"),
    edge("D", "t", "tD"),
    edge("I", "t", "tI"),
    edge("D", "a", "a", "eD"),
    edge("D", "a", "t", "bD"),
    edge("D", "s", "sD"),
    edge("D", "s'", "sD'"),

    // Catalan/Archimedean duality.
    edge("tT", "d", "dtT"),
    edge("aC", "d", "daC"),
    edge("tC", "d", "dtC"),
    edge("tO", "d", "dtO"),
    edge("eC", "d", "deC"),
    edge("bC", "d", "dbC"),
    edge("sC", "d", "dsC"),
    edge("sC'", "d", "dsC'"),
    edge("aD", "d", "daD"),
    edge("tD", "d", "dtD"),
    edge("tI", "d", "dtI"),
    edge("eD", "d", "deD"),
    edge("bD", "d", "dbD"),
    edge("sD", "d", "dsD"),
    edge("sD'", "d", "dsD'"),

    // Dual is involutive: test the reverse direction of every bidirectional diagram edge too.
    edge("dtT", "d", "tT"),
    edge("daC", "d", "aC"),
    edge("dtC", "d", "tC"),
    edge("dtO", "d", "tO"),
    edge("deC", "d", "eC"),
    edge("dbC", "d", "bC"),
    edge("dsC", "d", "sC"),
    edge("dsC'", "d", "sC'"),
    edge("daD", "d", "aD"),
    edge("dtD", "d", "tD"),
    edge("dtI", "d", "tI"),
    edge("deD", "d", "eD"),
    edge("dbD", "d", "bD"),
    edge("dsD", "d", "sD"),
    edge("dsD'", "d", "sD'"),
)
