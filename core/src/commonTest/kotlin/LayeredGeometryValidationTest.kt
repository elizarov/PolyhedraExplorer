package polyhedra.core

import polyhedra.core.poly.analyzeGeometry
import polyhedra.core.poly.Cube
import polyhedra.core.poly.polyhedron
import polyhedra.core.poly.Seed
import polyhedra.core.poly.validateContract
import polyhedra.core.poly.validateProperGeometry
import polyhedra.core.poly.validateRenderableImmersion
import polyhedra.model.api.PolyhedronContract
import polyhedra.model.api.SurfaceIntersectionClass
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.util.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LayeredGeometryValidationTest {
    @Test
    fun ordinarySeedSatisfiesAllThreeContracts() {
        val poly = Seed.Cube.poly

        poly.validateRenderableImmersion()
        assertEquals(PolyhedronContract.EmbeddedBoundary, poly.analyzeGeometry().strongestContract)
        assertEquals(
            PolyhedronContract.EmbeddedBoundary,
            poly.validateContract(PolyhedronContract.EmbeddedBoundary).strongestContract,
        )
    }

    @Test
    fun starPrismIsRenderableButNotAnEmbeddedBoundary() {
        val poly = starPrism(5, 2)

        poly.validateRenderableImmersion()
        val analysis = poly.analyzeGeometry()
        assertEquals(PolyhedronContract.RenderableImmersion, analysis.strongestContract)
        assertTrue(analysis.intersectionCounts.getValue(SurfaceIntersectionClass.SelfCrossingFace) > 0)
        assertFailsWith<IllegalArgumentException> {
            poly.validateContract(PolyhedronContract.EmbeddedBoundary)
        }
        assertFailsWith<IllegalArgumentException> { poly.validateProperGeometry() }
    }

    @Test
    fun distinctCoincidentSourceVerticesAreRejectedAtRenderableLayer() {
        val poly = polyhedron {
            vertex(1.0, 1.0, -1.0)
            vertex(-1.0, 1.0, -1.0)
            vertex(-1.0, -1.0, -1.0)
            vertex(1.0, -1.0, -1.0)
            vertex(1.0, 1.0, 1.0)
            vertex(-1.0, 1.0, 1.0)
            vertex(-1.0, -1.0, 1.0)
            vertex(1.0, 1.0, -1.0) // coincides with vertex 0 but has distinct identity
            face(0, 1, 2, 3)
            face(0, 4, 5, 1)
            face(1, 5, 6, 2)
            face(2, 6, 7, 3)
            face(3, 7, 4, 0)
            face(4, 7, 6, 5)
        }

        val error = assertFailsWith<IllegalArgumentException> { poly.validateRenderableImmersion() }
        assertTrue(error.message.orEmpty().contains("coincident positions"))
    }

    private fun starPrism(n: Int, q: Int): Polyhedron = polyhedron {
        val bottom = List(n) { index ->
            val angle = 2.0 * PI * index / n
            vertex(Vec3(cos(angle), sin(angle), -0.35))
        }
        val top = List(n) { index ->
            val angle = 2.0 * PI * index / n
            vertex(Vec3(cos(angle), sin(angle), 0.35))
        }
        face(List(n) { index -> bottom[(index * q) % n].id }, FaceKind(0))
        face(List(n) { index -> top[((n - index) * q) % n].id }, FaceKind(0))
        for (index in 0 until n) {
            val next = (index + q) % n
            face(listOf(bottom[index].id, top[index].id, top[next].id, bottom[next].id), FaceKind(1))
        }
    }
}
