package polyhedra.core

import polyhedra.core.poly.Seed
import polyhedra.core.poly.Tetrahedron
import polyhedra.core.poly.Cube
import polyhedra.core.poly.Octahedron
import polyhedra.core.poly.Dodecahedron
import polyhedra.core.poly.Icosahedron
import polyhedra.core.poly.toSeedOrNull
import polyhedra.model.poly.FaceThicknessJoins
import polyhedra.model.poly.Face
import polyhedra.model.poly.outwardNormal
import polyhedra.model.poly.keepsConfiguredRimWidth
import polyhedra.model.poly.size
import polyhedra.model.util.cross
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
    fun starPyramidFiveHalvesUsesTheSameExactTriangleMitersAsARegularPyramid() {
        val width = 0.1
        val rimWidth = 0.05
        for (tag in listOf("Y5", "SY5_2")) {
            val poly = requireNotNull(tag.toSeedOrNull()).poly
            val joins = FaceThicknessJoins(poly)
            val allFaces = poly.fs.mapTo(linkedSetOf()) { face -> face.id }
            val rimFaceIds = allFaces.takeIf { poly.keepsConfiguredRimWidth }.orEmpty()
            val rimJoins = FaceThicknessJoins(poly, allFaces, rimFaceIds, rimWidth)
            for (face in poly.fs.filter { it.kind.id == 1 }) {
                for (vertex in face.fvs) {
                    val exact = joins.vertexDirection(face, vertex)
                    val bounded = joins.vertexDirection(face, vertex, width)
                    assertTrue((bounded - exact).norm <= 1e-9, "$tag changed a valid corner miter")
                    assertInside(face, vertex - bounded * width)
                    if (poly.keepsConfiguredRimWidth) {
                        val rimDirection = rimJoins.vertexDirection(face, vertex, width)
                        val normalPart = face.outwardNormal * rimDirection
                        val tangentialDistance =
                            (rimDirection - face.outwardNormal * normalPart).norm * width
                        assertTrue(tangentialDistance <= rimWidth + 1e-9)
                    }
                }
            }
            for (vertex in poly.vs) {
                val directions = poly.fs.filter { face -> vertex in face.fvs }
                    .map { face -> rimJoins.vertexDirection(face, vertex, width) }
                assertTrue(directions.drop(1).all { direction ->
                    (direction - directions.first()).norm <= 1e-8
                }, "$tag vertex ${vertex.id} has disconnected rim corners")
            }
        }
    }

    @Test
    fun pyramidNineteenNinthsStopsAtCollapsedMiterBoundariesAndKeepsCornersShared() {
        val poly = requireNotNull("SY19_9".toSeedOrNull()).poly
        val width = 0.1
        val betaFaces = poly.fs.filter { it.kind.id == 1 }
        val materialFaces = poly.fs.mapTo(linkedSetOf()) { face -> face.id }
        val joins = FaceThicknessJoins(
            poly,
            materialFaces,
            betaFaces.mapTo(linkedSetOf()) { face -> face.id },
            rimWidth = 0.05,
        )

        assertTrue(betaFaces.flatMap { face -> face.fvs.map { face to it } }.any { (face, vertex) ->
            joins.vertexDirection(face, vertex, width).norm < joins.vertexDirection(face, vertex).norm - 1e-9
        })
        for (face in betaFaces) for (vertex in face.fvs) {
            assertInside(face, vertex - joins.vertexDirection(face, vertex, width) * width)
        }
        for (vertex in poly.vs) {
            val directions = betaFaces.filter { face -> vertex in face.fvs }
                .map { face -> joins.vertexDirection(face, vertex, width) }
            if (directions.size > 1) {
                assertTrue(directions.drop(1).all { direction ->
                    (direction - directions.first()).norm <= 1e-8
                }, "Vertex ${vertex.id} has disconnected bounded miter corners")
            }
        }
        for (edge in poly.es) {
            assertTrue(
                (joins.edgeDirection(edge, width) - joins.edgeDirection(edge.reversed, width)).norm <= 1e-8,
                "Edge ${edge.a.id}-${edge.b.id} has disconnected bounded miter sides",
            )
        }
    }
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

    private fun assertInside(face: Face, point: polyhedra.model.util.Vec3) {
        val orientation = ((face.fvs[1] - face.fvs[0]) cross (face.fvs[2] - face.fvs[0])) * face
        val sign = if (orientation >= 0.0) 1.0 else -1.0
        for (index in face.fvs.indices) {
            val a = face.fvs[index]
            val b = face.fvs[(index + 1) % face.size]
            assertTrue(
                sign * (((b - a) cross (point - a)) * face) >= -1e-9,
                "Point $point leaves face ${face.id} through edge $index",
            )
        }
    }
}
