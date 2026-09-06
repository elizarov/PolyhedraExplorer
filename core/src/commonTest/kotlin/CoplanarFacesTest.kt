package polyhedra.core

import polyhedra.core.poly.*
import polyhedra.core.transform.*
import polyhedra.model.poly.*
import polyhedra.model.util.*
import polyhedra.model.api.*
import polyhedra.core.api.evaluateCore
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.*

class CoplanarFacesTest {
    @Test
    fun tenTetrahedraHaveSharedTwoOrbitCellsWithoutChangingTopology() {
        val source = Seed.TenTetrahedra.poly
        val poly = source.withCoplanarFaces()
        assertEquals(source.fev(), poly.fev())
        assertEquals(10, poly.components.size)
        val shared = poly.coplanarFaces.filter { it.sourceFaceIds.size > 1 }
        assertTrue(shared.isNotEmpty())
        assertEquals(20, shared.size)
        assertEquals(40, poly.coplanarFacesBySource.size)
        assertTrue(shared.all { patch -> patch.sourceFaceIds.map { poly.fs[it].kind }.distinct().size == 2 })
        assertSame(poly, source.withCoplanarFaces())
        assertCoverage(poly)
    }

    @Test
    fun otherCompoundsAndTransformsHaveDisjointAreaConservingPresentation() {
        for (source in listOf(Seed.FiveOctahedra.poly, Seed.TwoTetrahedra.poly.rectified(),
            Seed.FiveTetrahedra.poly.rectified(), Seed.FiveCubes.poly.truncated(),
            Seed.TenTetrahedra.poly.truncated())) {
            val poly = source.withCoplanarFaces()
            assertTrue(poly.coplanarFaces.any { it.sourceFaceIds.size > 1 })
            assertCoverage(poly)
            assertDisjoint(poly.coplanarFaces)
        }
    }

    @Test
    fun rotationAndScaleDoNotLoseCoplanarPairsAtNormalOrHashBoundaries() {
        for (angle in listOf(0.0, 0.37, 1.1, 2.3)) for (scale in listOf(0.001, 1.0, 1000.0)) {
            val source = Seed.FiveOctahedra.poly
            val rotated = polyhedron {
                source.vs.forEach { v -> vertex(scale * Vec3(v.x * cos(angle) - v.y * sin(angle),
                    v.x * sin(angle) + v.y * cos(angle), v.z), v.kind) }
                faces(source.fs)
            }.withCoplanarFaces()
            assertEquals(20, rotated.coplanarFaces.count { it.sourceFaceIds.size > 1 }, "$angle/$scale")
            assertCoverage(rotated)
        }
    }

    @Test
    fun convexOverlayHandlesContainmentTripleCoverageCoincidenceAndTouching() {
        fun square(x: Double, y: Double, size: Double) = listOf(
            Vec3(x, y, 0.0), Vec3(x + size, y, 0.0),
            Vec3(x + size, y + size, 0.0), Vec3(x, y + size, 0.0))
        val sources = listOf(PlanarOverlaySource(square(0.0, 0.0, 3.0), 0),
            PlanarOverlaySource(square(1.0, 1.0, 1.0).asReversed(), 1),
            PlanarOverlaySource(square(1.5, 1.5, 1.0), 2),
            PlanarOverlaySource(square(3.0, 0.0, 1.0), 3))
        val patches = planarOverlay(sources, Vec3(0.0, 0.0, 1.0), 1e-9)
        assertEquals(0.25, patches.filter { it.sourceFaceIds == listOf(0, 1, 2) }.sumOf { area(it.vertices) }, 1e-8)
        assertTrue(patches.none { 3 in it.sourceFaceIds && it.sourceFaceIds.size > 1 })
        assertDisjoint(patches)
        // Differently triangulated coincident squares must still have a single partition.
        val square = square(0.0, 0.0, 1.0)
        val triangles = listOf(listOf(0, 1, 2), listOf(0, 2, 3), listOf(0, 1, 3), listOf(1, 2, 3))
        val coincident = planarOverlay(triangles.mapIndexed { i, ids ->
            PlanarOverlaySource(ids.map(square::get), i / 2)
        }, Vec3(0.0, 0.0, 1.0), 1e-9)
        assertTrue(coincident.all { it.sourceFaceIds == listOf(0, 1) })
        assertEquals(1.0, coincident.sumOf { area(it.vertices) }, 1e-8)
        assertDisjoint(coincident)
    }

