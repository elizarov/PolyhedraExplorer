import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import polyhedra.common.poly.EdgeKind
import polyhedra.common.poly.FaceKind
import polyhedra.common.poly.VertexKind
import polyhedra.js.catalog.Drop
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
    fun addPopupGroupsConcreteDropsByOrbitKindAndAddsFirstTarget(): Promise<Unit> {
        val params = PolyParams("", null)
        params.updateAvailableDrops(listOf(listOf(vertexB, faceAlpha, edge, vertexA).map(Any::toString)))
        composition = renderComposable(host) {
            ControlPane(params, popup = Popup.AddTransform, togglePopup = {})
        }

        assertEquals(
            linkedMapOf(
                "Transform" to listOf("Truncated", "Rectified", "Dual", "Snub", "Chamfered", "Canonical"),
                "Macro" to listOf("Kis", "Join", "Needle", "Zip", "Cantellated", "Bevelled", "Ortho", "Meta", "Gyro"),
                "Orbit-targeted" to listOf("Drop edge", "Drop vertex", "Drop face"),
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
        params.updateAvailableDrops(listOf(listOf(faceAlpha, vertexA, edge, vertexB).map(Any::toString)))
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
        params.updateAvailableDrops(
            listOf(
                listOf(vertexA, vertexB).map(Any::toString),
                emptyList(),
            ),
        )
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        assertNull(host.querySelector(".drop-orbit-controls"))
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
            .single { it.textContent.orEmpty().contains("Drop ") }
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
