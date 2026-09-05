package polyhedra.web

import androidx.compose.runtime.Composition
import androidx.compose.runtime.mutableStateOf
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.Element
import polyhedra.web.main.IndicatorIcon
import polyhedra.web.main.MessageSpan
import polyhedra.web.poly.*
import kotlin.js.Promise
import kotlin.test.*

class IndicatorIconTest {
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
        host.remove()
    }

    @Test
    fun nonPlanarityWarningsAreCenteredMonochromeVectorsWithTooltips() {
        composition = renderComposable(host) {
            Div(attrs = { attr("style", "color: rgb(17, 34, 51)") }) {
                MessageSpan(SomeFacesNotPlanar())
                MessageSpan(FaceNotPlanar())
            }
        }
        assertNull(host.querySelector(".emoji"))
        assertTrue(!host.textContent.orEmpty().contains("⚠"))
        assertEquals(2, host.querySelectorAll("svg").length)
        assertEquals(2, host.querySelectorAll(".tooltip-text").length)
        for (index in 0..1) {
            val svg = host.querySelectorAll("svg").item(index) as Element
            assertEquals("currentColor", svg.getAttribute("stroke"))
            assertEquals("none", svg.getAttribute("fill"))
            assertEquals("rgb(17, 34, 51)", window.getComputedStyle(svg).getPropertyValue("stroke"))
            assertCentered(svg.querySelector("path")!!.asDynamic().getBBox())
        }
    }

    @Test
    fun immersionIconIsAContinuousFiveCrossingPentagram() {
        composition = renderComposable(host) { MessageSpan(ImmersedSurface("Self-crossing faces")) }
        assertNull(host.querySelector(".fa-star-o"))
        val polygon = host.querySelector("polygon")!!
        val points = polygon.getAttribute("points")!!.split(' ').map { point ->
            point.split(',').map(String::toDouble)
        }
        assertEquals(5, points.size)
        fun side(a: List<Double>, b: List<Double>, p: List<Double>): Double =
            (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
        var crossings = 0
        for (i in points.indices) for (j in i + 1 until points.size) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val c = points[j]
            val d = points[(j + 1) % points.size]
            if (side(a, b, c) * side(a, b, d) < 0.0 && side(c, d, a) * side(c, d, b) < 0.0) crossings++
        }
        assertEquals(5, crossings)
        assertCentered(polygon.asDynamic().getBBox())
        assertTrue(host.querySelector(".tooltip-text")!!.textContent!!.contains("Click to add Resolved"))
    }

    @Test
    fun changingAnIndicatorReplacesItsSvgWithoutLeavingTheOldDrawing(): Promise<Unit> {
        val symbol = mutableStateOf(IndicatorSymbol.Warning)
        composition = renderComposable(host) { IndicatorIcon(symbol.value) }
        assertNotNull(host.querySelector("path"))
        symbol.value = IndicatorSymbol.Pentagram
        return Promise<Unit> { resolve, _ ->
            window.requestAnimationFrame {
                window.requestAnimationFrame {
                    resolve(Unit)
                }
            }
        }.then {
            assertNull(host.querySelector("path"))
            assertEquals(1, host.querySelectorAll("polygon").length)
            assertEquals(1, host.querySelectorAll("svg").length)
        }
    }

    private fun assertCentered(bounds: dynamic) {
        assertEquals(12.0, (bounds.x as Double) + (bounds.width as Double) / 2.0, 0.001)
        assertEquals(12.0, (bounds.y as Double) + (bounds.height as Double) / 2.0, 0.001)
    }
}
