package polyhedra.web

import polyhedra.core.poly.SeedType
import polyhedra.core.poly.Seeds
import polyhedra.model.poly.*
import polyhedra.model.util.Vec3
import polyhedra.web.poly.exportGeometryToScad
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportScadTest {
    @Test
    fun exportsAllResolvedKeplerPoinsotCells() {
        val expectedFaces = mapOf("SD" to 60, "GD" to 60, "GSD" to 60, "GI" to 180)
        for (seed in Seeds.filter { candidate -> candidate.type == SeedType.KeplerPoinsot }) {
            val scad = seed.poly.exportGeometryToScad(seed.tag, seed.name)
            assertEquals(seed.poly.vs.size, scad.lineSequence().count { it.endsWith(" vertex") }, seed.tag)
            assertEquals(expectedFaces.getValue(seed.tag), scad.lineSequence().count { it.endsWith(" face") }, seed.tag)
        }
    }

    @Test
    fun exportsCompleteCubeGeometry() {
        val scad = cube().exportGeometryToScad("cube", "test state")

        assertContains(scad, "// polyhedron(cube[0], cube[1]);")
        assertContains(scad, "// test state")
        assertContains(scad, "cube = [[")
        assertEquals(8, scad.lineSequence().count { it.endsWith(" vertex") })
        assertEquals(6, scad.lineSequence().count { it.endsWith(" face") })
        assertTrue(scad.trimEnd().endsWith("]];"))
    }

    @Test
    fun triangulatesConcaveFacesForOpenScad() {
        val scad = concavePrismFixture().exportGeometryToScad("concave", "concave test")

        // Two eight-vertex C-shaped caps become six triangles each; the eight convex side quads
        // stay intact. Face kinds are expanded in lockstep with the emitted geometry.
        assertEquals(20, scad.lineSequence().count { it.endsWith(" face") })
        val faceKindSection = scad.substringAfterLast("], [").substringBefore("]];")
        assertEquals(20, Regex("\\d+").findAll(faceKindSection).count())
    }

    private fun cube(): Polyhedron {
        val vertices = listOf(
            Vec3(1.0, 1.0, -1.0),
            Vec3(-1.0, 1.0, -1.0),
            Vec3(-1.0, -1.0, -1.0),
            Vec3(1.0, -1.0, -1.0),
            Vec3(1.0, 1.0, 1.0),
            Vec3(-1.0, 1.0, 1.0),
            Vec3(-1.0, -1.0, 1.0),
            Vec3(1.0, -1.0, 1.0),
        ).mapIndexed { index, point -> MutableVertex(index, point, VertexKind(0)) }
        val faceVertexIds = listOf(
            listOf(0, 1, 2, 3),
            listOf(0, 4, 5, 1),
            listOf(1, 5, 6, 2),
            listOf(2, 6, 7, 3),
            listOf(3, 7, 4, 0),
            listOf(4, 7, 6, 5),
        )
        val faces = faceVertexIds.mapIndexed { index, ids ->
            MutableFace(index, ids.map(vertices::get), FaceKind(0))
        }
        return Polyhedron(vertices, faces, faceKindSources = null)
    }

}
