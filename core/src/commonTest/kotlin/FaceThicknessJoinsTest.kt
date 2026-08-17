package polyhedra.core

import polyhedra.core.poly.*
import polyhedra.model.poly.FaceThicknessJoins
import polyhedra.model.poly.keepsConfiguredRimWidth
import polyhedra.model.poly.outwardNormal
import polyhedra.model.poly.size
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaceThicknessJoinsTest {
    @Test
    fun innerFacePlanesMeetOnEveryPlatonicEdgeBisector() {
        val solids = listOf(
            Seed.Tetrahedron.poly,
            Seed.Cube.poly,
            Seed.Octahedron.poly,
            Seed.Dodecahedron.poly,
            Seed.Icosahedron.poly,
        )
        for (poly in solids) {
            val joins = FaceThicknessJoins(poly)
            for (edge in poly.directedEdges) {
                val direction = joins.edgeDirection(edge)
                assertEquals(1.0, edge.r.outwardNormal * direction, 1e-9)
                assertEquals(1.0, edge.l.outwardNormal * direction, 1e-9)
                assertEquals(0.0, (edge.r.outwardNormal - edge.l.outwardNormal) * direction, 1e-9)
            }
        }
    }

    @Test
    fun requiredRimIsWidthOverTangentOfHalfInteriorDihedral() {
        for (poly in listOf(Seed.Tetrahedron.poly, Seed.Cube.poly, Seed.Icosahedron.poly)) {
            val joins = FaceThicknessJoins(poly)
            for (edge in poly.es) {
                val normalAngle = acos(
                    (edge.r.outwardNormal * edge.l.outwardNormal).coerceIn(-1.0, 1.0),
                )
                val expected = 1.0 / tan((PI - normalAngle) / 2.0)
                assertEquals(expected, joins.rimFactor(edge), 1e-9)
            }
        }
    }

    @Test
    fun starPrismKeepsEveryVisibleTopRimAtTheConfiguredWidth() {
        val poly = requireNotNull("SP5_2".toSeedOrNull()).poly
        val joins = FaceThicknessJoins(poly)
        val rim = 0.12
        val width = 0.112
        assertTrue(poly.fs.all(poly::keepsConfiguredRimWidth))
        assertTrue(poly.fs.all { face ->
            joins.effectiveRimWidths(face, rim, width).all { effective ->
                abs(effective - rim) <= 1e-9
            }
        })
    }

    @Test
    fun localJoinsAreFiniteSharedAndIndependentOfRimSettings() {
        val tags = listOf("C", "T", "P3", "SP5_2", "SY5_2", "SY19_9", "SD")
        val settings = listOf(
            0.015 to 0.015,
            0.015 to 0.129,
            0.12 to 0.05,
            0.12 to 0.12,
        )
        for (tag in tags) {
            val poly = requireNotNull(tag.toSeedOrNull()).poly
            val joins = FaceThicknessJoins(poly)
            for (edge in poly.es) {
                val direction = joins.edgeDirection(edge)
                assertTrue(direction.x.isFinite() && direction.y.isFinite() && direction.z.isFinite())
                assertTrue((direction - joins.edgeDirection(edge.reversed)).norm <= 1e-9)
            }
            for (vertex in poly.vs) {
                val incident = poly.fs.filter { face -> vertex in face.fvs }
                val directions = incident.map { face -> joins.vertexDirection(face, vertex) }
                assertTrue(directions.isNotEmpty())
                assertTrue(directions.all { direction ->
                    direction.x.isFinite() && direction.y.isFinite() && direction.z.isFinite()
                })
                assertTrue(directions.drop(1).all { direction ->
                    (direction - directions.first()).norm <= 1e-9
                }, "$tag vertex ${vertex.id} has disconnected inner corners")
            }
            for ((rim, width) in settings) for (face in poly.fs) {
                val effective = joins.effectiveRimWidths(face, rim, width)
                assertEquals(face.size, effective.size)
                assertTrue(effective.all { value -> value.isFinite() && value >= rim - 1e-12 })
            }
        }
    }
}
