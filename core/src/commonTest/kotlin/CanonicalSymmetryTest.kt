import polyhedra.common.poly.Cube
import polyhedra.common.poly.Polyhedron
import polyhedra.common.poly.Seed
import polyhedra.common.poly.SeedType
import polyhedra.common.poly.Seeds
import polyhedra.common.poly.TransformFEV
import polyhedra.common.poly.polyhedron
import polyhedra.common.transform.Transforms
import polyhedra.common.transform.canonical
import polyhedra.common.transform.canonicalOrbitStats
import polyhedra.common.transform.isCanonical
import polyhedra.common.transform.transformed
import polyhedra.common.transform.truncated
import polyhedra.common.util.MutableVec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanonicalSymmetryTest {
    @Test
    fun catalogSeedsUseDeclaredSymmetryOrbits() {
        for (seed in Seeds) {
            val poly = seed.poly
            val stats = poly.canonicalOrbitStats()

            assertEquals(poly.edgeKinds.size, stats.pointOrbits, seed.name)
            assertEquals(poly.vertexKinds.size + poly.faceKinds.size, stats.faceOrbits, seed.name)
        }
    }

    @Test
    fun transformedPlatonicSolidsUseDeclaredSymmetryOrbits() {
        val transforms = Transforms.filter { it.fev != TransformFEV.ID }
        for (seed in Seeds.filter { it.type == SeedType.Platonic }) {
            for (transform in transforms) {
                val poly = seed.poly.transformed(transform)
                val stats = poly.canonicalOrbitStats()
                val context = "$transform $seed"

                assertEquals(poly.edgeKinds.size, stats.pointOrbits, context)
                assertEquals(poly.vertexKinds.size + poly.faceKinds.size, stats.faceOrbits, context)
            }
        }
    }

    @Test
    fun symmetricPackingRunsOnOrbitQuotient() {
        val poly = Seed.Cube.poly.truncated(0.2)
        val stats = poly.canonicalOrbitStats()

        assertEquals(poly.edgeKinds.size, stats.pointOrbits)
        assertEquals(poly.vertexKinds.size + poly.faceKinds.size, stats.faceOrbits)
        assertTrue(stats.pointOrbits < stats.points, stats.toString())
        assertTrue(stats.faceOrbits < stats.faces, stats.toString())
        assertTrue(poly.canonical().isCanonical())
    }

    @Test
    fun invalidGeometricOrbitIsSplitBeforeSolving() {
        val symmetric = Seed.Cube.poly.truncated(0.2)
        val perturbed = perturbFirstVertex(symmetric)
        val symmetricStats = symmetric.canonicalOrbitStats()
        val perturbedStats = perturbed.canonicalOrbitStats()

        assertTrue(perturbedStats.pointOrbits > symmetricStats.pointOrbits)
        assertTrue(perturbedStats.faceOrbits > symmetricStats.faceOrbits)
        assertTrue(perturbed.canonical().isCanonical())
    }

    private fun perturbFirstVertex(poly: Polyhedron): Polyhedron = polyhedron {
        val vertices = poly.vs.map { sourceVertex ->
            if (sourceVertex.id == 0) {
                vertex(
                    MutableVec3(sourceVertex.x + 0.01, sourceVertex.y - 0.02, sourceVertex.z + 0.015),
                    sourceVertex.kind,
                )
            } else {
                vertex(sourceVertex)
            }
        }
        for (face in poly.fs) face(face.fvs.map { vertices[it.id] }, face.kind)
    }
}
