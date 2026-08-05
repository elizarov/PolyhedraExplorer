package polyhedra.js.poly

import polyhedra.common.poly.*
import polyhedra.common.util.Vec3
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportScadTest {
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
