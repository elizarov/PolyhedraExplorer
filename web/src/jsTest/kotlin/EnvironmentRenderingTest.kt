/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import org.jetbrains.compose.web.renderComposable
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement
import polyhedra.model.util.Vec3
import polyhedra.web.main.ConfigPopup
import polyhedra.web.main.RootParams
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.LightingContext
import polyhedra.web.poly.SceneEnvironment
import polyhedra.web.poly.TableProgram
import polyhedra.web.poly.TableShadowProgram
import polyhedra.web.poly.environmentTableHeight
import polyhedra.web.poly.projectPointToTable
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EnvironmentRenderingTest {
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
    fun environmentControlOffersNoneAndTable() {
        composition = renderComposable(host) { ConfigPopup(RootParams()) }

        val rows = host.querySelectorAll("tr")
        val row = List(rows.length) { rows.item(it) as HTMLElement }
            .single { it.textContent.orEmpty().startsWith("Environment") }
        val select = assertNotNull(row.querySelector("select") as? HTMLSelectElement)
        assertEquals(
            listOf("None", "Table"),
            List(select.options.length) { select.options.item(it)!!.textContent },
        )
    }

    @Test
    fun tableEnvironmentRoundTripsAndNoneStaysImplicit() {
        val source = RootParams()
        assertEquals(SceneEnvironment.None, source.render.view.environment.value)
        assertFalse("env(" in source.toString())

        source.render.view.environment.updateValue(SceneEnvironment.Table)
        val serialized = source.toString()
        assertTrue("env(t)" in serialized, serialized)

        val restored = RootParams()
        restored.loadFromString(serialized)
        assertEquals(SceneEnvironment.Table, restored.render.view.environment.value)
    }

    @Test
    fun environmentDoesNotChangePolyhedronLightPosition() {
        val params = RootParams()
        val lighting = LightingContext(params.render.lighting)
        val position = lighting.lightPosition

        params.render.view.environment.updateValue(SceneEnvironment.Table)

        assertSame(position, lighting.lightPosition)
    }

    @Test
    fun tableStaysBelowTheRotationSafeBoundingSphere() {
        assertEquals(-1.18, environmentTableHeight(1.0, 1.0), absoluteTolerance = 1e-12)
        assertEquals(-3.54, environmentTableHeight(1.0, 3.0), absoluteTolerance = 1e-12)
        assertTrue(environmentTableHeight(2.5, 0.5) < -2.5 * 0.5)
    }

    @Test
    fun projectedShadowPointLandsOnTableAlongTheLightRay() {
        val light = Vec3(-2.4, 3.2, 4.3)
        val point = Vec3(0.2, 0.4, -0.1)
        val projected = projectPointToTable(point, light, -1.18)
        val scale = (projected.y - light.y) / (point.y - light.y)

        assertEquals(-1.18, projected.y, absoluteTolerance = 1e-12)
        assertEquals(light.x + (point.x - light.x) * scale, projected.x, absoluteTolerance = 1e-12)
        assertEquals(light.z + (point.z - light.z) * scale, projected.z, absoluteTolerance = 1e-12)
        assertTrue(scale > 1.0, "The receiver is beyond the object from the light")
    }

    @Test
    fun tableAndShadowShadersCompileInWebGl() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = assertNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)

        assertNotNull(TableProgram(gl).program)
        assertNotNull(TableShadowProgram(gl).program)
    }
}
