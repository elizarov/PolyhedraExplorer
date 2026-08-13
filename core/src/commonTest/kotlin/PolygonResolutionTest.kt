package polyhedra.core

import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.MutableFace
import polyhedra.model.poly.MutableVertex
import polyhedra.model.poly.ResolvedFaceGeometry
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.resolveFaceGeometry
import polyhedra.model.util.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PolygonResolutionTest {
    @Test
    fun simpleFaceKeepsDirectVertexAndTriangleMapping() {
        val face = face(
            Vec3(-1.0, -1.0, 0.0),
            Vec3(-1.0, 1.0, 0.0),
            Vec3(1.0, 1.0, 0.0),
            Vec3(1.0, -1.0, 0.0),
        )

        val resolved = resolveFaceGeometry(face)

        assertEquals(1, resolved.cells.size)
        assertEquals(4, resolved.vertices.size)
        assertEquals(2, resolved.triangles.size)
        assertEquals(listOf(0, 1, 2, 3), resolved.cells.single().boundary)
        resolved.vertices.forEachIndexed { index, vertex ->
            assertEquals(listOf(index), vertex.provenance.sourceVertexIds)
            assertTrue(vertex.provenance.sourceSegmentPoints.isEmpty())
        }
    }

    @Test
    fun pentagramRetainsArmsAndTwiceWoundCenterUnderNonzeroWinding() {
        val resolved = resolveFaceGeometry(regularStarFace(5, 2))

        assertEquals(6, resolved.cells.size)
        assertEquals(5, resolved.cells.count { abs(it.winding) == 1 })
        assertEquals(1, resolved.cells.count { abs(it.winding) == 2 })
        assertEquals(10, resolved.vertices.size)
        assertEquals(5, resolved.vertices.count { it.provenance.sourceSegmentPoints.size == 2 })
        assertTrue(resolved.edges.any { it.internalToFill })
        assertTrianglesCoverCells(resolved)
    }

    @Test
    fun reversingBoundaryReversesWindingWithoutChangingResolvedTopology() {
        val forward = resolveFaceGeometry(regularStarFace(7, 2))
        val reversed = resolveFaceGeometry(regularStarFace(7, 2, reversed = true))

        assertEquals(forward.cells.size, reversed.cells.size)
        assertEquals(forward.vertices.size, reversed.vertices.size)
        assertEquals(
            forward.cells.map { abs(it.winding) }.sorted(),
            reversed.cells.map { abs(it.winding) }.sorted(),
        )
        // Reversing a source face also reverses its derived outward normal, so signed winding in
        // the face's own oriented plane stays stable.
        assertEquals(forward.cells.map { it.winding }.toSet(), reversed.cells.map { it.winding }.toSet())
    }

    @Test
    fun topologyAndProvenanceAreStableAcrossScaleAndRigidRotation() {
        val source = starPoints(9, 4)
        val baseline = resolveFaceGeometry(face(*source.toTypedArray()))
        val transformed = resolveFaceGeometry(face(*source.map { point ->
            val x = point.x * 17.0
            val y = point.y * 17.0
            Vec3(x, -0.6 * y, 0.8 * y)
        }.toTypedArray()))

        assertEquals(baseline.cells.map { it.winding }, transformed.cells.map { it.winding })
        assertEquals(baseline.cells.map { it.boundary }, transformed.cells.map { it.boundary })
        assertEquals(
            baseline.vertices.map { it.provenance },
            transformed.vertices.map { it.provenance },
        )
    }

    @Test
    fun nonPlanarSimpleFaceRemainsSupportedButCrossingFaceIsRejected() {
        val simple = face(
            Vec3(-1.0, -1.0, 0.1),
            Vec3(-1.0, 1.0, -0.1),
            Vec3(1.0, 1.0, 0.12),
            Vec3(1.0, -1.0, -0.08),
        )
        assertTrue(!simple.isPlanar)
        val simpleResolved = resolveFaceGeometry(simple)
        assertEquals(simple.fvs.size + 1, simpleResolved.vertices.size)
        assertEquals(simple.fvs.size, simpleResolved.triangles.size)

        val star = starPoints(5, 2).toMutableList()
        star[0] = Vec3(star[0].x, star[0].y, 0.1)
        val crossing = face(*star.toTypedArray())
        assertTrue(!crossing.isPlanar)
        val error = assertFailsWith<IllegalArgumentException> { resolveFaceGeometry(crossing) }
        assertTrue(error.message.orEmpty().contains("not planar"))
    }

    @Test
    fun positiveLengthCollinearOverlapIsRejectedPrecisely() {
        val overlapping = face(
            Vec3(0.0, 0.0, 0.0),
            Vec3(3.0, 0.0, 0.0),
            Vec3(3.0, 2.0, 0.0),
            Vec3(1.0, 2.0, 0.0),
            Vec3(1.0, 0.0, 0.0),
            Vec3(2.0, 0.0, 0.0),
            Vec3(2.0, 1.0, 0.0),
            Vec3(0.0, 1.0, 0.0),
        )

        val error = assertFailsWith<IllegalArgumentException> { resolveFaceGeometry(overlapping) }
        assertTrue(error.message.orEmpty().contains("overlapping segments"))
    }

    @Test
    fun representativeRegularStarsResolveDeterministically() {
        for ((n, q) in listOf(5 to 2, 7 to 2, 7 to 3, 8 to 3, 9 to 2, 9 to 4, 11 to 5)) {
            val first = resolveFaceGeometry(regularStarFace(n, q))
            val second = resolveFaceGeometry(regularStarFace(n, q))
            assertEquals(first.cells, second.cells, "$n/$q cells")
            assertEquals(first.edges, second.edges, "$n/$q edges")
            assertEquals(
                first.vertices.map { it.provenance },
                second.vertices.map { it.provenance },
                "$n/$q provenance",
            )
            assertTrue(first.cells.isNotEmpty(), "$n/$q")
            assertTrue(first.triangles.isNotEmpty(), "$n/$q")
            assertTrianglesCoverCells(first)
        }
    }

    private fun assertTrianglesCoverCells(resolved: ResolvedFaceGeometry) {
        for (cell in resolved.cells) {
            assertEquals(cell.boundary.size - 2, cell.triangles.size, "cell ${cell.id}")
            assertTrue(cell.triangles.all { triangle ->
                triangle.a != triangle.b && triangle.b != triangle.c && triangle.c != triangle.a
            })
        }
    }

    private fun regularStarFace(n: Int, q: Int, reversed: Boolean = false): MutableFace {
        val points = starPoints(n, q).let { if (reversed) it.asReversed() else it }
        return face(*points.toTypedArray())
    }

    private fun starPoints(n: Int, q: Int): List<Vec3> {
        val ring = List(n) { index ->
            val angle = 2.0 * PI * index / n
            Vec3(cos(angle), sin(angle), 0.0)
        }
        return List(n) { index -> ring[(index * q) % n] }
    }

    private fun face(vararg points: Vec3): MutableFace {
        val vertices = points.mapIndexed { index, point ->
            MutableVertex(index, point, VertexKind(0))
        }
        return MutableFace(0, vertices, FaceKind(0))
    }
}
