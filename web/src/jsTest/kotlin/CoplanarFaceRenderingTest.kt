package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.promise
import org.jetbrains.compose.web.renderComposable
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.*
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.model.api.*
import polyhedra.model.poly.*
import polyhedra.model.util.*
import polyhedra.web.glsl.get
import polyhedra.web.main.*
import polyhedra.web.poly.*
import polyhedra.web.util.toRgbString
import kotlin.math.round
import kotlin.test.*

class CoplanarFaceRenderingTest {
    @Test
    fun sharedAreasAreEmittedOnceWithOneSharedColorInOpaqueAndAcrylicModes() {
        val poly = Seed.TenTetrahedra.poly.withCoplanarFaces()
        val params = RenderParams("", null)
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
        val context = FaceContext(gl, params) { poly }
        try {
            for (transparent in listOf(false, true)) {
                params.view.transparencyEnabled.updateValue(transparent)
                context.performUpdate(null, 0.0)
                assertEquals(poly.coplanarFaces.sumOf { (it.vertices.size - 2) * 3 }, context.indexSize)
                assertNoDuplicateTopTriangles(context)
                var offset = 0
                for (patch in poly.coplanarFaces) {
                    val color = PolyStyle.faceColor(poly, patch.sourceFaceIds)
                    assertEquals(color, PolyStyle.faceColor(poly, patch.sourceFaceIds.asReversed()))
                    for (i in patch.vertices.indices) {
                        assertEquals(color.r.toDouble(), context.target.colorBuffer[offset, 0], 1e-6)
                        assertEquals(color.g.toDouble(), context.target.colorBuffer[offset, 1], 1e-6)
                        assertEquals(color.b.toDouble(), context.target.colorBuffer[offset, 2], 1e-6)
                        offset++
                    }
                }
            }
            val shared = poly.coplanarFaces.first { it.sourceFaceIds.size > 1 }
            assertTrue(poly.fs.none { PolyStyle.faceColor(it) == PolyStyle.faceColor(poly, shared.sourceFaceIds) })
        } finally {
            context.destroy()
            params.destroy()
        }
    }

    @Test
    fun hiddenOrbitsUseRimCoverageWithoutDoubleDrawingSharedTopMaterial() = MainScope().let { scope ->
        scope.promise {
            val state = CoreState("C10T", emptyList(), "c")
            val response = evaluateCore(CoreRequest(state, rimWidth = 0.03, faceWidth = 0.04))
            val params = RenderParams("", null)
            params.poly.applyCoreResponse(state, response)
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            val gl = requireNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)
            val context = FaceContext(gl, params)
            try {
                for (hidden in listOf(setOf(FaceKind(0)), response.poly.faceKinds.keys)) {
                    params.poly.hideFaces.updateValue(hidden)
                    context.performUpdate(null, 0.0)
                    assertNoDuplicateTopTriangles(context)
                    val expected = response.coplanarRimFaces.filter { patch ->
                        patch.sourceFaceIds.any { response.poly.fs[it].kind !in hidden || it in patch.rimFaceIds }
                    }.sumOf { (it.vertices.size - 2) * 3 }
                    val actual = (0 until context.indexSize step 3).count {
                        (0..2).all { corner -> context.innerBuffer[context.indexBuffer[it + corner]] == 0 }
                    } * 3
                    assertEquals(expected, actual)
                }
            } finally {
                context.destroy()
                params.destroy()
            }
        }.also { it.then { scope.cancel() }; it.catch { scope.cancel() } }
    }

    @Test
    fun faceAndEdgeFiguresUseTheCanvasOverlapColorAndOriginalProjectionBasis() {
        val poly = Seed.TenTetrahedra.poly.withCoplanarFaces()
        val patch = poly.coplanarFaces.first { it.sourceFaceIds.size > 1 }
        val color = PolyStyle.faceColor(poly, patch.sourceFaceIds).toRgbString()
        val host = document.createElement("div") as HTMLDivElement
        document.body!!.appendChild(host)
        var composition: Composition? = null
        try {
            composition = renderComposable(host) {
                patch.sourceFaceIds.forEach { id -> SvgFaceFigure("figure", poly, poly.fs[id], PolyStyle.edgeColor) }
                SvgEdgeNet("edge-figure", poly.fs[patch.sourceFaceIds.first()].directedEdges.first(), PolyStyle.edgeColor, poly)
            }
            val figures = host.querySelectorAll(".figure")
            assertEquals(2, figures.length)
            for (i in 0 until figures.length) {
                val overlay = (figures.item(i) as Element).querySelector("[data-overlap]")!!
                assertEquals(color, overlay.getAttribute("fill"))
                assertEquals("none", overlay.getAttribute("stroke"))
                val face = poly.fs[patch.sourceFaceIds[i]]
                val projection = face.computeProjectionFigure()
                val first = poly.coplanarFacesBySource.getValue(face.id).first { it.sourceFaceIds.size > 1 }
                val expected = first.vertices.map(projection.project).joinToString(" ") { "${it.x.fmt},${it.y.fmt}" }
                assertEquals(expected, overlay.getAttribute("points"))
            }
            assertEquals(color, host.querySelector(".edge-figure [data-overlap]")!!.getAttribute("fill"))
            assertEquals("left", host.querySelector(".edge-figure [data-side]")!!.getAttribute("data-side"))
        } finally {
            composition?.dispose()
            host.parentNode?.removeChild(host)
        }
    }

    private fun assertNoDuplicateTopTriangles(context: FaceContext) {
        val seen = hashSetOf<List<String>>()
        for (i in 0 until context.indexSize step 3) {
            val indices = (0..2).map { context.indexBuffer[i + it] }
            if (indices.any { context.innerBuffer[it] != 0 }) continue
            val key = indices.map { vertex -> (0..2).joinToString(",") {
                round(context.target.positionBuffer[vertex, it] * 1e7).toString()
            } }.sorted()
            assertTrue(seen.add(key), "Coincident top triangle rendered twice: $key")
        }
    }
}
