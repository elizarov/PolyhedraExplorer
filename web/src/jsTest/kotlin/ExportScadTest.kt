package polyhedra.web

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.promise
import polyhedra.core.poly.SeedType
import polyhedra.core.poly.Seeds
import polyhedra.core.poly.resolvedRims
import polyhedra.core.poly.toSeedOrNull
import polyhedra.core.transform.resolved
import polyhedra.model.api.SeedFamily
import polyhedra.model.api.StarFamilySeedId
import polyhedra.model.poly.*
import polyhedra.model.util.Vec3
import polyhedra.web.poly.exportGeometryToScad
import polyhedra.web.poly.exportSolidToScad
import polyhedra.web.poly.FaceExportParams
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportScadTest {
    private val scope = MainScope()
    private val exportParams = FaceExportParams(scale = 10.0, width = 0.1, rim = 0.2, expand = 0.0)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun exportsAuthoritativeKeplerPoinsotSourceFaces() {
        val expectedFaces = mapOf("SD" to 12, "GD" to 12, "GSD" to 12, "GI" to 20)
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

    @Test
    fun closedEmbeddedBoundaryUsesOnePolygonalPolyhedron() {
        val scad = cube().exportSolidToScad(
            "cube",
            "closed cube",
            exportParams,
            hiddenFaceKinds = emptySet(),
            resolvedRims = emptyList(),
            embeddedBoundary = true,
        )

        assertEquals(1, Regex("polyhedron\\(").findAll(scad).count())
        assertTrue("union()" !in scad)
        assertTrue("linear_extrude" !in scad)
        assertEquals(8, Regex("\\[[^\\[\\]]+, [^\\[\\]]+, [^\\[\\]]+\\]", RegexOption.MULTILINE)
            .findAll(scad.substringBefore("faces =")).count())
        assertEquals(6, scad.substringAfter("faces = [").substringBefore("],\n  convexity")
            .lineSequence().count { line -> line.trimStart().startsWith("[") })
    }

    @Test
    fun immersedClosedSurfaceFallsBackToUnionOfClosedPolygonPieces() {
        val source = requireNotNull("SD".toSeedOrNull()).poly
        val scad = source.exportSolidToScad(
            "stellated_dodecahedron",
            "immersed",
            exportParams,
            hiddenFaceKinds = emptySet(),
            resolvedRims = emptyList(),
            embeddedBoundary = false,
        )

        assertContains(scad, "union() {")
        assertTrue("polyhedron(" !in scad)
        assertTrue(scad.lineSequence().count { "linear_extrude" in it } >= source.fs.size)
        assertContains(scad, "polygon(")
    }

    @Test
    fun explicitResolvedSolidUsesMergedPolygonBoundary(): Promise<Unit> = scope.promise {
        val resolved = requireNotNull("SD".toSeedOrNull()).poly.resolved(null)
        val scad = resolved.exportSolidToScad(
            "resolved_stellated_dodecahedron",
            "resolved",
            exportParams,
            hiddenFaceKinds = emptySet(),
            resolvedRims = emptyList(),
            embeddedBoundary = true,
        )

        assertEquals(1, Regex("polyhedron\\(").findAll(scad).count())
        assertTrue("linear_extrude" !in scad)
        assertEquals(resolved.fs.size, scad.substringAfter("faces = [").substringBefore("],\n  convexity")
            .lineSequence().count { line -> line.trimStart().startsWith("[") })
    }

    @Test
    fun hiddenStarPrismCapsRemainPentagramRimPolygonPaths() {
        val source = requireNotNull(
            StarFamilySeedId(SeedFamily.Prism, 5, 2).tag.toSeedOrNull(),
        ).poly
        val hidden = setOf(FaceKind(0))
        val rims = source.resolvedRims(exportParams.rim)
        val scad = source.exportSolidToScad(
            "star_prism",
            "hidden pentagram caps",
            exportParams,
            hiddenFaceKinds = hidden,
            resolvedRims = rims,
            embeddedBoundary = false,
        )

        assertEquals(2, scad.lineSequence().count { "hidden face" in it && "rim" in it })
        assertContains(scad, "paths = [")
        assertTrue(scad.lineSequence().count { "linear_extrude" in it } >= 7)
        assertTrue("polyhedron(" !in scad)
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
