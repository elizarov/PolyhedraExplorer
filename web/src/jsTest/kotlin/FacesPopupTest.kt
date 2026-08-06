package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import polyhedra.model.poly.FaceKind
import polyhedra.web.main.*
import polyhedra.web.params.SetParam
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FacesPopupTest {
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
    fun faceVisibilityRowPropagatesUpdatesInBothDirections(): Promise<Unit> {
        val kind = FaceKind(0)
        val hiddenFaces = hiddenFacesParam()
        composition = renderComposable(host) {
            FaceVisibilityControl(hiddenFaces, kind, attentionWhenHidden = false)
        }
        var icon = visibilityIcon()

        icon.click()
        assertEquals(setOf(kind), hiddenFaces.value)

        return awaitRecomposition().then {
            icon = visibilityIcon()
            assertTrue(icon.classList.contains("fa-circle-o"), "The row icon must show the hidden state")
            icon.click()
            assertEquals(emptySet(), hiddenFaces.value)
            awaitRecomposition()
        }.then {
            icon = visibilityIcon()
            assertTrue(icon.classList.contains("fa-circle"), "The row icon must show the visible state")
        }
    }

    @Test
    fun allFacesVisibilityHeaderPropagatesUpdatesInBothDirections(): Promise<Unit> {
        val kinds = setOf(FaceKind(0), FaceKind(1))
        val hiddenFaces = hiddenFacesParam()
        composition = renderComposable(host) { AllFacesVisibilityControl(hiddenFaces, kinds) }
        var icon = visibilityIcon()
        assertTooltip("Hide all face orbits")

        icon.click()
        assertEquals(kinds, hiddenFaces.value)

        return awaitRecomposition().then {
            icon = visibilityIcon()
            assertTrue(icon.classList.contains("fa-circle-o"), "The header icon must show that all faces are hidden")
            assertTooltip("Show all face orbits")
            hiddenFaces.updateValue(setOf(kinds.first()))
            awaitRecomposition()
        }.then {
            icon = visibilityIcon()
            assertTrue(icon.classList.contains("fa-dot-circle-o"), "The header icon must show mixed visibility")
            assertTooltip("Show all face orbits")
            icon.click()
            assertEquals(emptySet(), hiddenFaces.value)
            awaitRecomposition()
        }.then {
            icon = visibilityIcon()
            assertTrue(icon.classList.contains("fa-circle"), "The header icon must show that faces are visible")
        }
    }

    @Test
    fun bottomFacesVisibilityControlIsCircularAndUsesTheSharedBehavior(): Promise<Unit> {
        val kinds = setOf(FaceKind(0), FaceKind(1))
        val hiddenFaces = hiddenFacesParam()
        composition = renderComposable(host) { BottomFacesVisibilityControl(hiddenFaces, kinds) }

        val wrapper = assertNotNull(host.querySelector(".btn.faces-visibility"))
        val button = assertNotNull(wrapper.querySelector("button.face-visibility-toggle.square")) as HTMLElement
        assertTooltip("Hide all face orbits")
        button.click()
        assertEquals(kinds, hiddenFaces.value)

        return awaitRecomposition().then {
            assertTrue(visibilityIcon().classList.contains("fa-circle-o"))
            assertTooltip("Show all face orbits")
        }
    }

    private fun hiddenFacesParam() = SetParam<FaceKind>("hidden", emptySet()) { null }

    private fun visibilityIcon(): HTMLElement = host.querySelector("i") as HTMLElement

    private fun assertTooltip(expected: String) {
        val button = assertNotNull(host.querySelector("button.face-visibility-toggle")) as HTMLElement
        assertEquals(expected, button.getAttribute("aria-label"))
        assertEquals(expected, button.querySelector(".tooltip-text")?.textContent)
    }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }
}
