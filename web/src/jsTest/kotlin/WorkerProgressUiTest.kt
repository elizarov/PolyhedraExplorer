package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.promise
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import polyhedra.model.api.CoreProgress
import polyhedra.model.api.CoreState
import polyhedra.web.catalog.Transform
import polyhedra.web.main.ControlPane
import polyhedra.web.main.RootPane
import polyhedra.web.main.RootParams
import polyhedra.web.poly.PolyParams
import polyhedra.web.poly.WORKER_PROGRESS_GRACE_MS
import polyhedra.web.worker.CoreWorkerException
import kotlin.js.Promise
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerProgressUiTest {
    private lateinit var host: HTMLDivElement
    private lateinit var scope: CoroutineScope
    private var composition: Composition? = null

    @BeforeTest
    fun setUp() {
        scope = MainScope()
        host = document.createElement("div") as HTMLDivElement
        document.body!!.appendChild(host)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        composition?.dispose()
        composition = null
        host.parentNode?.removeChild(host)
    }

    @Test
    fun progressMovesToTheTransformStageReportedByTheWorker(): Promise<Unit> = scope.promise {
        val params = PolyParams("", null)
        params.transforms.updateValue(
            listOf(Transform.Cantellated, Transform.Chamfered, Transform.Snub, Transform.Canonical)
        )
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        params.updateTransformProgress(CoreProgress(transformIndex = 0, done = 5))
        awaitRecomposition()
        assertEquals(0, host.querySelectorAll("button.msg").length)
        params.performUpdate(source = null, dt = 0.0)
        awaitProgressGrace()
        assertEquals(0, params.transformError?.index)
        assertProgressPill(transformName = "Cantellated", progress = "5%")

        params.performUpdate(source = null, dt = 0.0)
        params.updateTransformProgress(CoreProgress(transformIndex = 3, done = 42))
        awaitRecomposition()
        assertEquals(0, host.querySelectorAll("button.msg").length)
        params.performUpdate(source = null, dt = 0.0)
        awaitProgressGrace()
        assertEquals(3, params.transformError?.index)
        assertProgressPill(transformName = "Canonical", progress = "42%")
    }

    @Test
    fun loadingStatusEndsBeforeTransformProgressBegins(): Promise<Unit> = scope.promise {
        val params = RootParams()
        params.render.poly.transforms.updateValue(listOf(Transform.Canonical))
        composition = renderComposable(host) { RootPane(params) }

        assertEquals("Loading Wasm core…", host.querySelector(".core-status")?.textContent)
        assertEquals(0, host.querySelectorAll("button.msg").length)

        params.render.poly.updateTransformProgress(CoreProgress(transformIndex = 0, done = 42))
        awaitRecomposition()
        assertEquals(null, host.querySelector(".core-status"))
        assertEquals(0, host.querySelectorAll("button.msg").length)
        params.performUpdate(source = null, dt = 0.0)
        awaitProgressGrace()
        assertEquals(0, params.render.poly.transformError?.index)
        assertProgressPill(transformName = "Canonical", progress = "42%")
    }

    @Test
    fun operationCompletedWithinGracePeriodNeverShowsProgress(): Promise<Unit> = scope.promise {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Canonical))
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        params.updateTransformProgress(CoreProgress(transformIndex = 0, done = 42))
        awaitRecomposition()
        assertEquals(0, host.querySelectorAll("button.msg").length)
        params.performUpdate(source = null, dt = 0.0)
        params.updateTransformProgress(CoreProgress(transformIndex = 0, done = 100))
        awaitProgressGrace()
        assertEquals(0, host.querySelectorAll("button.msg").length)
    }

    @Test
    fun completedStageRemovesProgressFromTheNewLastPill(): Promise<Unit> = scope.promise {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Cantellated, Transform.Chamfered))
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }

        params.updateTransformProgress(CoreProgress(transformIndex = 1, done = 99))
        params.performUpdate(source = null, dt = 0.0)
        awaitProgressGrace()
        assertEquals(1, params.transformError?.index)
        assertProgressPill(transformName = "Chamfered", progress = "99%")

        params.performUpdate(source = null, dt = 0.0)
        params.updateTransformProgress(CoreProgress(transformIndex = 1, done = 100))
        awaitRecomposition()
        assertEquals(0, host.querySelectorAll("button.msg").length)
    }

    @Test
    fun workerFailureIsAssignedToTheStageReportedByTheWorker() {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Greatened, Transform.Dual))

        params.handleCoreFailure(
            CoreState("deD", listOf("G", "d"), "c"),
            CoreWorkerException(1, "animation failed"),
        )

        assertEquals(1, params.transformError?.index)
        assertEquals(Transform.Dual, params.transformError?.msg?.value)
        assertEquals("animation failed", params.coreError)
    }

    private fun assertProgressPill(transformName: String, progress: String) {
        val progressButton = host.querySelector("button.msg") as HTMLElement
        assertEquals(progress, progressButton.querySelector("span:last-of-type")?.textContent)
        assertTrue(progressButton.parentElement?.textContent.orEmpty().contains(transformName))
        assertEquals(1, host.querySelectorAll("button.msg").length)
    }

    private suspend fun awaitRecomposition() {
        awaitAnimationFrame()
        awaitAnimationFrame()
    }

    private suspend fun awaitProgressGrace() {
        delay(WORKER_PROGRESS_GRACE_MS.toLong() + 50)
        awaitRecomposition()
    }

    private suspend fun awaitAnimationFrame() = suspendCoroutine { continuation ->
        window.requestAnimationFrame { continuation.resume(Unit) }
    }
}
