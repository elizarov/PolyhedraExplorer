package polyhedra.web

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.promise
import polyhedra.core.poly.SeedType
import polyhedra.core.poly.Seeds
import polyhedra.core.poly.resolvedRims
import polyhedra.core.poly.toSeedOrNull
import polyhedra.core.api.evaluateCore
import polyhedra.core.transform.resolved
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.SeedFamily
import polyhedra.model.api.StarFamilySeedId
import polyhedra.model.poly.*
import polyhedra.model.util.*
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
    fun regularCompoundsExportClosedMembersOrHiddenRimPieces() {
        for (seed in Seeds.filter { it.type == SeedType.RegularCompounds }) {
            val poly = seed.poly
            for (hidden in listOf(false, true)) {
                val scad = poly.exportSolidToScad(
                    "compound", seed.tag, exportParams,
                    hiddenFaceKinds = if (hidden) poly.faceKinds.keys.toSet() else emptySet(),
                    resolvedRims = if (hidden) poly.resolvedRims(exportParams.rim, exportParams.width) else emptyList(),
                    embeddedBoundary = false,
                )
                assertContains(scad, "union() {")
                val pieces = Regex("polyhedron\\(").findAll(scad).count()
                if (hidden) assertTrue(pieces >= poly.fs.size, seed.tag)
                else assertEquals(poly.components.size, pieces, seed.tag)
                assertTrue("linear_extrude" !in scad)
                assertTrue("NaN" !in scad && "Infinity" !in scad)
            }
        }
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
        assertTrue(scad.lineSequence().count { "polyhedron(" in it } >= source.fs.size)
        assertTrue("linear_extrude" !in scad)
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
        val rims = source.resolvedRims(exportParams.rim, exportParams.width)
        val scad = source.exportSolidToScad(
            "star_prism",
            "hidden pentagram caps",
            exportParams,
            hiddenFaceKinds = hidden,
            resolvedRims = rims,
            embeddedBoundary = false,
        )

        assertEquals(2, scad.lineSequence().count { "hidden face" in it && "rim" in it })
        assertTrue(scad.lineSequence().count { "polyhedron(" in it } >= 7)
        assertTrue("linear_extrude" !in scad)
    }

    @Test
    fun hiddenTetrahedronRimsUseTheConfiguredPerpendicularDepth() {
        val source = requireNotNull("T".toSeedOrNull()).poly
        val hidden = source.fs.mapTo(linkedSetOf(), Face::kind)
        val scad = source.exportSolidToScad(
            "tetrahedron",
            "hidden faces",
            exportParams,
            hiddenFaceKinds = hidden,
            resolvedRims = source.resolvedRims(exportParams.rim, exportParams.width),
            embeddedBoundary = true,
        )
        assertEquals(source.fs.size, scad.lineSequence().count { line -> "polyhedron(" in line })
        assertTrue("linear_extrude" !in scad)
    }

    @Test
    fun perpendicularDepthDoesNotDependOnFacePlaneDistance() {
        val source = asymmetricTetrahedron()
        val hidden = source.fs.mapTo(linkedSetOf(), Face::kind)
        val scad = source.exportSolidToScad(
            "asymmetric_tetrahedron",
            "different vertex joins",
            exportParams,
            hiddenFaceKinds = hidden,
            resolvedRims = source.resolvedRims(exportParams.rim, exportParams.width),
            embeddedBoundary = true,
        )
        assertEquals(source.fs.size, scad.lineSequence().count { line -> "polyhedron(" in line })
        assertTrue("linear_extrude" !in scad)
    }

    @Test
    fun foldedFacesAutomaticallyUseRimPatchUnion(): Promise<Unit> = scope.promise {
        val response = evaluateCore(
            CoreRequest(CoreState("O", listOf("S", "R", "t"), "c"), rimWidth = exportParams.rim),
        )
        val source = response.poly
        assertTrue(source.nonPlanarFaceKinds.isNotEmpty())

        val scad = source.exportSolidToScad(
            "truncated_stellated_octahedron",
            "folded faces",
            exportParams,
            hiddenFaceKinds = emptySet(),
            resolvedRims = response.resolvedRims,
            embeddedBoundary = true,
        )

        assertContains(scad, "union() {")
        assertTrue(scad.lineSequence().any { line -> "polyhedron(" in line })
        assertTrue(scad.lineSequence().any { line -> "hidden face" in line && "rim" in line })
        assertTrue("linear_extrude" !in scad)
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

    private fun asymmetricTetrahedron(): Polyhedron {
        val regular = requireNotNull("T".toSeedOrNull()).poly
        val radii = listOf(0.78, 1.0, 1.24, 1.43)
        val vertices = regular.vs.mapIndexed { index, point ->
            MutableVertex(index, point * radii[index], point.kind)
        }
        val faces = regular.fs.map { face ->
            MutableFace(face.id, face.fvs.map { vertex -> vertices[vertex.id] }, face.kind)
        }
        return Polyhedron(vertices, faces, faceKindSources = null)
    }

}
