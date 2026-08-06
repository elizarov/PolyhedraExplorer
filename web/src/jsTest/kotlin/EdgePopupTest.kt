package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.Element
import org.w3c.dom.HTMLDivElement
import polyhedra.core.poly.Seed
import polyhedra.core.poly.TruncatedCube
import polyhedra.model.poly.Edge
import polyhedra.model.poly.Face
import polyhedra.model.poly.PolygonProjection
import polyhedra.model.poly.Vertex
import polyhedra.model.poly.computeNetProjection
import polyhedra.model.poly.len
import polyhedra.web.main.PolyStyle
import polyhedra.web.main.SvgEdgeNet
import polyhedra.web.util.toRgbString
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdgePopupTest {
    private lateinit var host: HTMLDivElement
    private var composition: Composition? = null

    @BeforeTest
    fun setUp() {
        host = document.createElement("div") as HTMLDivElement
        document.body!!.appendChild(host)
    }

    @AfterTest
    fun tearDown() {
        composition?.dispose()
        host.parentNode?.removeChild(host)
    }

    @Test
    fun edgeNetPlacesNamedFacesOnTheirOrderedSides() {
        val edge = representativeMixedFaceEdge()
        val net = edge.computeNetProjection()

        assertEquals(edge.l, net.left.face)
        assertEquals(edge.r, net.right.face)
        assertTrue(net.left.figure.vs.all { it.x <= tolerance })
        assertTrue(net.left.figure.vs.any { it.x < -tolerance })
        assertTrue(net.right.figure.vs.all { it.x >= -tolerance })
        assertTrue(net.right.figure.vs.any { it.x > tolerance })

        val leftA = net.left.figure.at(net.left.face, edge.a)
        val leftB = net.left.figure.at(net.left.face, edge.b)
        val rightA = net.right.figure.at(net.right.face, edge.a)
        val rightB = net.right.figure.at(net.right.face, edge.b)
        listOf(leftA.x, leftB.x, rightA.x, rightB.x).forEach { assertTrue(abs(it) <= tolerance) }
        assertEquals(leftA.y, rightA.y, tolerance)
        assertEquals(leftB.y, rightB.y, tolerance)
        assertEquals(edge.len, abs(leftB.y - leftA.y), tolerance)
    }

    @Test
    fun edgeNetSvgUsesLeftThenRightFaceColors() {
        val edge = representativeMixedFaceEdge()
        composition = renderComposable(host) {
            SvgEdgeNet("figure edge-figure", edge, PolyStyle.edgeColor)
        }

        val polygons = host.querySelectorAll("polygon")
        assertEquals(2, polygons.length)
        val left = polygons.item(0) as Element
        val right = polygons.item(1) as Element
        assertEquals("left", left.getAttribute("data-side"))
        assertEquals(edge.l.kind.toString(), left.getAttribute("data-face-kind"))
        assertEquals(PolyStyle.faceColor(edge.l).toRgbString(), left.getAttribute("fill"))
        assertEquals("right", right.getAttribute("data-side"))
        assertEquals(edge.r.kind.toString(), right.getAttribute("data-face-kind"))
        assertEquals(PolyStyle.faceColor(edge.r).toRgbString(), right.getAttribute("fill"))
    }

    private fun representativeMixedFaceEdge(): Edge =
        Seed.TruncatedCube.poly.es.first { it.l.kind != it.r.kind }

    private fun PolygonProjection.at(face: Face, vertex: Vertex) =
        vs[face.fvs.indexOf(vertex)]

    private companion object {
        const val tolerance = 1e-8
    }
}
