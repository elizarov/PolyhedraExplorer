package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import polyhedra.web.main.*
import polyhedra.web.poly.createSavePreview
import kotlin.js.Promise
import kotlin.test.*

class SavedConfigurationsTest {
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
    fun savesAreIndependentVersionedRecordsSortedNewestFirst() {
        val storage = FakeSavedConfigurationStorage()
        var now = 1_000L
        val store = SavedConfigurationStore(storage) { now }

        val first = store.save("Cube", "s(C)v(r(1,2,3))", PREVIEW_RED)
        now = 2_000L
        val second = store.save("Snub cube", "s(C)t(s)", PREVIEW_BLUE)

        assertEquals(2, storage.length)
        assertNotEquals(first.id, second.id)
        assertEquals(listOf("Snub cube", "Cube"), store.load().map { it.name })
        assertEquals("s(C)t(s)", store.load().first().urlState)
        assertTrue(storage.values.all { "\"formatVersion\":1" in it })
        assertTrue(storage.values.any { "\"urlState\":\"s(C)v(r(1,2,3))\"" in it })
    }

    @Test
    fun saveNeverOverwritesAnEntryWhenTimestampsCollide() {
        val storage = FakeSavedConfigurationStorage()
        val store = SavedConfigurationStore(storage) { 42L }

        store.save("First", "s(C)", PREVIEW_RED)
        store.save("Second", "s(O)", PREVIEW_BLUE)

        assertEquals(2, storage.length)
        assertEquals(setOf("42", "42-1"), store.load().mapTo(mutableSetOf()) { it.id })
    }

    @Test
    fun malformedAndUnrelatedStorageEntriesDoNotHideValidSaves() {
        val storage = FakeSavedConfigurationStorage()
        val store = SavedConfigurationStore(storage) { 100L }
        store.save("Valid", "s(T)", PREVIEW_RED)
        storage.setItem("unrelated.preference", "anything")
        storage.setItem("${SAVED_CONFIGURATION_KEY_PREFIX}broken", "{not json")

        assertEquals(listOf("Valid"), store.load().map { it.name })
    }

    @Test
    fun relativeTimestampsRemainConciseAndStable() {
        val savedAt = 10_000L
        assertEquals("just now", relativeSavedTime(savedAt, savedAt + 9_999))
        assertEquals("45 sec ago", relativeSavedTime(savedAt, savedAt + 45_000))
        assertEquals("1 min ago", relativeSavedTime(savedAt, savedAt + 90_000))
        assertEquals("12 min ago", relativeSavedTime(savedAt, savedAt + 12 * 60_000))
        assertEquals("1 hour ago", relativeSavedTime(savedAt, savedAt + 90 * 60_000))
        assertEquals("yesterday", relativeSavedTime(savedAt, savedAt + 30 * 3_600_000))
        assertEquals("9 days ago", relativeSavedTime(savedAt, savedAt + 9 * 86_400_000L))
        assertEquals("2 years ago", relativeSavedTime(savedAt, savedAt + 2 * 365 * 86_400_000L))
    }

    @Test
    fun canvasPreviewIsStoredAsACompactImageDataUrl() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 640
        canvas.height = 360
        val context = canvas.getContext("2d")!!
        context.asDynamic().fillStyle = "#e53935"
        context.asDynamic().fillRect(0, 0, canvas.width, canvas.height)

        val preview = createSavePreview(canvas)

        assertTrue(preview.startsWith("data:image/"))
        assertTrue(preview.length < 20_000, "Solid-color thumbnail should remain compact: ${preview.length}")
    }

    @Test
    fun oneClickSaveUsesGeneratedNameAndStoredRowLoadsExactUrlState(): Promise<Unit> {
        val storage = FakeSavedConfigurationStorage()
        val store = SavedConfigurationStore(storage) { 1_000L }
        var captureCount = 0
        var loadedState: String? = null
        composition = renderComposable(host) {
            SaveLoadPopup(
                autoName = "Truncated icosahedron",
                serializeState = { "s(I)t(t)v(r(1,2,3))" },
                store = store,
                capturePreview = { onCaptured ->
                    captureCount++
                    onCaptured(PREVIEW_BLUE)
                },
                onLoad = { loadedState = it },
            )
        }

        assertEquals("Truncated icosahedron", saveNameInput().value)
        assertTrue(host.textContent.orEmpty().contains("No saves yet"))
        (host.querySelector("button.save-current-button") as HTMLButtonElement).click()
        assertEquals(1, captureCount)
        assertEquals(1, storage.length)

        return awaitRecomposition().then {
            assertEquals("Truncated icosahedron", savedNames().single().textContent)
            val preview = host.querySelector("img.saved-preview") as HTMLImageElement
            assertEquals(PREVIEW_BLUE, preview.src)
            (host.querySelector("button.saved-configuration") as HTMLButtonElement).click()
            assertEquals("s(I)t(t)v(r(1,2,3))", loadedState)
        }
    }

    @Test
    fun customNameCanBeTypedAndNewerSavesAppearFirst(): Promise<Unit> {
        val storage = FakeSavedConfigurationStorage()
        var now = 1_000L
        val store = SavedConfigurationStore(storage) { now++ }
        composition = renderComposable(host) {
            SaveLoadPopup(
                autoName = "Cube",
                serializeState = { "s(C)" },
                store = store,
                capturePreview = { it(PREVIEW_RED) },
                onLoad = {},
            )
        }

        (host.querySelector("button.save-current-button") as HTMLButtonElement).click()
        return awaitRecomposition().then {
            val input = saveNameInput()
            input.value = "Desk sculpture"
            input.dispatchEvent(Event("input"))
            (host.querySelector("button.save-current-button") as HTMLButtonElement).click()
            awaitRecomposition()
        }.then {
            assertEquals(listOf("Desk sculpture", "Cube"), savedNames().map { it.textContent })
        }
    }

    private fun saveNameInput() = host.querySelector("input.save-name") as HTMLInputElement

    private fun savedNames(): List<HTMLElement> {
        val nodes = host.querySelectorAll(".saved-name")
        return List(nodes.length) { nodes.item(it) as HTMLElement }
    }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame { window.requestAnimationFrame { resolve(Unit) } }
    }

    private class FakeSavedConfigurationStorage : SavedConfigurationStorage {
        private val items = LinkedHashMap<String, String>()
        override val length: Int get() = items.size
        override fun key(index: Int): String? = items.keys.elementAtOrNull(index)
        override fun getItem(key: String): String? = items[key]
        override fun setItem(key: String, value: String) {
            items[key] = value
        }
        val values: Collection<String> get() = items.values
    }

    companion object {
        private const val PREVIEW_RED = "data:image/webp;base64,cmVk"
        private const val PREVIEW_BLUE = "data:image/webp;base64,Ymx1ZQ=="
    }
}
