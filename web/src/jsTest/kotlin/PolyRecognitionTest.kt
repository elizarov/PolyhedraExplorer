package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import polyhedra.core.poly.Seed as CoreSeed
import polyhedra.core.poly.SnubDodecahedron
import polyhedra.core.poly.analyzeSymmetry
import polyhedra.model.api.CoreResponse
import polyhedra.model.api.CoreState
import polyhedra.web.catalog.Seeds
import polyhedra.web.catalog.Transform
import polyhedra.web.main.ControlPane
import polyhedra.web.poly.PolyParams
import polyhedra.web.poly.shouldDetectSeed
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PolyRecognitionTest {
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
    fun detectionOnlyRunsForSeedOrTransformChainChanges() {
        val transformed = CoreState("I", listOf("t"), "c")

        assertEquals(true, shouldDetectSeed(null, transformed))
        assertEquals(true, shouldDetectSeed(transformed, transformed.copy(transformTags = listOf("a"))))
        assertEquals(true, shouldDetectSeed(transformed, transformed.copy(seedTag = "D")))
        assertEquals(false, shouldDetectSeed(transformed, transformed.copy(scaleTag = "m")))
        assertEquals(false, shouldDetectSeed(transformed, transformed))
        assertEquals(false, shouldDetectSeed(transformed, transformed.copy(transformTags = listOf("t~d=0.7"))))
        assertEquals(false, shouldDetectSeed(transformed, transformed.copy(transformTags = emptyList())))

        val family = CoreState("P4", emptyList(), "c")
        assertEquals(true, shouldDetectSeed(null, family))
        assertEquals(true, shouldDetectSeed(family, family.copy(seedTag = "P5")))
        assertEquals(false, shouldDetectSeed(family, family.copy(scaleTag = "m")))
    }

    @Test
    fun recognizedSolidIsOfferedAtRightAndOnlyReplacedOnClick(): Promise<Unit> {
        val state = CoreState("dsD'", listOf("d"), "c")
        val response = CoreResponse(
            poly = CoreSeed.SnubDodecahedron.poly,
            polyName = "Dual Pentagonal Hexecontahedron",
            symmetry = CoreSeed.SnubDodecahedron.poly.analyzeSymmetry(),
            recognizedSeedTag = "sD'",
            transformedPolys = listOf(CoreSeed.SnubDodecahedron.poly),
            validTransformTags = listOf("d"),
            availableOrbitTransforms = emptyList(),
            warnings = listOf(null),
        )
        val params = PolyParams("", null)
        params.seed.updateValue(Seeds.single { it.tag == "dsD'" })
        params.transforms.updateValue(listOf(Transform.Dual))

        params.updateSuggestedSeed(state, response)

        assertEquals("dsD'", params.seed.value.tag, "Detection must preserve the current seed")
        assertEquals(listOf(Transform.Dual), params.transforms.value, "Detection must preserve the transform chain")
        assertEquals("sD'", params.suggestedSeed?.tag)

        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }
        val controlItems = host.querySelectorAll(".ctrl-pane > .btn")
        val suggestion = host.querySelector(".ctrl-pane > .suggestion") as HTMLDivElement
        val suggestionButton = suggestion.querySelector("button") as HTMLElement

        assertTrue(controlItems.item(controlItems.length - 1) === suggestion, "Suggestion must be rightmost")
        assertTrue(suggestionButton.textContent.orEmpty().startsWith("→ Snub dodecahedron'"))

        suggestionButton.click()

        assertEquals("sD'", params.seed.value.tag)
        assertEquals(emptyList(), params.transforms.value)
        assertNull(params.suggestedSeed)

        return awaitRecomposition().then {
            assertNull(host.querySelector(".suggestion"), "Accepted suggestion must disappear")
        }
    }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }
}
