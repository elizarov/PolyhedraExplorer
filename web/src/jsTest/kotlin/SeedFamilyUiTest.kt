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
import polyhedra.core.poly.analyzeSymmetry
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
    fun familyPopupUsesFamilyNamesAndSelectsSizeThreeByDefault() {
        val params = PolyParams("", null)
        composition = renderComposable(host) { ControlPane(params, popup = Popup.Seed, togglePopup = {}) }

        assertEquals(
            listOf("Platonic", "Families", "Kepler-Poinsot", "Archimedean", "Catalan"),
            elements(".dropdown .header").map { it.textContent.orEmpty().trim() },
        )
        assertEquals(
            listOf("Prism", "Antiprism", "Pyramid", "Bipyramid"),
            elements(".dropdown .header")[1].parentElement?.let { headerRow ->
                generateSequence(headerRow.nextElementSibling) { it.nextElementSibling }
                    .takeWhile { it.querySelector(".header") == null }
                    .map { it.textContent.orEmpty().trim() }
                    .toList()
            },
        )

        elements(".dropdown .item").single { it.textContent == "Prism" }.click()
        assertEquals("P3", params.seed.value.tag)
    }

    @Test
    fun keplerPoinsotPopupShowsConwayFormsAndRoundTripsItsSeedTag() {
        val params = PolyParams("", null)
        composition = renderComposable(host) { ControlPane(params, popup = Popup.Seed, togglePopup = {}) }

        assertEquals(
            listOf("sD", "gD", "sgD = gsD", "gI"),
            elements(".seed-notation").map { element -> element.textContent.orEmpty() },
        )
        elements(".dropdown .item").single { element ->
            element.textContent.orEmpty().contains("Great icosahedron")
        }.click()
        assertEquals("GI", params.seed.value.tag)

        val source = RootParams()
        source.render.poly.seed.updateValue(Seeds.single { seed -> seed.tag == "GSD" })
        val restored = RootParams()
        restored.loadFromString(source.toString())
        assertEquals("GSD", restored.render.poly.seed.value.tag)
    }

    @Test
    fun horizontalNavigationKeepsFamilySizeAcrossFamiliesAndCatalogBoundary(): Promise<Unit> {
        val params = PolyParams("", null)
        params.seed.updateValue(familySeed(SeedFamily.Prism, 37))
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        (host.querySelector(".family-seed-increment") as HTMLButtonElement).click()
        seedSpinnerButton(1).click()
        assertEquals("A38", params.seed.value.tag)

        return awaitRecomposition().then {
            seedSpinnerButton(0).click()
            assertEquals("P38", params.seed.value.tag)
            awaitRecomposition()
        }.then {
            seedSpinnerButton(0).click()
            assertEquals("I", params.seed.value.tag)
            awaitRecomposition()
        }.then {
            seedSpinnerButton(0).click()
            assertEquals("D", params.seed.value.tag)
            awaitRecomposition()
        }.then {
            seedSpinnerButton(1).click()
            assertEquals("I", params.seed.value.tag)
            awaitRecomposition()
        }.then {
            seedSpinnerButton(1).click()
            assertEquals("P38", params.seed.value.tag)
        }
    }

    @Test
    fun dropdownKeepsFamilySizeThroughFixedSeedsWithTransformsPresent(): Promise<Unit> {
        val params = PolyParams("", null)
        params.seed.updateValue(familySeed(SeedFamily.Prism, 23))
        params.transforms.updateValue(listOf(Transform.Dual))
        composition = renderComposable(host) { ControlPane(params, popup = Popup.Seed, togglePopup = {}) }

        dropdownItem("Cube").click()
        assertEquals("C", params.seed.value.tag)
        assertEquals(listOf(Transform.Dual), params.transforms.value)

        return awaitRecomposition().then {
            dropdownItem("Pyramid").click()
            assertEquals("Y23", params.seed.value.tag)
            awaitRecomposition()
        }.then {
            dropdownItem("Octahedron").click()
            assertEquals("O", params.seed.value.tag)
            awaitRecomposition()
        }.then {
            dropdownItem("Antiprism").click()
            assertEquals("A23", params.seed.value.tag)
        }
    }

    @Test
    fun onlySeedLevelResetClearsRememberedFamilySize(): Promise<Unit> {
        val params = PolyParams("", null)
        params.seed.updateValue(familySeed(SeedFamily.Prism, 19))
        params.transforms.updateValue(listOf(Transform.Dual))
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        resetButton().click()
        assertEquals(emptyList(), params.transforms.value)
        assertEquals("P19", params.seed.value.tag)

        return awaitRecomposition().then {
            seedSpinnerButton(1).click()
            assertEquals("A19", params.seed.value.tag)
            awaitRecomposition()
        }.then {
            resetButton().click()
            assertEquals("T", params.seed.value.tag)
            awaitRecomposition()
        }.then {
            val next = seedSpinnerButton(1)
            repeat(5) { next.click() }
            assertEquals("P3", params.seed.value.tag)
        }
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
            symmetry = CoreSeed.Cube.poly.analyzeSymmetry(),
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

    private fun seedSpinnerButton(index: Int): HTMLButtonElement {
        val seed = elements(".ctrl-pane > .btn > button.txt")
            .single { button -> Seeds.any { button.textContent.orEmpty().contains(it.name) } }
        return generateSequence(seed.parentElement?.firstElementChild) { it.nextElementSibling }
            .filterIsInstance<HTMLButtonElement>()
            .elementAt(index)
    }

    private fun dropdownItem(name: String): HTMLElement =
        elements(".dropdown .item").single { it.textContent == name }

    private fun resetButton(): HTMLButtonElement =
        host.querySelector(".reset button") as HTMLButtonElement

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
