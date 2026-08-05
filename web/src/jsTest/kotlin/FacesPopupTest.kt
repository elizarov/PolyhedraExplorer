package polyhedra.js.main

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import polyhedra.common.poly.FaceKind
import polyhedra.js.params.SetParam
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

        icon.click()
        assertEquals(kinds, hiddenFaces.value)

        return awaitRecomposition().then {
            icon = visibilityIcon()
            assertTrue(icon.classList.contains("fa-circle-o"), "The header icon must show that all faces are hidden")
            icon.click()
            assertEquals(emptySet(), hiddenFaces.value)
            awaitRecomposition()
        }.then {
            icon = visibilityIcon()
            assertTrue(icon.classList.contains("fa-circle"), "The header icon must show that faces are visible")
        }
    }

    private fun hiddenFacesParam() = SetParam<FaceKind>("hidden", emptySet()) { null }

    private fun visibilityIcon(): HTMLElement = host.querySelector("i") as HTMLElement

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }
}
