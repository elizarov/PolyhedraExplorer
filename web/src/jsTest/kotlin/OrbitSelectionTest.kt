package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.MouseEvent
import polyhedra.model.poly.*
import polyhedra.web.main.*
import polyhedra.web.params.TransientParam
import kotlin.js.Promise
import kotlin.test.*

class OrbitSelectionTest {
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
    fun faceEdgeAndVertexRowsPropagateRolloverInBothDirections(): Promise<Unit> {
        val face = FaceKind(0)
        val edge = EdgeKind(VertexKind(0), VertexKind(1), FaceKind(0), FaceKind(1))
        val vertex = VertexKind(0)
        val selectedFace = TransientParam<FaceKind?>(null)
        val selectedEdge = TransientParam<EdgeKind?>(null)
        val selectedVertex = TransientParam<VertexKind?>(null)
        composition = renderComposable(host) {
            Table {
                Tbody {
                    OrbitInfoRow(face, selectedFace) { Td { Text("face") } }
                    OrbitInfoRow(edge, selectedEdge) { Td { Text("edge") } }
                    OrbitInfoRow(vertex, selectedVertex) { Td { Text("vertex") } }
                }
            }
        }
        var rows = rows()

        rows[0].dispatchEvent(MouseEvent("mouseover"))
        assertEquals(face, selectedFace.value)
        rows[0].dispatchEvent(MouseEvent("mouseout"))
        assertNull(selectedFace.value)

        rows[1].dispatchEvent(MouseEvent("mouseover"))
        assertEquals(edge, selectedEdge.value)
        rows[1].dispatchEvent(MouseEvent("mouseout"))
        assertNull(selectedEdge.value)

        rows[2].dispatchEvent(MouseEvent("mouseover"))
        assertEquals(vertex, selectedVertex.value)
        rows[2].dispatchEvent(MouseEvent("mouseout"))
        assertNull(selectedVertex.value)

        selectedFace.updateValue(face)
        selectedEdge.updateValue(edge)
        selectedVertex.updateValue(vertex)
        return awaitRecomposition().then {
            rows = rows()
            rows.forEachIndexed { index, row ->
                assertTrue(
                    row.classList.contains("selected"),
                    "Row $index must follow external selection state",
                )
                assertEquals("true", row.getAttribute("aria-selected"))
            }
        }
    }

    private fun rows(): List<HTMLElement> =
        List(3) { host.querySelectorAll("tr").item(it) as HTMLElement }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }
}
