package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLDivElement
import polyhedra.web.main.GitHubCorner
import polyhedra.web.main.PROJECT_GITHUB_URL
import polyhedra.web.main.fpsCaption
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubCornerTest {
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
    fun githubLogoIsAStandaloneAccessibleRepositoryLink() {
        composition = renderComposable(host) { GitHubCorner(null) }

        val corner = assertNotNull(host.querySelector(".github-corner"))
        assertFalse(corner.classList.contains("btn"))
        val link = assertNotNull(corner.querySelector("a.github-link") as? HTMLAnchorElement)
        assertEquals(PROJECT_GITHUB_URL, link.href.removeSuffix("/"))
        assertEquals("_blank", link.target)
        assertTrue(link.relList.contains("noopener"))
        assertTrue(link.relList.contains("noreferrer"))
        assertEquals("Open Polyhedra Explorer on GitHub", link.getAttribute("aria-label"))
        assertNotNull(link.querySelector("i.fa-github"))
        assertEquals("Open Source", corner.querySelector(".github-caption")?.textContent)
    }

    @Test
    fun captionUsesOpenSourceAsTheIdleFallback() {
        assertEquals("Open Source", fpsCaption(null))
        assertEquals("60 fps", fpsCaption(60))
    }
}
