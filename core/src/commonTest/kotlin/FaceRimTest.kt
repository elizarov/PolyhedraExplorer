package polyhedra.core

import polyhedra.core.poly.DisdyakisTriacontahedron
import polyhedra.core.poly.Seed
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.FaceRim
import polyhedra.model.poly.MutableFace
import polyhedra.model.poly.MutableVertex
import polyhedra.model.poly.VertexKind
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.distanceToLine
import polyhedra.model.util.minus
import polyhedra.model.util.plus
import polyhedra.model.util.times
import polyhedra.model.util.unit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class FaceRimTest {
    @Test
    fun disdyakisTriacontahedronRimHasUniformWidthAlongEveryFaceEdge() {
        val poly = Seed.DisdyakisTriacontahedron.poly
        val failures = buildList {
            for (face in poly.fs) {
                val faceRim = poly.faceRim(face)
                val inset = faceRim.maxRim * 0.5
                val insetVertices = face.fvs.mapIndexed { index, vertex ->
                    vertex + faceRim.rimDir[index] * inset
                }
                for (index in face.fvs.indices) {
                    val next = (index + 1) % face.fvs.size
                    val width = insetVertices[index].distanceToLine(face.fvs[index], face.fvs[next])
                    if (abs(width - inset) > 1e-9) {
                        add("face ${face.id}, edge $index: expected $inset, got $width")
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun nonPlanarFaceRimUsesUniformOffsetsInItsAveragePlane() {
        val vertices = listOf(
            Vec3(-1.0, -0.7, 0.12),
            Vec3(1.1, -0.8, -0.08),
            Vec3(0.9, 0.9, 0.16),
            Vec3(-0.8, 0.7, -0.11),
        ).mapIndexed { index, point -> MutableVertex(index, point, VertexKind(0)) }
        val face = MutableFace(0, vertices, FaceKind(0))
        val faceRim = FaceRim(face)
        val inset = faceRim.maxRim * 0.5

        assertTrue(!face.isPlanar)
        assertTrue(faceRim.maxRim.isFinite() && faceRim.maxRim > 0.0)
        for (index in vertices.indices) {
            val next = (index + 1) % vertices.size
            val edge = vertices[next] - vertices[index]
            val inward = (edge cross face).unit
            val firstOffset = faceRim.rimDir[index] * inset
            val secondOffset = faceRim.rimDir[next] * inset

            assertTrue(abs(firstOffset * inward - inset) < 1e-9)
            assertTrue(abs(secondOffset * inward - inset) < 1e-9)
        }
    }
}
