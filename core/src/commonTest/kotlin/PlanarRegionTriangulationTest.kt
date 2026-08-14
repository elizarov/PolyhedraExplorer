package polyhedra.core

import polyhedra.model.poly.triangulatePlanarRegion
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.plus
import polyhedra.model.util.times
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlanarRegionTriangulationTest {
    @Test
    fun triangulatesAConcaveRegionWithoutFillingItsHole() {
        val outer = polygon(
            0.0 to 0.0,
            5.0 to 0.0,
            5.0 to 5.0,
            3.0 to 3.0,
            0.0 to 5.0,
        )
        val hole = polygon(1.0 to 1.0, 1.0 to 2.0, 2.0 to 2.0, 2.0 to 1.0)
        val result = triangulatePlanarRegion(outer, listOf(hole), Vec3(0.0, 0.0, 1.0))

        val triangleArea = result.triangles.sumOf { triangle ->
            val a = result.vertices[triangle.a]
            val b = result.vertices[triangle.b]
            val c = result.vertices[triangle.c]
            ((b - a) cross (c - a)).norm / 2.0
        }
        assertEquals(19.0, triangleArea, 1e-10)
        for (triangle in result.triangles) {
            val center = (result.vertices[triangle.a] + result.vertices[triangle.b] +
                result.vertices[triangle.c]) * (1.0 / 3.0)
            assertFalse(center.x > 1.0 && center.x < 2.0 && center.y > 1.0 && center.y < 2.0)
        }
    }

    @Test
    fun triangulatesMultipleHoles() {
        val outer = polygon(0.0 to 0.0, 6.0 to 0.0, 6.0 to 4.0, 0.0 to 4.0)
        val holes = listOf(
            polygon(1.0 to 1.0, 1.0 to 2.0, 2.0 to 2.0, 2.0 to 1.0),
            polygon(4.0 to 1.0, 4.0 to 3.0, 5.0 to 3.0, 5.0 to 1.0),
        )
        val result = triangulatePlanarRegion(outer, holes, Vec3(0.0, 0.0, 1.0))
        val triangleArea = result.triangles.sumOf { triangle ->
            val a = result.vertices[triangle.a]
            val b = result.vertices[triangle.b]
            val c = result.vertices[triangle.c]
            ((b - a) cross (c - a)).norm / 2.0
        }

        assertEquals(21.0, triangleArea, 1e-10)
    }

    private fun polygon(vararg points: Pair<Double, Double>): List<Vec3> =
        points.map { (x, y) -> Vec3(x, y, 0.0) }
}
