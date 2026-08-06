import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import polyhedra.js.catalog.Seeds
import polyhedra.js.catalog.Transform
import polyhedra.js.main.ControlPane
import polyhedra.js.main.Popup
import polyhedra.js.poly.PolyParams
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransformMacroUiTest {
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
    fun addPopupSeparatesPrimitiveTransformsFromMacros() {
        val params = PolyParams("", null)
        composition = renderComposable(host) {
            ControlPane(params, popup = Popup.AddTransform, togglePopup = {})
        }

        val rows = host.querySelectorAll(".dropdown .text-row")
        val optionsBySection = linkedMapOf<String, MutableList<String>>()
        var section = ""
        for (index in 0 until rows.length) {
            val row = rows.item(index) as HTMLElement
            val header = row.querySelector(".header")?.textContent?.trim()
            if (header != null) {
                section = header
                optionsBySection.getOrPut(section, ::mutableListOf)
            } else {
                row.querySelector(".item")?.textContent?.trim()?.let {
                    optionsBySection.getOrPut(section, ::mutableListOf) += it
                }
            }
        }

        assertEquals(listOf("Transform", "Macro"), optionsBySection.keys.toList())
        assertTrue("Cantellated" !in optionsBySection.getValue("Transform"))
        assertTrue("Bevelled" !in optionsBySection.getValue("Transform"))
        assertEquals(
            listOf("Kis", "Join", "Needle", "Zip", "Cantellated", "Bevelled", "Ortho", "Meta", "Gyro"),
            optionsBySection.getValue("Macro"),
        )
    }

    @Test
    fun equivalentPrefixIsOfferedInPlaceAndReplacedOnlyOnClick(): Promise<Unit> {
        val params = PolyParams("", null)
        params.seed.updateValue(Seeds.single { it.tag == "C" })
        params.transforms.updateValue(listOf(Transform.Dual, Transform.Truncated, Transform.Dual))
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        val suggestion = host.querySelector(".prefix-replacement-suggestion") as HTMLDivElement
        val button = suggestion.querySelector("button") as HTMLElement
        assertTrue(
            button.textContent.orEmpty().startsWith("→ Kis"),
            "Unexpected suggestion text: ${button.textContent}",
        )
        assertTrue(
            suggestion.previousElementSibling?.textContent.orEmpty().contains("Dual"),
            "Suggestion must follow the displayed Dual Truncated Dual prefix",
        )
        assertTrue(
            suggestion.nextElementSibling?.textContent.orEmpty().contains("Cube"),
            "Suggestion must precede the seed",
        )
        assertEquals(listOf(Transform.Dual, Transform.Truncated, Transform.Dual), params.transforms.value)

        button.click()

        assertEquals(listOf(Transform.Kis), params.transforms.value)
        return awaitRecomposition().then {
            assertNull(host.querySelector(".prefix-replacement-suggestion"))
        }
    }

    @Test
    fun dualNeedlePrefixIsOfferedAsTruncated(): Promise<Unit> {
        val params = PolyParams("", null)
        params.seed.updateValue(Seeds.single { it.tag == "C" })
        params.transforms.updateValue(listOf(Transform.Needle, Transform.Dual))
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        val suggestion = host.querySelector(".prefix-replacement-suggestion") as HTMLDivElement
        val button = suggestion.querySelector("button") as HTMLElement
        assertTrue(
            button.textContent.orEmpty().startsWith("→ Truncated"),
            "Unexpected suggestion text: ${button.textContent}",
        )
        assertEquals(listOf(Transform.Needle, Transform.Dual), params.transforms.value)

        button.click()

        assertEquals(listOf(Transform.Truncated), params.transforms.value)
        return awaitRecomposition().then {
            assertNull(host.querySelector(".prefix-replacement-suggestion"))
        }
    }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }
}
