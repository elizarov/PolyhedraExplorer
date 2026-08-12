package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Tr
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import polyhedra.model.poly.AnyKind
import polyhedra.model.poly.EdgeKind
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.VertexKind
import polyhedra.web.catalog.Drop
import polyhedra.web.catalog.KisFace
import polyhedra.web.catalog.RectifyVertex
import polyhedra.web.catalog.Transform
import polyhedra.web.catalog.TruncateVertex
import polyhedra.web.catalog.toTransformOrNull
import polyhedra.web.main.ControlPane
import polyhedra.web.main.OrbitTargetActions
import polyhedra.web.main.Popup
import polyhedra.web.poly.PolyParams
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DropOrbitUiTest {
    private lateinit var host: HTMLDivElement
    private var composition: Composition? = null

    private val vertexA = VertexKind(0)
    private val vertexB = VertexKind(1)
    private val faceAlpha = FaceKind(0)
    private val faceBeta = FaceKind(1)
    private val edge = EdgeKind(vertexA, vertexB, faceAlpha, faceBeta)

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
    fun concreteTargetedTagsRoundTripInTheBrowserModel() {
        assertEquals(KisFace(faceAlpha), KisFace(faceAlpha).tag.toTransformOrNull())
        assertEquals(TruncateVertex(vertexA), TruncateVertex(vertexA).tag.toTransformOrNull())
        assertEquals(RectifyVertex(vertexA), RectifyVertex(vertexA).tag.toTransformOrNull())
    }

    @Test
    fun addPopupGroupsConcreteOrbitOperationsAndAddsFirstTarget(): Promise<Unit> {
        val params = PolyParams("", null)
        params.updateAvailableOrbitTransforms(
            listOf(
                listOf(
                    Drop(vertexB),
                    Drop(faceAlpha),
                    Drop(edge),
                    Drop(vertexA),
                    KisFace(faceBeta),
                    KisFace(faceAlpha),
                    TruncateVertex(vertexB),
                    TruncateVertex(vertexA),
                    RectifyVertex(vertexB),
                    RectifyVertex(vertexA),
                ).map(Transform::tag),
            ),
        )
        composition = renderComposable(host) {
            ControlPane(params, popup = Popup.AddTransform, togglePopup = {})
        }

        assertEquals(
            linkedMapOf(
                "Transform" to listOf(
                    "Truncated", "Rectified", "Dual", "Snub", "Propeller", "Whirl", "Quinto",
                    "Chamfered", "Canonical",
                ),
                "Macro" to listOf("Kis", "Join", "Needle", "Zip", "Cantellated", "Bevelled", "Ortho", "Meta", "Gyro"),
                "Orbit-targeted" to listOf(
                    "Drop face",
                    "Drop edge",
                    "Drop vertex",
                    "Kis face",
                    "Truncate vertex",
                    "Rectify vertex",
                ),
                "Star" to listOf("Greatened", "Stellated"),
            ),
            dropdownOptionsBySection(),
        )

        dropdownItem("Drop vertex").click()

        assertEquals(listOf(Drop(vertexA)), params.transforms.value)
        return awaitRecomposition().then {
            assertEquals("Drop A", transformName())
        }
    }

    @Test
    fun lastDropHasRightSideControlsThatCycleWithinItsOrbitKind(): Promise<Unit> {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Drop(vertexB)))
        params.updateAvailableOrbitTransforms(
            listOf(listOf(Drop(faceAlpha), Drop(vertexA), Drop(edge), Drop(vertexB)).map(Transform::tag)),
        )
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        val name = transformNameElement()
        val previous = host.querySelector(".drop-orbit-previous") as HTMLElement
        val next = host.querySelector(".drop-orbit-next") as HTMLElement
        assertEquals(name, previousElementBeforeOrbitControls())

        next.click()
        assertEquals(listOf(Drop(vertexA)), params.transforms.value, "Next must wrap from vertex B to vertex A")

        return awaitRecomposition().then {
            (host.querySelector(".drop-orbit-previous") as HTMLElement).click()
            assertEquals(listOf(Drop(vertexB)), params.transforms.value, "Previous must wrap from vertex A to vertex B")
        }
    }

    @Test
    fun orbitControlsAreOnlyShownWhenDropIsLast() {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Drop(vertexA), Transform.Dual))
        params.updateAvailableOrbitTransforms(
            listOf(
                listOf(Drop(vertexA), Drop(vertexB)).map(Transform::tag),
                emptyList(),
            ),
        )
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        assertNull(host.querySelector(".drop-orbit-controls"))
    }

    @Test
    fun kisFaceControlsCycleOnlyThroughFaceTargets(): Promise<Unit> {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(KisFace(faceBeta)))
        params.updateAvailableOrbitTransforms(
            listOf(
                listOf(
                    Drop(faceAlpha),
                    KisFace(faceAlpha),
                    KisFace(faceBeta),
                    TruncateVertex(vertexA),
                    RectifyVertex(vertexA),
                ).map(Transform::tag),
            ),
        )
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        assertEquals("Kis $faceBeta", transformName())
        (host.querySelector(".drop-orbit-next") as HTMLElement).click()
        assertEquals(listOf(KisFace(faceAlpha)), params.transforms.value)

        return awaitRecomposition().then {
            assertEquals("Kis $faceAlpha", transformName())
        }
    }

    @Test
    fun modifyPopupReusesOrbitAfterPassingThroughRegularOperation(): Promise<Unit> {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(TruncateVertex(vertexB)))
        params.updateAvailableOrbitTransforms(vertexOperationTags())
        composition = renderComposable(host) {
            ControlPane(params, popup = Popup.ModifyTransform(0), togglePopup = {})
        }

        dropdownItem("Dual").click()
        assertEquals(listOf(Transform.Dual), params.transforms.value)

        return awaitRecomposition().then {
            dropdownItem("Rectify vertex").click()
            assertEquals(listOf(RectifyVertex(vertexB)), params.transforms.value)
        }
    }

    @Test
    fun horizontalNavigationReusesCurrentOrbitWhenChangingTargetedOperation() {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(TruncateVertex(vertexB)))
        params.updateAvailableOrbitTransforms(vertexOperationTags())
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        horizontalTransformButton(1).click()

        assertEquals(listOf(RectifyVertex(vertexB)), params.transforms.value)
    }

    @Test
    fun orbitPopupClickUpdatesRememberedOrbitForLaterOperationChanges() {
        val params = PolyParams("", null)
        params.updateAvailableOrbitTransforms(vertexOperationTags())
        renderOrbitTargetActions(params, vertexB)

        orbitTargetActionButtons()
            .single { ariaLabel(it) == "Truncate vertex orbit $vertexB" }
            .click()
        assertEquals(listOf(TruncateVertex(vertexB)), params.transforms.value)

        composition?.dispose()
        host.textContent = ""
        composition = renderComposable(host) {
            ControlPane(params, popup = Popup.ModifyTransform(0), togglePopup = {})
        }
        dropdownItem("Rectify vertex").click()

        assertEquals(listOf(RectifyVertex(vertexB)), params.transforms.value)
    }

    @Test
    fun facePopupActionsFollowTransformMenuOrderAndIncludeTooltips() {
        val params = PolyParams("", null)
        params.updateAvailableOrbitTransforms(
            listOf(
                listOf(
                    KisFace(faceAlpha),
                    Drop(faceBeta),
                    Drop(faceAlpha),
                    TruncateVertex(vertexA),
                    RectifyVertex(vertexA),
                ).map(Transform::tag),
            ),
        )
        renderOrbitTargetActions(params, faceAlpha)

        val actions = orbitTargetActionButtons()
        assertEquals(listOf("Drop face orbit $faceAlpha", "Kis face orbit $faceAlpha"), actions.map(::ariaLabel))
        assertAction(actions[0], "fa-remove")
        assertAction(actions[1], "fa-caret-up")

        actions[1].click()
        assertEquals(listOf(KisFace(faceAlpha)), params.transforms.value)
    }

    @Test
    fun vertexPopupActionsFollowTransformMenuOrderAndIncludeTooltips() {
        val params = PolyParams("", null)
        params.updateAvailableOrbitTransforms(
            listOf(
                listOf(
                    TruncateVertex(vertexA),
                    RectifyVertex(vertexA),
                    Drop(vertexB),
                    Drop(vertexA),
                    KisFace(faceAlpha),
                ).map(Transform::tag),
            ),
        )
        renderOrbitTargetActions(params, vertexA)

        val actions = orbitTargetActionButtons()
        assertEquals(
            listOf(
                "Drop vertex orbit $vertexA",
                "Truncate vertex orbit $vertexA",
                "Rectify vertex orbit $vertexA",
            ),
            actions.map(::ariaLabel),
        )
        assertAction(actions[0], "fa-remove")
        assertAction(actions[1], "fa-scissors")
        assertAction(actions[2], "fa-compress")

        actions[0].click()
        assertEquals(listOf(Drop(vertexA)), params.transforms.value)
    }

    private fun dropdownOptionsBySection(): Map<String, List<String>> {
        val rows = host.querySelectorAll(".dropdown .text-row")
        val result = linkedMapOf<String, MutableList<String>>()
        var section = ""
        for (index in 0 until rows.length) {
            val row = rows.item(index) as HTMLElement
            val header = row.querySelector(".header")?.textContent?.trim()
            if (header != null) {
                section = header
                result.getOrPut(section, ::mutableListOf)
            } else {
                row.querySelector(".item")?.textContent?.trim()?.let {
                    result.getOrPut(section, ::mutableListOf) += it
                }
            }
        }
        return result
    }

    private fun renderOrbitTargetActions(params: PolyParams, kind: AnyKind) {
        composition = renderComposable(host) {
            Table { Tbody { Tr { OrbitTargetActions(params, kind) } } }
        }
    }

    private fun orbitTargetActionButtons(): List<HTMLElement> {
        val actions = host.querySelectorAll("button.orbit-target-action")
        return (0 until actions.length).map { actions.item(it) as HTMLElement }
    }

    private fun vertexOperationTags(): List<List<String>> = listOf(
        listOf(
            TruncateVertex(vertexA),
            TruncateVertex(vertexB),
            RectifyVertex(vertexA),
            RectifyVertex(vertexB),
        ).map(Transform::tag),
    )

    private fun horizontalTransformButton(index: Int): HTMLButtonElement =
        generateSequence(transformNameElement().parentElement?.firstElementChild) { it.nextElementSibling }
            .filterIsInstance<HTMLButtonElement>()
            .elementAt(index)

    private fun ariaLabel(action: HTMLElement): String = action.getAttribute("aria-label").orEmpty()

    private fun assertAction(action: HTMLElement, iconClass: String) {
        val icon = action.querySelector("i") as HTMLElement
        assertTrue(
            icon.classList.contains(iconClass),
            "Expected icon $iconClass, but action rendered ${action.outerHTML}",
        )
        assertEquals(ariaLabel(action), action.querySelector(".tooltip-text")?.textContent)
    }

    private fun dropdownItem(text: String): HTMLElement {
        val items = host.querySelectorAll(".dropdown .item")
        return (0 until items.length)
            .map { items.item(it) as HTMLElement }
            .single { it.textContent?.trim() == text }
    }

    private fun transformNameElement(): HTMLElement {
        val buttons = host.querySelectorAll(".ctrl-pane > .btn > button.txt")
        return (0 until buttons.length)
            .map { buttons.item(it) as HTMLElement }
            .single {
                val text = it.textContent.orEmpty()
                text.contains("Drop ") || text.contains("Kis ") ||
                    text.contains("Truncate ") || text.contains("Rectify ")
            }
    }

    private fun transformName(): String = transformNameElement().textContent.orEmpty()
        .substringBefore("Modify transform")
        .trim()

    private fun previousElementBeforeOrbitControls(): HTMLElement? =
        host.querySelector(".drop-orbit-controls")?.previousElementSibling as? HTMLElement

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }
}
