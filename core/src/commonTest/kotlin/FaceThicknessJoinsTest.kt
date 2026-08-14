package polyhedra.core

import polyhedra.core.poly.Cube
import polyhedra.core.poly.Seed
import polyhedra.core.poly.Tetrahedron
import polyhedra.core.poly.Octahedron
import polyhedra.core.poly.Dodecahedron
import polyhedra.core.poly.Icosahedron
import polyhedra.model.poly.FaceThicknessJoins
import polyhedra.model.poly.outwardNormal
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class FaceThicknessJoinsTest {
    @Test
    fun platonicFaceMitersPreserveThicknessWithoutCrossingNeighborPlanes() {
        val solids = listOf(
            Seed.Tetrahedron.poly,
            Seed.Cube.poly,
            Seed.Octahedron.poly,
            Seed.Dodecahedron.poly,
            Seed.Icosahedron.poly,
        )
        for (poly in solids) {
            val joins = FaceThicknessJoins(poly, poly.fs.mapTo(linkedSetOf()) { face -> face.id })
            for (face in poly.fs) for (vertex in face.fvs) {
                val direction = joins.vertexDirection(face, vertex)
                assertTrue(abs(face.outwardNormal * direction - 1.0) <= 1e-8,
                    "$poly face ${face.id} loses perpendicular thickness at vertex ${vertex.id}: $direction")
                val neighbors = vertex.directedEdges.flatMap { edge -> listOf(edge.l, edge.r) }
                    .distinctBy { it.id }.filter { it != face }
                assertTrue(neighbors.all { neighbor -> neighbor.outwardNormal * direction >= -1e-8 },
                    "$poly face ${face.id} protrudes at vertex ${vertex.id}: $direction")
            }
        }
    }

    @Test
    fun cubeFaceMiterOffsetsItsFaceAndStaysOnNeighborPlanes() {
        val poly = Seed.Cube.poly
        val joins = FaceThicknessJoins(poly, poly.fs.mapTo(linkedSetOf()) { face -> face.id })
        for (vertex in poly.vs) {
            val incident = vertex.directedEdges.flatMap { edge -> listOf(edge.l, edge.r) }.distinctBy { it.id }
            for (face in incident) {
                val direction = joins.vertexDirection(face, vertex)
                assertTrue(incident.all { candidate ->
                    val expected = if (candidate == face) 1.0 else 0.0
                    abs(candidate.outwardNormal * direction - expected) <= 1e-9
                }, "vertex=$vertex face=$face direction=$direction")
            }
        }
    }

    @Test
    fun tetrahedronMiterMovesEveryInnerVertexInside() {
        val poly = Seed.Tetrahedron.poly
        val joins = FaceThicknessJoins(poly, poly.fs.mapTo(linkedSetOf()) { face -> face.id })
        for (vertex in poly.vs) {
            val incident = vertex.directedEdges.flatMap { edge -> listOf(edge.l, edge.r) }.distinctBy { it.id }
            for (sourceFace in incident) {
                val inner = vertex - joins.vertexDirection(sourceFace, vertex) * 0.1
                for (face in poly.fs) assertTrue(
                    face.outwardNormal * inner <= abs(face.d) + 1e-9,
                    "vertex=$vertex inner=$inner face=$face value=${face.outwardNormal * inner} d=${abs(face.d)}",
                )
            }
        }
    }
}
