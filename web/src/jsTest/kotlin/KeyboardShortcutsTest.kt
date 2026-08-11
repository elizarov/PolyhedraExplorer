package polyhedra.web

import androidx.compose.runtime.Composition
import androidx.compose.runtime.mutableStateOf
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import polyhedra.core.poly.Seed
import polyhedra.core.poly.TruncatedCube
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.EventTarget
import org.w3c.dom.events.KeyboardEvent
import polyhedra.model.api.SeedFamily
import polyhedra.model.poly.FaceKind
import polyhedra.web.catalog.FamilySeeds
import polyhedra.web.catalog.Transform
import polyhedra.web.main.ControlKeyboardActions
import polyhedra.web.main.ControlPane
import polyhedra.web.main.HelpButton
import polyhedra.web.main.KeyboardCommand
import polyhedra.web.main.KeyboardHelpPopup
import polyhedra.web.main.Popup
import polyhedra.web.main.RootPane
import polyhedra.web.main.RootParams
import polyhedra.web.main.adjacentOrbit
import polyhedra.web.main.faceVisibilityAfterSpace
import polyhedra.web.main.navigateInspectionOrbit
import polyhedra.web.main.isKeyboardInputTarget
import polyhedra.web.main.keyboardCommandFor
import polyhedra.web.poly.PolyParams
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyboardShortcutsTest {
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
    fun everyDocumentedKeyMapsToItsCommand() {
        val expected = mapOf(
            "ArrowLeft" to KeyboardCommand.PreviousItem,
            "ArrowRight" to KeyboardCommand.NextItem,
            "ArrowUp" to KeyboardCommand.PreviousDetail,
            "ArrowDown" to KeyboardCommand.NextDetail,
            "Enter" to KeyboardCommand.ConfirmSelection,
            "+" to KeyboardCommand.AddTransform,
            "Delete" to KeyboardCommand.DeleteTransform,
            "Backspace" to KeyboardCommand.DeleteTransform,
            "Escape" to KeyboardCommand.ClosePopups,
            "Esc" to KeyboardCommand.ClosePopups,
            " " to KeyboardCommand.ToggleFaceVisibility,
            "f" to KeyboardCommand.ToggleFacesPopup,
            "E" to KeyboardCommand.ToggleEdgesPopup,
            "v" to KeyboardCommand.ToggleVerticesPopup,
            "Y" to KeyboardCommand.ToggleSymmetry,
            "r" to KeyboardCommand.ToggleRotation,
            "c" to KeyboardCommand.ToggleConfig,
            "X" to KeyboardCommand.ToggleExport,
            "s" to KeyboardCommand.ToggleSaves,
            "?" to KeyboardCommand.ToggleHelp,
        )

        for ((key, command) in expected) assertEquals(command, keyboardCommandFor(key), key)
        assertNull(keyboardCommandFor("f", ctrlKey = true))
        assertNull(keyboardCommandFor("s", altKey = true))
        assertNull(keyboardCommandFor("x", metaKey = true))
        assertNull(keyboardCommandFor("t"))
    }

    @Test
    fun editableControlsSuppressGlobalShortcuts() {
        val input = document.createElement("input") as HTMLElement
        val textarea = document.createElement("textarea") as HTMLElement
        val select = document.createElement("select") as HTMLElement
        val editable = document.createElement("div") as HTMLElement
        editable.setAttribute("contenteditable", "true")
        val button = document.createElement("button") as HTMLElement

        for (element in listOf(input, textarea, select, editable, button)) host.appendChild(element)

        assertTrue(isKeyboardInputTarget(input))
        assertTrue(isKeyboardInputTarget(textarea))
        assertTrue(isKeyboardInputTarget(select))
        assertTrue(isKeyboardInputTarget(editable))
        assertFalse(isKeyboardInputTarget(button))
        assertFalse(isKeyboardInputTarget(document.body))
    }

    @Test
    fun inspectionRowsWrapAndFacesSpaceTogglesOnlyTheSelection() {
        val alpha = FaceKind(0)
        val beta = FaceKind(1)
        val gamma = FaceKind(2)
        val kinds = linkedSetOf(alpha, beta, gamma)

        assertEquals(alpha, adjacentOrbit(kinds, null, 1))
        assertEquals(gamma, adjacentOrbit(kinds, null, -1))
        assertEquals(beta, adjacentOrbit(kinds, alpha, 1))
        assertEquals(alpha, adjacentOrbit(kinds, gamma, 1))
        assertEquals(gamma, adjacentOrbit(kinds, alpha, -1))

        assertEquals(setOf(beta), faceVisibilityAfterSpace(emptySet(), kinds, beta, individual = true))
        assertEquals(emptySet(), faceVisibilityAfterSpace(setOf(beta), kinds, beta, individual = true))
        assertNull(faceVisibilityAfterSpace(emptySet(), kinds, null, individual = true))
        assertEquals(kinds, faceVisibilityAfterSpace(emptySet(), kinds, null, individual = false))
        assertEquals(emptySet(), faceVisibilityAfterSpace(setOf(alpha), kinds, null, individual = false))

        val params = PolyParams("", null)
        val poly = Seed.TruncatedCube.poly
        assertTrue(navigateInspectionOrbit(Popup.Faces, poly, params, 1))
        assertEquals(poly.faceKinds.keys.first(), params.selectedFace.value)
        assertTrue(navigateInspectionOrbit(Popup.Edges, poly, params, 1))
        assertEquals(poly.edgeKinds.keys.first(), params.selectedEdge.value)
        assertTrue(navigateInspectionOrbit(Popup.Vertices, poly, params, 1))
        assertEquals(poly.vertexKinds.keys.first(), params.selectedVertex.value)
    }

    @Test
    fun helpPopupShowsVersionAndEveryShortcut() {
        composition = renderComposable(host) { KeyboardHelpPopup("9.8.7-test") }

        assertEquals("Polyhedra Explorer", host.querySelector(".keyboard-help-title")?.textContent)
        assertEquals("Version 9.8.7-test", host.querySelector(".keyboard-help-version")?.textContent)
        assertEquals(
            KeyboardCommand.entries.size,
            host.querySelectorAll(".keyboard-help-table tr").length,
        )
        assertEquals(
            KeyboardCommand.entries.map(KeyboardCommand::displayedKey),
            elements(".keyboard-help-keycap").map { it.textContent },
        )
        for (command in KeyboardCommand.entries) {
            assertTrue(host.textContent.orEmpty().contains(command.description), command.name)
        }
    }

    @Test
    fun helpButtonIsAccessibleAndToggles() {
        var clicks = 0
        composition = renderComposable(host) { HelpButton(active = true) { clicks++ } }

        val wrapper = assertNotNull(host.querySelector(".btn.help"))
        assertTrue(wrapper.classList.contains("active"))
        val button = assertNotNull(wrapper.querySelector("button") as? HTMLButtonElement)
        assertEquals("Keyboard help", button.getAttribute("aria-label"))
        assertEquals("?Keyboard help", button.textContent)
        button.click()
        assertEquals(1, clicks)
    }

    @Test
    fun controlKeyboardActionsReuseDisplayedNavigationBehavior(): Promise<Unit> {
        val params = PolyParams("", null)
        val actions = ControlKeyboardActions()
        var requestedPopup: Popup? = null
        composition = renderComposable(host) {
            ControlPane(params, popup = null, togglePopup = { requestedPopup = it }, keyboardActions = actions)
        }

        assertTrue(actions.adjustHorizontal(1))
        assertEquals("C", params.seed.value.tag)
        return awaitRecomposition().then {
            assertFalse(actions.adjustVertical(-1))
            assertTrue(actions.addTransform())
            assertEquals(Popup.AddTransform, requestedPopup)
            assertTrue(actions.deleteTransform())
            assertEquals("T", params.seed.value.tag)
        }
    }

    @Test
    fun verticalArrowsFollowFamilyButtonsShownOnScreen() {
        val params = PolyParams("", null)
        params.seed.updateValue(
            FamilySeeds.single { seed ->
                seed.familyId?.run { family == SeedFamily.Prism && n == 5 } == true
            }
        )
        val actions = ControlKeyboardActions()
        composition = renderComposable(host) {
            ControlPane(params, popup = null, togglePopup = {}, keyboardActions = actions)
        }

        assertTrue(actions.adjustVertical(-1))
        assertEquals("P6", params.seed.value.tag, "ArrowUp must match the displayed increment button")
    }

    @Test
    fun addTransformPopupHighlightsWithArrowsAndAddsWithEnter(): Promise<Unit> {
        val params = PolyParams("", null)
        val actions = ControlKeyboardActions()
        val popup = mutableStateOf<Popup?>(Popup.AddTransform)
        composition = renderComposable(host) {
            ControlPane(
                params,
                popup = popup.value,
                togglePopup = { popup.value = it },
                keyboardActions = actions,
            )
        }

        val initial = assertNotNull(host.querySelector(".dropdown .keyboard-selected"))
        val initialName = initial.textContent
        assertEquals("true", initial.getAttribute("aria-selected"))
        assertTrue(actions.navigateAddTransform(1))

        return awaitRecomposition().then {
            val selected = assertNotNull(host.querySelector(".dropdown .keyboard-selected"))
            assertTrue(selected.textContent != initialName)
            val selectedName = selected.textContent.orEmpty()
            assertTrue(actions.confirmAddTransform())
            assertEquals(selectedName, params.transforms.value.single().toString())
            assertNull(popup.value)
        }
    }

    @Test
    fun enterAcceptsVisiblePrefixReplacementOnMainScreen() {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Needle, Transform.Dual))
        val actions = ControlKeyboardActions()
        composition = renderComposable(host) {
            ControlPane(params, popup = null, togglePopup = {}, keyboardActions = actions)
        }

        assertTrue(host.textContent.orEmpty().contains("→ Truncated"))
        assertTrue(actions.acceptSuggestion())
        assertEquals(listOf(Transform.Truncated), params.transforms.value)
    }

    @Test
    fun rootPaneRoutesGlobalKeysAndTogglesHelp(): Promise<Unit> {
        val params = RootParams()
        composition = renderComposable(host) { RootPane(params) }

        dispatchKey("ArrowRight")
        assertEquals("C", params.render.poly.seed.value.tag)
        dispatchKey("r")
        assertFalse(params.animationParams.animatedRotation.value)

        return awaitRecomposition().then {
            dispatchKey("Delete")
            assertEquals("T", params.render.poly.seed.value.tag)
            dispatchKey("c")
            awaitRecomposition()
        }.then {
            assertNotNull(host.querySelector("aside.drawer.config"), "Config popup must open")
            val configInput = assertNotNull(host.querySelector("aside.drawer.config input"))
            dispatchKey("Escape", configInput)
            awaitRecomposition()
        }.then {
            assertNull(host.querySelector("aside.drawer.config"))
            dispatchKey("?")
            awaitRecomposition()
        }.then {
            assertNotNull(host.querySelector("aside.drawer.help"), "Help popup must open")
            assertTrue(host.textContent.orEmpty().contains("Version "))
            dispatchKey("?")
            awaitRecomposition()
        }.then {
            assertNull(host.querySelector("aside.drawer.help"))
            dispatchKey("+")
            awaitRecomposition()
        }.then {
            assertNotNull(host.querySelector("aside.dropdown"), "Add popup must open")
            dispatchKey("Escape")
            awaitRecomposition()
        }.then {
            assertNull(host.querySelector("aside.dropdown"))
        }
    }

    private fun elements(selector: String): List<HTMLElement> {
        val nodes = host.querySelectorAll(selector)
        return List(nodes.length) { index -> nodes.item(index) as HTMLElement }
    }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }

    private fun dispatchKey(key: String, target: EventTarget = document) {
        val init = js("({})")
        init.key = key
        init.bubbles = true
        init.cancelable = true
        val event = js("new KeyboardEvent('keydown', init)").unsafeCast<KeyboardEvent>()
        target.dispatchEvent(event)
    }
}
