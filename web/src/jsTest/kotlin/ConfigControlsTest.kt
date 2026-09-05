package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.Event
import polyhedra.model.util.Tagged
import polyhedra.web.components.*
import polyhedra.web.main.RootParams
import polyhedra.web.main.ConfigPopup
import polyhedra.web.params.BooleanParam
import polyhedra.web.params.DoubleParam
import polyhedra.web.params.EnumParam
import polyhedra.web.params.Param
import polyhedra.web.params.ValueAnimationParams
import polyhedra.web.params.loadFromString
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigControlsTest {
    private lateinit var host: HTMLDivElement
    private lateinit var composition: Composition

    @BeforeTest
    fun setUp() {
        host = document.createElement("div") as HTMLDivElement
        document.body!!.appendChild(host)
    }

    @AfterTest
    fun tearDown() {
        if (::composition.isInitialized) composition.dispose()
        host.parentNode?.removeChild(host)
    }

    @Test
    fun observerInvalidatesOnlyForSubscribedUpdates(): Promise<Unit> {
        val param = BooleanParam("enabled", false)
        var recompositions = 0
        composition = renderComposable(host) {
            param.observe()
            recompositions++
            Text(param.value.toString())
        }

        assertEquals(1, recompositions)
        param.notifyUpdated(Param.AnimatedValue)

        return awaitRecomposition().then {
            assertEquals(1, recompositions, "Animated-only updates must not invalidate a target-value observer")
            param.updateValue(true)
            awaitRecomposition()
        }.then {
            assertEquals(2, recompositions)
            assertEquals("true", host.textContent)
        }
    }

    @Test
    fun sliderPropagatesUpdatesInBothDirections(): Promise<Unit> {
        val animation = object : ValueAnimationParams {
            override val animateValueUpdatesDuration: Double = 0.5
        }
        val param = DoubleParam("value", 0.2, 0.0, 1.0, 0.01, animation)
        composition = renderComposable(host) { PSlider(param, ariaLabel = "Test value") }
        val input = host.querySelector("input[type=range]") as HTMLInputElement
        val valueInput = host.querySelector(".slider-value-input") as HTMLInputElement

        assertEquals("0.2", valueInput.value)
        assertEquals("Test value", valueInput.getAttribute("aria-label"))

        input.value = "70"
        input.dispatchEvent(Event("input"))
        assertEquals(0.7, param.targetValue, absoluteTolerance = 1e-12)

        return awaitRecomposition().then {
            assertEquals("70", input.value, "DOM value must follow the configured target")
            assertEquals("0.7", valueInput.value, "Editable value must follow the slider")

            valueInput.value = "0.43"
            valueInput.dispatchEvent(Event("change"))
            assertEquals(0.43, param.targetValue, absoluteTolerance = 1e-12)
            awaitRecomposition()
        }.then {
            assertEquals("43", input.value, "Slider must follow the typed value")
            assertEquals("0.43", valueInput.value)
            param.updateValue(0.4)
            awaitRecomposition()
        }.then {
            assertEquals("40", input.value, "Programmatic target changes must update the DOM value")
            assertEquals("0.4", valueInput.value, "Editable value must follow programmatic changes")
        }
    }

    @Test
    fun editableSliderValueScalesClampsAndKeepsUnitOutside(): Promise<Unit> {
        val param = DoubleParam("value", 0.2, 0.0, 1.0, 0.01)
        composition = renderComposable(host) {
            PSlider(
                param,
                valueScale = 20.0,
                valuePrecision = 2,
                unit = "(mm)",
                ariaLabel = "Width in millimeters",
            )
        }
        val valueInput = host.querySelector(".slider-value-input") as HTMLInputElement

        assertEquals("4", valueInput.value)
        assertTrue(valueInput.classList.contains("slider-value-input"))
        assertEquals("(mm)", host.querySelector(".slider-unit")?.textContent)
        assertTrue(!valueInput.value.contains("mm"), "Units must not be part of the editable value")

        valueInput.value = "12,6"
        valueInput.dispatchEvent(Event("change"))
        assertEquals(0.63, param.targetValue, absoluteTolerance = 1e-12)

        return awaitRecomposition().then {
            assertEquals("12.6", valueInput.value)
            valueInput.value = "99"
            valueInput.dispatchEvent(Event("change"))
            assertEquals(1.0, param.targetValue, absoluteTolerance = 1e-12)
            awaitRecomposition()
        }.then {
            assertEquals("20", valueInput.value, "Typed values must be clamped to the slider range")
        }
    }

    @Test
    fun typedPhysicalValueIsNotRoundedToScaledSliderTick(): Promise<Unit> {
        val params = RootParams()
        params.export.size.updateValue(60.0)
        val rim = params.render.view.faceRim
        composition = renderComposable(host) {
            PSlider(
                rim,
                valueScale = 30.0,
                valuePrecision = 3,
                unit = "(mm)",
                snapInputToStep = false,
                ariaLabel = "Rim in millimeters",
            )
        }
        val valueInput = host.querySelector(".slider-value-input") as HTMLInputElement
        valueInput.value = "2"
        valueInput.dispatchEvent(Event("change"))

        assertEquals(2.0 / 30.0, rim.targetValue, absoluteTolerance = 1e-12)
        return awaitRecomposition().then {
            assertEquals("2", valueInput.value, "Typed millimeters must remain exact")
            assertEquals(
                "67",
                (host.querySelector("input[type=range]") as HTMLInputElement).value,
                "The slider may point at its nearest coarser tick without changing the typed value",
            )

            val serializationSource = RootParams()
            serializationSource.export.size.updateValue(60.0, Param.TargetValue)
            serializationSource.render.view.faceRim.updateUnsnappedValue(2.0 / 30.0, Param.TargetValue)
            val serialized = serializationSource.toString()
            val restored = RootParams()
            restored.loadFromString(serialized)
            val restoredMillimeters = restored.render.view.faceRim.targetValue * restored.export.size.targetValue / 2.0
            assertEquals(2.0, restoredMillimeters, absoluteTolerance = 1e-6)
        }
    }

    @Test
    fun checkboxPropagatesUpdatesInBothDirections(): Promise<Unit> {
        val param = BooleanParam("enabled", false)
        composition = renderComposable(host) { PCheckbox(param) }
        var input = host.querySelector("input[type=checkbox]") as HTMLInputElement

        (host.querySelector(".checkbox") as HTMLDivElement).click()
        assertTrue(param.value)

        return awaitRecomposition().then {
            input = host.querySelector("input[type=checkbox]") as HTMLInputElement
            assertTrue(input.checked, "DOM checked state must follow the parameter")
            param.updateValue(false)
            awaitRecomposition()
        }.then {
            input = host.querySelector("input[type=checkbox]") as HTMLInputElement
            assertFalse(input.checked, "Programmatic changes must update the DOM checked state")
        }
    }

    @Test
    fun cutControlEnablesItsSignedPositionSlider(): Promise<Unit> {
        val params = RootParams()
        composition = renderComposable(host) { ConfigPopup(params) }
        val cutRow = (0 until host.querySelectorAll("tr.control").length)
            .map { host.querySelectorAll("tr.control").item(it) as HTMLElement }
            .single { it.querySelector("td")?.textContent == "Cut" }
        val configRows = (0 until host.querySelectorAll("tr.control").length)
            .map { host.querySelectorAll("tr.control").item(it) as HTMLElement }
        val checkbox = cutRow.querySelector(".checkbox") as HTMLDivElement
        var range = cutRow.querySelector("input[type=range]") as HTMLInputElement

        assertTrue(configRows.all { row ->
            val cells = row.querySelectorAll("td")
            cells.length == 3 || cells.length == 2 &&
                (cells.item(0) as HTMLElement).getAttribute("colspan") == "2"
        }, "Rows without a checkbox must span the label and checkbox columns")
        assertFalse(params.render.view.cutEnabled.value)
        assertTrue(range.disabled)
        assertEquals("-100", range.min)
        assertEquals("100", range.max)
        assertEquals("50", range.value)

        checkbox.click()
        assertTrue(params.render.view.cutEnabled.value)
        return awaitRecomposition().then {
            range = cutRow.querySelector("input[type=range]") as HTMLInputElement
            assertFalse(range.disabled)
            range.value = "-35"
            range.dispatchEvent(Event("input"))
            assertEquals(-0.35, params.render.view.cutPosition.targetValue, absoluteTolerance = 1e-12)
            awaitRecomposition()
        }.then {
            assertEquals("-35", range.value)
        }
    }

    @Test
    fun dropdownPropagatesUpdatesInBothDirections(): Promise<Unit> {
        val first = Choice("first", "First")
        val second = Choice("second", "Second")
        val param = EnumParam("choice", first, listOf(first, second))
        composition = renderComposable(host) { PDropdown(param) }
        val select = host.querySelector("select") as HTMLSelectElement

        select.value = second.toString()
        select.dispatchEvent(Event("change"))
        assertEquals(second, param.value)

        return awaitRecomposition().then {
            assertEquals(second.toString(), select.value, "DOM selection must follow the parameter")
            param.updateValue(first)
            awaitRecomposition()
        }.then {
            assertEquals(first.toString(), select.value, "Programmatic changes must update the DOM selection")
        }
    }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }

    private data class Choice(
        override val tag: String,
        private val label: String,
    ) : Tagged {
        override fun toString(): String = label
    }
}