    @Test
    fun ordinaryAndTransverselyIntersectingFacesNeedNoOverlay() {
        for (seed in listOf(Seed.Cube, Seed.Icosahedron, Seed.TwoTetrahedra, Seed.FiveTetrahedra)) {
            assertSame(seed.poly, seed.poly.withCoplanarFaces(), seed.tag)
        }
    }

    @Test
    fun selfCrossingCapsKeepNonzeroWindingFillAndCoincidentCopiesAreNotLayers() {
        val prism = requireNotNull("SP5_2".toSeedOrNull()).poly
        val poly = compound(listOf(prism, prism)).withCoplanarFaces()
        assertTrue(poly.resolvedFaces.any { it.sourceBoundarySelfIntersects })
        assertTrue(poly.coplanarFaces.isNotEmpty())
        assertTrue(poly.coplanarFaces.all { it.sourceFaceIds.size == 2 })
        assertCoverage(poly)
        assertDisjoint(poly.coplanarFaces)
    }

    @Test
    fun responseAndSerializationCarrySharedPatchesAndRimMasks() = runTest {
        val response = evaluateCore(CoreRequest(CoreState("C10T", emptyList(), "c"), rimWidth = 0.03,
            faceWidth = 0.04, calculateTweakRanges = false))
        val poly = response.poly
        assertTrue(response.coplanarRimFaces.any { it.rimFaceIds.isNotEmpty() })
        assertTrue(response.coplanarRimFaces.any { it.rimFaceIds.isEmpty() })
        val restored = CoreJson.decodeFromString<CoreResponse>(CoreJson.encodeToString(CoreResponse.serializer(), response))
        assertEquals(poly.coplanarFaces.size, restored.poly.coplanarFaces.size)
        assertEquals(response.coplanarRimFaces.size, restored.coplanarRimFaces.size)
        assertCoverage(restored.poly)
        for (patch in restored.coplanarRimFaces) {
            val center = patch.vertices.map { it as Vec3 }.reduce { a, b -> a + b } / patch.vertices.size.toDouble()
            for (id in patch.sourceFaceIds) assertEquals(id in patch.rimFaceIds,
                restored.resolvedRims[id].containsProjected(center, restored.poly.fs[id], 1e-8))
        }
    }

    private fun assertDisjoint(patches: List<CoplanarFacePatch>) {
        for (i in patches.indices) for (j in i + 1 until patches.size) {
            val a = patches[i].vertices
            val b = patches[j].vertices
            val normal = ((a[1] - a[0]) cross (a[2] - a[0])).unit
            if (b.any { abs((it - a[0]) * normal) > 1e-7 }) continue
            // Independent convex separating-axis check, allowing zero-area edge/point contacts.
            val separated = listOf(a, b).any { polygon -> polygon.indices.any { edge ->
                val axis = ((polygon[(edge + 1) % polygon.size] - polygon[edge]) cross normal).unit
                a.maxOf { it * axis } <= b.minOf { it * axis } + 1e-8 ||
                    b.maxOf { it * axis } <= a.minOf { it * axis } + 1e-8
            } }
            assertTrue(separated, "Presentation cells $i and $j overlap")
        }
    }

    private fun assertCoverage(poly: Polyhedron) {
        for ((id, patches) in poly.coplanarFacesBySource) {
            val resolved = poly.resolvedFaces[id]
            val originalArea = resolved.triangles.sumOf { t ->
                ((resolved.vertices[t.b].position - resolved.vertices[t.a].position) cross
                    (resolved.vertices[t.c].position - resolved.vertices[t.a].position)).norm / 2.0
            }
            val patchArea = patches.sumOf { patch -> area(patch.vertices) }
            assertEquals(originalArea, patchArea, originalArea * 1e-7, "Face $id coverage")
        }
    }

    private fun area(points: List<Vec3>): Double = (1 until points.lastIndex).sumOf { i ->
        ((points[i] - points[0]) cross (points[i + 1] - points[0])).norm / 2.0
    }
}
