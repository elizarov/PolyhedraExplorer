package polyhedra.core

import polyhedra.core.poly.Seed
import polyhedra.core.poly.Tetrahedron
import polyhedra.core.poly.Cube
import polyhedra.core.poly.Octahedron
import polyhedra.core.poly.Dodecahedron
import polyhedra.core.poly.Icosahedron
import polyhedra.model.poly.FaceThicknessJoins
import polyhedra.model.poly.outwardNormal
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
                assertEquals(
                    0.0,
                    (edge.r.outwardNormal - edge.l.outwardNormal) * direction,
                    1e-9,
                    "${poly} edge $edge is not on the equal-offset angle bisector",
                )
            }
        }
    }

    @Test
    fun requiredRimIsWidthOverTangentOfHalfInteriorDihedral() {
        val solids = listOf(Seed.Tetrahedron.poly, Seed.Cube.poly, Seed.Icosahedron.poly)
        for (poly in solids) {
            val joins = FaceThicknessJoins(poly)
            for (edge in poly.es) {
                val normalAngle = acos(
                    (edge.r.outwardNormal * edge.l.outwardNormal).coerceIn(-1.0, 1.0),
                )
                val interiorDihedral = PI - normalAngle
                val expected = 1.0 / tan(interiorDihedral / 2.0)
                assertEquals(expected, joins.rimFactor(edge), 1e-9, "$poly edge $edge")
            }
        }
    }

    @Test
    fun cubeUnitWidthRequiresUnitRimAndHasSharedInnerCorners() {
        val poly = Seed.Cube.poly
        val joins = FaceThicknessJoins(poly)
        for (face in poly.fs) {
            assertTrue(joins.effectiveRimWidths(face, rim = 0.5, width = 1.0)
                .all { required -> abs(required - 1.0) <= 1e-9 })
            for (vertex in face.fvs) {
                val direction = joins.vertexDirection(face, vertex)
                val inner = vertex - direction
                val incident = vertex.directedEdges.flatMap { edge -> listOf(edge.l, edge.r) }
                    .distinctBy { incidentFace -> incidentFace.id }
                assertTrue(incident.all { incidentFace ->
                    abs(incidentFace.outwardNormal * inner - (incidentFace.d - 1.0)) <= 1e-9
                }, "Inner corner $inner does not lie on every incident inner plane")
            }
        }
    }

    @Test
    fun configuredRimWinsWhenItAlreadyReachesTheInnerBisector() {
        val poly = Seed.Icosahedron.poly
        val joins = FaceThicknessJoins(poly)
        val width = 0.1
        val rim = 0.05
        assertTrue(poly.fs.flatMap { joins.effectiveRimWidths(it, rim, width) }
            .all { effective -> abs(effective - rim) <= 1e-9 })
    }
}
