package polyhedra.web

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.promise
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement
import polyhedra.core.poly.polyhedron
import polyhedra.core.poly.resolvedRims
import polyhedra.core.api.evaluateCore
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.util.Vec3
import polyhedra.web.poly.FaceContext
import polyhedra.web.poly.RenderParams
import polyhedra.web.poly.FaceExportParams
import polyhedra.web.poly.triangulate
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolvedFaceRenderingTest {
    private val scope = MainScope()

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun faceBuffersConsumeWorkerSuppliedPentagramCells() {
        val poly = starPrism(5, 2)
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val context = FaceContext(gl, RenderParams("", null)) { poly }
        try {
            context.performUpdate(null, 0.0)

            assertTrue(poly.resolvedFaces.take(2).all { it.sourceBoundarySelfIntersects })
            assertEquals(40, context.bufferSize)
            assertEquals(26 * 3, context.indexSize)
        } finally {
            context.destroy()
        }
    }

    @Test
    fun hiddenPentagramRimsUseWorkerPolygonsForRenderingAndExport() {
        val poly = starPrism(5, 2)
        val params = RenderParams("", null)
        params.poly.hideFaces.updateValue(setOf(FaceKind(0)))
        val rims = poly.resolvedRims(params.view.faceRim.targetValue)
        params.poly.updateResolvedRims(rims)
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val context = FaceContext(gl, params) { poly }
        try {
            context.performUpdate(null, 0.0)

            val rimMeshes = rims.filter { it.sourceFaceKind == FaceKind(0) }.flatMap { rim ->
                rim.regions.map { region -> region.triangulate(poly.fs[rim.sourceFaceId]) }
            }
            val visibleBufferSize = 5 * 4 * 2
            val visibleIndexSize = 5 * 2 * 3 * 2
            val capBufferSize = 2 * rimMeshes.sumOf { it.vertices.size }
            val capIndexSize = 2 * rimMeshes.sumOf { it.triangles.size }
            val boundarySize = rimMeshes.sumOf { mesh -> mesh.cycles.sumOf { it.vertices.size } }
            assertEquals(visibleBufferSize + capBufferSize + 2 * boundarySize, context.bufferSize)
            assertEquals(visibleIndexSize + capIndexSize + 6 * boundarySize, context.indexSize)

            var triangleCount = 0
            context.exportTriangles(FaceExportParams(1.0, 0.1, 0.05, 0.0)) { a, b, c ->
                assertTrue(((b - a) cross (c - a)).norm > 1e-12)
                triangleCount++
            }
            assertEquals(context.indexSize / 3, triangleCount)
        } finally {
            context.destroy()
        }
    }

    @Test
    fun rimTriangulationCoversOuterCycleMinusHoles() {
        val poly = starPrism(5, 2)
        for (rim in poly.resolvedRims(0.035).take(2)) {
            val face = poly.fs[rim.sourceFaceId]
            for (region in rim.regions) {
                val mesh = region.triangulate(face)
                val triangleArea = mesh.triangles.chunked(3).sumOf { (a, b, c) ->
                    abs(((mesh.vertices[b] - mesh.vertices[a]) cross
                        (mesh.vertices[c] - mesh.vertices[a])) * face) / 2.0
                }
                fun cycleArea(vertices: List<polyhedra.model.util.MutableVec3>) = abs(
                    vertices.indices.sumOf { index ->
                        (vertices[index] cross vertices[(index + 1) % vertices.size]) * face
                    } / 2.0
                )
                val expectedArea = cycleArea(region.outer.vertices) -
                    region.holes.sumOf { hole -> cycleArea(hole.vertices) }
                assertTrue(abs(triangleArea - expectedArea) <= expectedArea * 1e-7 + 1e-10)
            }
        }
    }

    @Test
    fun resolvedStarBipyramidRimTriangulationCoversOnlyItsRimRegions(): Promise<Unit> = scope.promise {
        val response = evaluateCore(
            CoreRequest(CoreState("SB7_2", listOf("R"), "c"), rimWidth = 0.05),
        )
        for (rim in response.resolvedRims) {
            val face = response.poly.fs[rim.sourceFaceId]
            for (region in rim.regions) {
                val mesh = region.triangulate(face)
                val triangleArea = mesh.triangles.chunked(3).sumOf { (a, b, c) ->
                    abs(((mesh.vertices[b] - mesh.vertices[a]) cross
                        (mesh.vertices[c] - mesh.vertices[a])) * face) / 2.0
                }
                fun cycleArea(vertices: List<polyhedra.model.util.MutableVec3>) = abs(
                    vertices.indices.sumOf { index ->
                        (vertices[index] cross vertices[(index + 1) % vertices.size]) * face
                    } / 2.0
                )
                val expectedArea = cycleArea(region.outer.vertices) -
                    region.holes.sumOf { hole -> cycleArea(hole.vertices) }
                assertTrue(
                    abs(triangleArea - expectedArea) <= expectedArea * 1e-7 + 1e-10,
                    "Face ${face.id} rim triangulation area $triangleArea != $expectedArea",
                )
            }
        }
    }

    private fun starPrism(n: Int, q: Int): Polyhedron = polyhedron {
        val bottom = List(n) { index ->
            val angle = 2.0 * PI * index / n
            vertex(Vec3(cos(angle), sin(angle), -0.35))
        }
        val top = List(n) { index ->
            val angle = 2.0 * PI * index / n
            vertex(Vec3(cos(angle), sin(angle), 0.35))
        }
        face(List(n) { index -> bottom[(index * q) % n].id }, FaceKind(0))
        face(List(n) { index -> top[((n - index) * q) % n].id }, FaceKind(0))
        for (index in 0 until n) {
            val next = (index + q) % n
            face(listOf(bottom[index].id, top[index].id, top[next].id, bottom[next].id), FaceKind(1))
        }
    }
}
