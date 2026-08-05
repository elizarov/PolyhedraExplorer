package polyhedra.js.components

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.Event
import polyhedra.common.util.Tagged
import polyhedra.js.params.BooleanParam
import polyhedra.js.params.DoubleParam
import polyhedra.js.params.EnumParam
import polyhedra.js.params.ValueAnimationParams
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
    fun sliderPropagatesUpdatesInBothDirections(): Promise<Unit> {
        val animation = object : ValueAnimationParams {
            override val animateValueUpdatesDuration: Double = 0.5
        }
        val param = DoubleParam("value", 0.2, 0.0, 1.0, 0.01, animation)
        composition = renderComposable(host) { PSlider(param, showValue = false) }
        val input = host.querySelector("input[type=range]") as HTMLInputElement

        input.value = "70"
        input.dispatchEvent(Event("input"))
        assertEquals(0.7, param.targetValue, absoluteTolerance = 1e-12)

        return awaitRecomposition().then {
            assertEquals("70", input.value, "DOM value must follow the configured target")
            param.updateValue(0.4)
            awaitRecomposition()
        }.then {
            assertEquals("40", input.value, "Programmatic target changes must update the DOM value")
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
