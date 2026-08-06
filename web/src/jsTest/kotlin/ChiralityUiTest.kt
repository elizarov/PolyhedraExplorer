package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import polyhedra.web.catalog.Seeds
import polyhedra.web.catalog.Transform
import polyhedra.web.main.ControlPane
import polyhedra.web.main.Popup
import polyhedra.web.main.RootParams
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.PolyParams
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChiralityUiTest {
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
        composition = null
        host.parentNode?.removeChild(host)
    }

    @Test
    fun lastSnubTransformHasFlipControlAndDisplaysPrimeAfterClick(): Promise<Unit> {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Dual, Transform.Snub))
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        val flip = host.querySelector(".chirality-flip") as HTMLElement
        assertTrue(flip.previousElementSibling?.textContent.orEmpty().contains("Snub"))

        flip.click()

        assertEquals(listOf("d", "s'"), params.transforms.value.map { it.tag })
        return awaitRecomposition().then {
            assertTrue(flip.parentElement?.textContent.orEmpty().contains("Snub'"))
        }
    }

    @Test
    fun flipControlOnlyAppearsForTheLastChainItem() {
        val params = PolyParams("", null)
        params.seed.updateValue(Seeds.single { it.tag == "sC" })
        params.transforms.updateValue(listOf(Transform.Snub, Transform.Dual))
        composition = renderComposable(host) { ControlPane(params, popup = Popup.Seed, togglePopup = {}) }

        assertNull(host.querySelector(".chirality-flip"))
        assertEquals(31, host.querySelectorAll(".dropdown .item").length)
    }

    @Test
    fun lastGyroMacroCanBeFlipped() {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Gyro))
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        (host.querySelector(".chirality-flip") as HTMLElement).click()

        assertEquals(listOf("g'"), params.transforms.value.map { it.tag })
        assertEquals("Gyro'", params.transforms.value.single().toString())
    }

    @Test
    fun lastChiralSeedCanBeFlippedAndRoundTripsInUrlState(): Promise<Unit> {
        val params = PolyParams("", null)
        params.seed.updateValue(Seeds.single { it.tag == "sC" })
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        val flip = host.querySelector(".chirality-flip") as HTMLElement
        flip.click()

        assertEquals("sC'", params.seed.value.tag)
        return awaitRecomposition().then {
            assertTrue(flip.parentElement?.textContent.orEmpty().contains("Snub cube'"))

            val source = RootParams()
            source.render.poly.seed.updateValue(Seeds.single { it.tag == "sC'" })
            source.render.poly.transforms.updateValue(listOf(Transform.GyroFlipped))
            val restored = RootParams()
            restored.loadFromString(source.toString())

            assertEquals("sC'", restored.render.poly.seed.value.tag)
            assertEquals(listOf("g'"), restored.render.poly.transforms.value.map { it.tag })
        }
    }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }
}
