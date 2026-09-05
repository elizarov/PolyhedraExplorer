package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.convertStl
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.ConvexHull
import polyhedra.core.transform.toTransformOrNull
import polyhedra.model.api.*
import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.*

class ConvexHullTest {
    @Test
    fun convexCatalogueSolidsAreIdentitiesAndCoplanarTrianglesMerge() {
        for (seed in Seeds.filter { it.type in listOf(SeedType.Platonic, SeedType.Archimedean, SeedType.Catalan) }) {
            val poly = seed.poly
            assertTrue(ConvexHull().isIdentityTransform(poly), seed.tag)
            assertSame(poly, ConvexHull().transform(poly), seed.tag)
            val hull = convexHull(poly.vs)
            assertEquals(poly.fev(), hull.fev(), seed.tag)
            assertHullContains(hull, poly.vs)
        }
        assertEquals(FEV(6, 12, 8), convexHull(Seed.Cube.poly.vs).fev())
    }

    @Test
    fun starFacesAndCompoundsBecomeSingleConvexEnvelopes() {
        val expected = listOf(
            "SD" to "I", "GD" to "I", "GSD" to "D", "GI" to "I",
            "C2T" to "C", "C5T" to "D", "C10T" to "D", "C5C" to "D",
        )
        for ((sourceTag, hullTag) in expected) {
            val poly = sourceTag.toSeedOrNull()!!.poly
            assertFalse(ConvexHull().isIdentityTransform(poly), sourceTag)
            val hull = ConvexHull().transform(poly)
            assertHullContains(hull, poly.vs)
            assertEquals(hullTag, hull.recognizedSeedOrNull()?.tag, sourceTag)
            assertEquals(FEV(1, 1, 1), hull.analyzeSymmetry().orbitCounts, sourceTag)
        }
        for (tag in listOf("SP5_2", "SA5_2", "SY19_9", "SB15_7", "SP100_9", "C5O")) {
            val poly = tag.toSeedOrNull()!!.poly
            assertHullContains(ConvexHull().transform(poly), poly.vs)
        }
    }

    @Test
    fun duplicateInteriorAndCoplanarPointsDoNotCreateExtraFacesOrVertices() {
        val cube = Seed.Cube.poly
        val centers = cube.fs.map { face -> face.fvs.fold(Vec3.ZERO as Vec3) { a, b -> a + b } / face.size.toDouble() }
        val points = cube.vs + cube.vs + listOf(Vec3.ZERO) + centers +
            cube.es.map { (it.a + it.b) / 2.0 }
        for (order in listOf(points, points.asReversed(), points.shuffled(Random(42)))) {
            val hull = convexHull(order)
            assertEquals(cube.fev(), hull.fev())
            assertHullContains(hull, points)
        }
    }

