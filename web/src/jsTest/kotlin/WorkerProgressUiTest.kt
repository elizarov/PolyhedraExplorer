package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import polyhedra.model.api.CoreProgress
import polyhedra.web.catalog.Transform
import polyhedra.web.main.ControlPane
import polyhedra.web.main.RootPane
import polyhedra.web.main.RootParams
import polyhedra.web.poly.PolyParams
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerProgressUiTest {
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
    fun progressMovesToTheTransformStageReportedByTheWorker(): Promise<Unit> {
        val params = PolyParams("", null)
        params.transforms.updateValue(
            listOf(Transform.Cantellated, Transform.Chamfered, Transform.Snub, Transform.Canonical)
        )
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        params.updateTransformProgress(CoreProgress(transformIndex = 0, done = 5))
        return awaitRecomposition().then {
            assertProgressPill(transformName = "Cantellated", progress = "5%")

            params.performUpdate(source = null, dt = 0.0)
            params.updateTransformProgress(CoreProgress(transformIndex = 3, done = 42))
            awaitRecomposition()
        }.then {
            assertProgressPill(transformName = "Canonical", progress = "42%")
        }
    }

    @Test
    fun loadingStatusEndsBeforeTransformProgressBegins(): Promise<Unit> {
        val params = RootParams()
        params.render.poly.transforms.updateValue(listOf(Transform.Canonical))
        composition = renderComposable(host) { RootPane(params) }

        assertEquals("Loading Wasm core…", host.querySelector(".core-status")?.textContent)
        assertEquals(0, host.querySelectorAll("button.msg").length)

        params.render.poly.updateTransformProgress(CoreProgress(transformIndex = 0, done = 42))
        return awaitRecomposition().then {
            assertEquals(null, host.querySelector(".core-status"))
            assertProgressPill(transformName = "Canonical", progress = "42%")
        }
    }

    private fun assertProgressPill(transformName: String, progress: String) {
        val progressButton = host.querySelector("button.msg") as HTMLElement
        assertEquals(progress, progressButton.querySelector("span:last-of-type")?.textContent)
        assertTrue(progressButton.parentElement?.textContent.orEmpty().contains(transformName))
        assertEquals(1, host.querySelectorAll("button.msg").length)
    }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }
}
