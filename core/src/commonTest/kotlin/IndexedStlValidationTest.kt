package polyhedra.core

import polyhedra.core.poly.Seed
import polyhedra.core.poly.Tetrahedron
import polyhedra.model.api.CoreStlResponse
import polyhedra.model.api.CoreStlTriangle
import polyhedra.model.util.MutableVec3
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IndexedStlValidationTest {
    private fun tetrahedron(): CoreStlResponse = Seed.Tetrahedron.poly.let { poly ->
        CoreStlResponse(
            poly.vs.map { MutableVec3(it) },
            poly.fs.map { CoreStlTriangle(it.fvs[0].id, it.fvs[2].id, it.fvs[1].id) },
        )
    }

    @Test
    fun acceptsClosedMeshAndSeparateCoincidentCompoundMembers() {
        val mesh = tetrahedron()
        mesh.validateIndexedStl()
        mesh.copy(
            vertices = mesh.vertices + mesh.vertices,
            triangles = mesh.triangles + mesh.triangles.map { it.copy(a = it.a + 4, b = it.b + 4, c = it.c + 4) },
        ).validateIndexedStl()
    }

    @Test
    fun rejectsOpenNonManifoldAndInwardMeshes() {
        val mesh = tetrahedron()
        val first = mesh.triangles.first()
        for (triangles in listOf(
            mesh.triangles.drop(1),
            mesh.triangles + first,
            mesh.triangles + mesh.triangles, // four uses of every edge
            listOf(first.copy(b = first.c, c = first.b)) + mesh.triangles.drop(1),
            mesh.triangles.map { it.copy(b = it.c, c = it.b) },
        )) {
            assertFailsWith<IllegalArgumentException> { mesh.copy(triangles = triangles).validateIndexedStl() }
        }
    }

    @Test
    fun rejectsBadIndicesCoordinatesDegeneracyAndUnusedVertices() {
        val mesh = tetrahedron()
        for (badIndex in listOf(-1, mesh.vertices.size)) {
            assertFailsWith<IllegalArgumentException> {
                mesh.copy(triangles = listOf(mesh.triangles.first().copy(a = badIndex)) + mesh.triangles.drop(1))
                    .validateIndexedStl()
            }
        }
        for (vertices in listOf(
            listOf(MutableVec3(Double.NaN, 0.0, 0.0)) + mesh.vertices.drop(1),
            listOf(MutableVec3(Double.POSITIVE_INFINITY, 0.0, 0.0)) + mesh.vertices.drop(1),
            listOf(mesh.vertices[1]) + mesh.vertices.drop(1),
            mesh.vertices + MutableVec3(0.0, 0.0, 0.0),
        )) {
            assertFailsWith<IllegalArgumentException> { mesh.copy(vertices = vertices).validateIndexedStl() }
        }
    }

    @Test
    fun rejectsTwoClosedFansSharingOneVertexIdentity() {
        val mesh = tetrahedron()
        val shared = mesh.vertices.first()
        val ids = intArrayOf(0, 4, 5, 6)
        val bowTie = mesh.copy(
            vertices = mesh.vertices + mesh.vertices.drop(1).map { point ->
                MutableVec3(2 * shared.x - point.x, 2 * shared.y - point.y, 2 * shared.z - point.z)
            },
            triangles = mesh.triangles + mesh.triangles.map { CoreStlTriangle(ids[it.a], ids[it.c], ids[it.b]) },
        )
        val failure = assertFailsWith<IllegalArgumentException> { bowTie.validateIndexedStl() }
        assertTrue(failure.message.orEmpty().contains("Disconnected vertex fan"))
    }
}