    @Test
    fun randomPointSetsAreContainedAndResultIsScaleAndTranslationIndependent() {
        val random = Random(541)
        repeat(25) {
            val points = List(45) { Vec3(random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0)) }
            val hull = convexHull(points)
            assertHullContains(hull, points)
            val mapped = points.map { Vec3(it.z, it.x, it.y) * 12.3 + Vec3(3.0, -4.0, 5.0) }
            val other = convexHull(mapped)
            assertEquals(hull.fev(), other.fev())
            assertHullContains(other, mapped)
            assertEquals(hull.signedVolume() * 12.3 * 12.3 * 12.3, other.signedVolume(), 1e-6)
        }
    }

    @Test
    fun combineRetainsSourceCoordinatesAndIndependentTopologyEvenWhenCoincident() {
        for (tag in listOf("C", "SD", "C2T")) {
            val source = tag.toSeedOrNull()!!.poly
            val transform = ConvexHull(combineOriginal = true)
            assertFalse(transform.isIdentityTransform(source))
            val hull = convexHull(source.vs)
            val combined = transform.transform(source)
            combined.validateRenderableImmersion()
            assertEquals(source.components.size + 1, combined.components.size)
            assertEquals(source.vs.size + hull.vs.size, combined.vs.size)
            assertEquals(source.fs.size + hull.fs.size, combined.fs.size)
            source.vs.forEach { assertTrue((it - combined.vs[it.id]).norm < 1e-12) }
            source.fs.forEach { face -> assertEquals(face.fvs.map { it.id }, combined.fs[face.id].fvs.map { it.id }) }
        }
    }

    @Test
    fun serializedSettingAndCoreIdentityWarningRoundTrip() = runTest {
        assertEquals("H", "H~b=0".parseTransformTag()?.tag)
        assertEquals("H~b=1", "H~b=1".parseTransformTag()?.tag)
        assertNull("H~b=0.5".parseTransformTag())
        assertNull("t~b=1".toTransformOrNull())
        for (tag in listOf("H", "H~b=1")) {
            assertEquals(tag, tag.toTransformOrNull()?.tag)
            for (seed in listOf("C", "SD", "C2T")) {
                val response = evaluateCore(CoreRequest(CoreState(seed, listOf(tag), "c")))
                assertNull(response.error, "$seed/$tag: ${response.error}")
                assertEquals(seed == "C" && tag == "H", response.warnings.single()?.code == CoreIssueCode.TransformIsIdentity)
                assertEquals(TransformTweak.CombineOriginal, response.transformTweakRanges.single().single().tweak)
                if (tag == "H") assertEquals(PolyhedronContract.EmbeddedBoundary, response.geometryAnalysis?.strongestContract)
            }
        }
    }

    @Test
    fun applyingRemovingAndCombiningHullHaveNoInventedAnimationMesh() = runTest {
        val states = listOf(emptyList(), listOf("H"), listOf("H~b=1"))
        for (from in states) for (to in states) if (from != to) {
            val response = evaluateCore(CoreRequest(
                state = CoreState("SD", to, "c"),
                previousState = CoreState("SD", from, "c"),
                animationDuration = 0.5,
                calculateTweakRanges = false,
            ))
            assertNull(response.error, "$from -> $to: ${response.error}")
            assertTrue(response.animation.isEmpty(), "$from -> $to")
        }
    }

    @Test
    fun hullAndCombinedSurfacesExportValidStl() = runTest {
        for ((seed, combined) in listOf("SD" to false, "SD" to true, "C" to true)) {
            val poly = ConvexHull(combined).transform(seed.toSeedOrNull()!!.poly)
            val result = convertStl(CoreStlRequest(presentation = CoreStlPresentation(
                poly, emptyList(), scale = 30.0, width = 0.04, rim = 0.04, expand = 0.0,
            )))
            assertNull(result.error, "$seed/$combined: ${result.error}")
            polyhedron {
                result.vertices.forEach { vertex(it) }
                result.triangles.forEach { face(listOf(it.a, it.c, it.b)) }
            }.validateProperGeometry()
        }
    }

    @Test
    fun degeneratePointSetsFailClearlyAndProgressCompletes() {
        assertFailsWith<IllegalArgumentException> { convexHull(List(4) { Vec3.ZERO }) }
        assertFailsWith<IllegalArgumentException> { convexHull(List(4) { Vec3(it.toDouble(), 0.0, 0.0) }) }
        assertFailsWith<IllegalArgumentException> { convexHull(Seed.Cube.poly.vs.map { Vec3(it.x, it.y, 0.0) }) }
        val progress = arrayListOf<Int>()
        convexHull(Seed.Icosahedron.poly.vs) { progress += it }
        assertEquals(100, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
    }

    private fun assertHullContains(hull: Polyhedron, points: List<Vec3>) {
        hull.validateProperGeometry()
        assertEquals(1, hull.components.size)
        assertEquals(2, hull.fs.size - hull.es.size + hull.vs.size)
        val tolerance = points.maxOf { it.norm } * 1e-7
        for (face in hull.fs) {
            assertTrue(face.isPlanar && face.isConvex)
            assertTrue(points.all { face * it <= face.d + tolerance }, "Non-supporting hull face")
            assertTrue(face.fvs.all { abs(face * it - face.d) < tolerance })
        }
    }
}
