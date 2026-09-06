package polyhedra.core

import polyhedra.core.poly.validateDistinctVertexPositions
import polyhedra.model.util.Vec3
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VertexPositionValidationTest {
    @Test
    fun nearbyVerticesAcrossCellBoundariesAreRejected() {
        for (sign in listOf(-1.0, 1.0)) {
            val points = listOf(
                Vec3(sign * 0.99, sign * 0.99, sign * 0.99),
                Vec3(sign * 1.01, sign * 1.01, sign * 1.01),
            )
            assertFailsWith<IllegalArgumentException> {
                validateDistinctVertexPositions(points, intArrayOf(0, 0), 1.0)
            }
            // Coincident or nearby points in distinct compound members are allowed.
            validateDistinctVertexPositions(points, intArrayOf(0, 1), 1.0)
        }
    }

    @Test
    fun exactDistanceAndComponentRulesArePreserved() {
        for (scale in listOf(1e-9, 1.0, 1e9)) {
            val origin = Vec3(0.0, 0.0, 0.0)
            assertFailsWith<IllegalArgumentException> {
                validateDistinctVertexPositions(listOf(origin, Vec3(scale, 0.0, 0.0)), intArrayOf(0, 0), scale)
            }
            validateDistinctVertexPositions(
                listOf(origin, Vec3(scale * 1.001, 0.0, 0.0)), intArrayOf(0, 0), scale,
            )
            validateDistinctVertexPositions(listOf(origin, origin), intArrayOf(0, 1), scale)
        }
        // A bucket is only a broad phase, not a replacement for Euclidean distance.
        validateDistinctVertexPositions(
            listOf(Vec3(0.0, 0.0, 0.0), Vec3(0.9, 0.9, 0.9)), intArrayOf(0, 0), 1.0,
        )
        assertFailsWith<IllegalArgumentException> {
            validateDistinctVertexPositions(
                listOf(Vec3(0.0, 0.0, 0.0), Vec3(-0.0, 0.0, 0.0)), intArrayOf(0, 0), 0.0,
            )
        }
    }

    @Test
    fun spatialValidationMatchesAllPairsReference() {
        val random = Random(20260906)
        repeat(250) { sample ->
            val tolerance = random.nextDouble(0.01, 1.0)
            val points = List(40) {
                Vec3(random.nextDouble(-4.0, 4.0), random.nextDouble(-4.0, 4.0), random.nextDouble(-4.0, 4.0))
            }
            val components = IntArray(points.size) { random.nextInt(3) }
            val expected = points.indices.all { first ->
                (first + 1 until points.size).all { second ->
                    components[first] != components[second] || (points[first] - points[second]).norm > tolerance
                }
            }
            assertEquals(expected, runCatching {
                validateDistinctVertexPositions(points, components, tolerance)
            }.isSuccess, "sample $sample")
        }
    }

    @Test
    fun validatesLargeSeparatedPointSetWithoutPairwiseWork() {
        val points = List(40_000) { index ->
            Vec3((index % 200).toDouble(), (index / 200).toDouble(), 0.0)
        }
        validateDistinctVertexPositions(points, IntArray(points.size), 0.01)
    }
}
