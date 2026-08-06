package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import polyhedra.core.poly.Cube
import polyhedra.core.poly.Seed as CoreSeed
import polyhedra.model.api.CoreResponse
import polyhedra.model.api.CoreState
import polyhedra.model.api.FamilySeedId
import polyhedra.model.api.SeedFamily
import polyhedra.web.catalog.FamilySeeds
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeedFamilyUiTest {
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
    fun familiesAppearAfterPlatonicWithDefaultSizeThree() {
        val params = PolyParams("", null)
        composition = renderComposable(host) { ControlPane(params, popup = Popup.Seed, togglePopup = {}) }

        assertEquals(
            listOf("Platonic", "Families", "Archimedean", "Catalan"),
            elements(".dropdown .header").map { it.textContent.orEmpty().trim() },
        )
        assertEquals(
            listOf("Prism 3", "Antiprism 3", "Pyramid 3", "Bipyramid 3"),
            elements(".dropdown .header")[1].parentElement?.let { headerRow ->
                generateSequence(headerRow.nextElementSibling) { it.nextElementSibling }
                    .takeWhile { it.querySelector(".header") == null }
                    .map { it.textContent.orEmpty().trim() }
                    .toList()
            },
        )
    }

    @Test
    fun familySizeControlsRespectBoundsAndRoundTripInUrl(): Promise<Unit> {
        val params = PolyParams("", null)
        params.seed.updateValue(familySeed(SeedFamily.Pyramid, 3))
        params.transforms.updateValue(listOf(Transform.Dual))
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        var increment = host.querySelector(".family-seed-increment") as HTMLButtonElement
        var decrement = host.querySelector(".family-seed-decrement") as HTMLButtonElement
        assertFalse(increment.disabled)
        assertTrue(decrement.disabled)
        assertTrue(seedButton().textContent.orEmpty().contains("Pyramid 3"))

        increment.click()
        assertEquals("Y4", params.seed.value.tag)

        return awaitRecomposition().then {
            assertTrue(seedButton().textContent.orEmpty().contains("Pyramid 4"))
            params.seed.updateValue(familySeed(SeedFamily.Pyramid, 100))
            awaitRecomposition()
        }.then {
            increment = host.querySelector(".family-seed-increment") as HTMLButtonElement
            decrement = host.querySelector(".family-seed-decrement") as HTMLButtonElement
            assertTrue(increment.disabled)
            assertFalse(decrement.disabled)

            val source = RootParams()
            source.render.poly.seed.updateValue(familySeed(SeedFamily.Prism, 37))
            val restored = RootParams()
            restored.loadFromString(source.toString())
            assertEquals("P37", restored.render.poly.seed.value.tag)
        }
    }

    @Test
    fun overlappingFamilyMemberOffersClickableCatalogReplacement(): Promise<Unit> {
        val state = CoreState("P4", emptyList(), "c")
        val response = CoreResponse(
            poly = CoreSeed.Cube.poly,
            polyName = "Prism 4",
            recognizedSeedTag = "C",
            transformedPolys = emptyList(),
            validTransformTags = emptyList(),
            availableOrbitTransforms = emptyList(),
            warnings = emptyList(),
        )
        val params = PolyParams("", null)
        params.seed.updateValue(familySeed(SeedFamily.Prism, 4))
        params.updateSuggestedSeed(state, response)
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        val suggestion = host.querySelector(".suggestion button") as HTMLElement
        assertTrue(suggestion.textContent.orEmpty().contains("Cube"))
        suggestion.click()

        assertEquals(Seeds.single { it.tag == "C" }, params.seed.value)
        assertNull(params.suggestedSeed)
        return awaitRecomposition().then {
            assertNull(host.querySelector(".suggestion"))
        }
    }

    private fun familySeed(family: SeedFamily, n: Int) =
        FamilySeeds.single { it.familyId == FamilySeedId(family, n) }

    private fun seedButton(): HTMLElement = elements(".ctrl-pane > .btn > button.txt")
        .single { it.textContent.orEmpty().contains("Pyramid") }

    private fun elements(selector: String): List<HTMLElement> {
        val nodes = host.querySelectorAll(selector)
        return (0 until nodes.length).map { nodes.item(it) as HTMLElement }
    }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }
}
