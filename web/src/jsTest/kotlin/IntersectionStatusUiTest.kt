package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import polyhedra.model.api.CoreGeometryAnalysis
import polyhedra.model.api.PolyhedronContract
import polyhedra.model.api.SurfaceIntersectionClass
import polyhedra.web.catalog.Seeds
import polyhedra.web.catalog.Transform
import polyhedra.web.main.ControlPane
import polyhedra.web.main.ControlKeyboardActions
import polyhedra.web.poly.PolyParams
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntersectionStatusUiTest {
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
    fun seedOnlyImmersionOwnsTheIndicatorAndClickAppendsResolved(): Promise<Unit> {
        val params = immersedParams()
        params.seed.updateValue(Seeds.single { it.tag == "SD" })
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        val indicator = requireNotNull(host.querySelector(".intersection-indicator")) as HTMLButtonElement
        assertEquals("Add Resolved transform", indicator.getAttribute("aria-label"))
        val owner = requireNotNull(indicator.parentElement)
        assertTrue(owner.textContent.orEmpty().contains("Stellated dodecahedron"))
        val tooltip = indicator.querySelector(".tooltip-text")?.textContent.orEmpty()
        assertTrue(tooltip.contains("Self-crossing source-face contacts: 5"))
        assertTrue(tooltip.contains("Crossings between face surfaces: 7"))

        indicator.click()
        assertEquals(listOf(Transform.Resolve), params.transforms.value)
        return awaitRecomposition().then {
            assertNull(host.querySelector(".intersection-indicator"))
        }
    }

    @Test
    fun transformedImmersionPlacesIndicatorOnlyOnLastTransform() {
        val params = immersedParams()
        params.transforms.updateValue(listOf(Transform.Canonical))
        val keyboard = ControlKeyboardActions()
        composition = renderComposable(host) {
            ControlPane(params, popup = null, togglePopup = {}, keyboardActions = keyboard)
        }

        val indicator = requireNotNull(host.querySelector(".intersection-indicator")) as HTMLElement
        val transformButtons = host.querySelectorAll(".ctrl-pane > .btn > button.txt")
        val owner = indicator.parentElement
        assertTrue(
            owner === (transformButtons.item(0) as HTMLElement).parentElement,
            "Transforms render last-first, so the last transform must own status",
        )
        assertTrue(owner?.textContent.orEmpty().contains("Canonical"))
        assertTrue(keyboard.acceptSuggestion(), "Enter action must accept the visible Resolved action")
        assertEquals(listOf(Transform.Canonical, Transform.Resolve), params.transforms.value)
    }

    @Test
    fun embeddedGeometryHasNoIndicator() {
        val params = PolyParams("", null)
        params.updateGeometryAnalysis(CoreGeometryAnalysis(PolyhedronContract.EmbeddedBoundary))
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        assertNull(host.querySelector(".intersection-indicator"))
    }

    private fun immersedParams() = PolyParams("", null).also { params ->
        params.updateGeometryAnalysis(
            CoreGeometryAnalysis(
                PolyhedronContract.RenderableImmersion,
                mapOf(
                    SurfaceIntersectionClass.SelfCrossingFace to 5,
                    SurfaceIntersectionClass.IntersectingFaces to 7,
                ),
            )
        )
    }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame { window.requestAnimationFrame { resolve(Unit) } }
    }
}
