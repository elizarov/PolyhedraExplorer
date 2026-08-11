/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import org.jetbrains.compose.web.renderComposable
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.WebGLRenderingContext
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement
import polyhedra.core.poly.Seed as CoreSeed
import polyhedra.core.poly.Tetrahedron
import polyhedra.model.util.Vec3
import polyhedra.web.main.ConfigPopup
import polyhedra.web.main.RootParams
import polyhedra.web.glsl.set
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.EnvironmentContext
import polyhedra.web.poly.FaceContext
import polyhedra.web.poly.LightingContext
import polyhedra.web.poly.SceneEnvironment
import polyhedra.web.poly.TableProgram
import polyhedra.web.poly.TableShadowProgram
import polyhedra.web.poly.ViewContext
import polyhedra.web.poly.environmentTableHeight
import polyhedra.web.poly.projectPointToTable
import polyhedra.web.poly.draw
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

    @Test
    fun hidingFacesDoesNotEraseTableOnTheOnlyRedraw() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 160
        canvas.height = 120
        val gl = assertNotNull(
            canvas.getContext("webgl", js("({ premultipliedAlpha: false, stencil: true })"))
                as? WebGLRenderingContext,
        )
        val params = RootParams()
        val poly = CoreSeed.Tetrahedron.poly
        val view = ViewContext(params.render.view)
        val lighting = LightingContext(params.render.lighting)
        val faces = FaceContext(gl, params.render) { poly }
        val environment = EnvironmentContext(gl, params.render)

        gl.blendFunc(WebGLRenderingContext.SRC_ALPHA, WebGLRenderingContext.ONE_MINUS_SRC_ALPHA)
        gl.depthFunc(WebGLRenderingContext.LEQUAL)
        gl.clearColor(0.0f, 0.0f, 0.0f, 0.0f)
        gl.clearDepth(1.0f)
        gl.clearStencil(0)
        gl.getExtension("OES_element_index_uint")
        gl[WebGLRenderingContext.CULL_FACE] = true
        gl.cullFace(WebGLRenderingContext.BACK)

        params.render.view.environment.updateValue(SceneEnvironment.Table)
        params.render.performUpdate(null, 0.0)
        drawEnvironmentAndFaces(gl, canvas, view, lighting, faces, environment)
        assertTrue(pixelAlpha(gl, 10, 20) > 0, "The initial table must render")

        params.render.poly.hideFaces.updateValue(poly.faceKinds.keys)
        params.render.performUpdate(null, 0.0)
        beginFrame(gl, canvas, view)
        environment.draw(view, lighting, faces)
        assertTrue(pixelAlpha(gl, 10, 20) > 0, "The table draw itself must remain visible")
        gl[WebGLRenderingContext.DEPTH_TEST] = true
        gl[WebGLRenderingContext.BLEND] = false
        faces.draw(view, lighting)

        assertTrue(
            pixelAlpha(gl, 10, 20) > 0,
            "Hiding faces must leave the table in the one frame triggered by the update",
        )
    }

    private fun drawEnvironmentAndFaces(
        gl: WebGLRenderingContext,
        canvas: HTMLCanvasElement,
        view: ViewContext,
        lighting: LightingContext,
        faces: FaceContext,
        environment: EnvironmentContext,
    ) {
        beginFrame(gl, canvas, view)
        environment.draw(view, lighting, faces)
        gl[WebGLRenderingContext.DEPTH_TEST] = true
        gl[WebGLRenderingContext.BLEND] = false
        faces.draw(view, lighting)
    }

    private fun beginFrame(
        gl: WebGLRenderingContext,
        canvas: HTMLCanvasElement,
        view: ViewContext,
    ) {
        view.initProjection(canvas.width, canvas.height)
        gl.viewport(0, 0, canvas.width, canvas.height)
        gl.clear(
            WebGLRenderingContext.COLOR_BUFFER_BIT or
                WebGLRenderingContext.DEPTH_BUFFER_BIT or
                WebGLRenderingContext.STENCIL_BUFFER_BIT,
        )
    }

    private fun pixelAlpha(gl: WebGLRenderingContext, x: Int, y: Int): Int {
        val pixels = Uint8Array(4)
        gl.readPixels(
            x,
            y,
            1,
            1,
            WebGLRenderingContext.RGBA,
            WebGLRenderingContext.UNSIGNED_BYTE,
            pixels,
        )
        return js("pixels => pixels[3]").unsafeCast<(Uint8Array) -> Int>()(pixels)
    }

}
