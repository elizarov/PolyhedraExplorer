package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import polyhedra.model.api.CoreTransformTweakRange
import polyhedra.model.api.TransformTweak
import polyhedra.web.catalog.Transform
import polyhedra.web.catalog.withTweak
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransformSettingsUiTest {
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
    fun gearFollowsNameAndSliderUpdatesTransformInBothDirections(): Promise<Unit> {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Truncated))
        composition = renderComposable(host) {
            ControlPane(params, Popup.TransformSettings(0), togglePopup = {})
        }

        val name = host.querySelector("button.txt") as HTMLElement
        assertTrue(name.nextElementSibling?.classList?.contains("transform-settings-button") == true)
        val slider = host.querySelector(".transform-setting-slider") as HTMLInputElement
        val reset = host.querySelector(".transform-settings-reset") as HTMLButtonElement
        assertEquals("100", slider.value)
        assertTrue(reset.disabled)

        slider.value = "70"
        slider.dispatchEvent(Event("input"))
        assertEquals("t~d=0.7", params.transforms.value.single().tag)

        return awaitRecomposition().then {
            assertEquals("70", (host.querySelector(".transform-setting-slider") as HTMLInputElement).value)
            assertTrue(host.querySelector(".transform-setting-value")?.textContent == "70%")
            val updatedReset = host.querySelector(".transform-settings-reset") as HTMLButtonElement
            assertTrue(!updatedReset.disabled)
            updatedReset.click()
            awaitRecomposition()
        }.then {
            assertEquals("t", params.transforms.value.single().tag)
            assertEquals("100", (host.querySelector(".transform-setting-slider") as HTMLInputElement).value)
        }
    }

    @Test
    fun onlyLastTransformHasGearAndChiralityLivesInsideSettings() {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Truncated, Transform.Snub))

        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }
        assertEquals(1, host.querySelectorAll(".transform-settings-button").length)
        assertNull(host.querySelector(".chirality-flip"))

        composition?.dispose()
        composition = renderComposable(host) {
            ControlPane(params, popup = Popup.TransformSettings(0), togglePopup = {})
        }
        assertNull(host.querySelector(".transform-settings"))
        assertEquals(1, host.querySelectorAll(".transform-settings-button").length)

        composition?.dispose()
        composition = renderComposable(host) {
            ControlPane(params, popup = Popup.TransformSettings(1), togglePopup = {})
        }
        assertNotNull(host.querySelector(".chirality-flip"))
        assertEquals(2, host.querySelectorAll(".transform-setting-slider").length)
    }

    @Test
    fun sliderUsesGeometrySafeRangeReturnedByCore() {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Truncated))
        params.updateTransformTweakRanges(
            listOf(
                listOf(CoreTransformTweakRange(TransformTweak.Depth, min = 0.423, max = 1.137))
            )
        )

        composition = renderComposable(host) {
            ControlPane(params, Popup.TransformSettings(0), togglePopup = {})
        }

        val slider = host.querySelector(".transform-setting-slider") as HTMLInputElement
        assertEquals("43", slider.min)
        assertEquals("113", slider.max)
        slider.value = slider.min
        slider.dispatchEvent(Event("input"))
        assertEquals("t~d=0.43", params.transforms.value.single().tag)
    }

    @Test
    fun nonDefaultTweaksRoundTripWhileDefaultsAreOmitted() {
        val source = RootParams()
        source.render.poly.transforms.updateValue(
            listOf(
                Transform.Truncated.withTweak(TransformTweak.Depth, 0.7),
                Transform.Bevelled.withTweak(TransformTweak.Distance, 0.8),
                Transform.SnubFlipped.withTweak(TransformTweak.Twist, 1.25),
            )
        )
        val encoded = source.toString()
        assertTrue("t~d=0.7" in encoded)
        assertTrue("b~c=0.8" in encoded)
        assertTrue("s'~r=1.25" in encoded)

        val restored = RootParams()
        restored.loadFromString(encoded)

        assertEquals(
            source.render.poly.transforms.value.map { it.tag },
            restored.render.poly.transforms.value.map { it.tag },
        )
        assertEquals("t", Transform.Truncated.withTweak(TransformTweak.Depth, 1.0).tag)
    }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }
}
