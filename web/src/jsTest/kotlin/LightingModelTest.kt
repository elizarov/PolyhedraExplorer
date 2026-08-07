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
import polyhedra.web.main.ConfigPopup
import polyhedra.web.main.RootParams
import polyhedra.web.params.Param
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.dielectricF0
import polyhedra.web.poly.FaceProgram
import polyhedra.web.poly.schlickFresnel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LightingModelTest {
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
    fun plaDefaultsUseMeasuredIorAndPlausibleFresnel() {
        val lighting = RootParams().render.lighting

        assertEquals(2.5, lighting.keyLight.targetValue)
        assertEquals(0.22, lighting.fillLight.targetValue)
        assertEquals(0.45, lighting.roughness.targetValue)
        assertEquals(1.46, lighting.ior.targetValue)
        assertEquals(0.03496, dielectricF0(lighting.ior.targetValue), absoluteTolerance = 1e-5)
        assertEquals(dielectricF0(1.46), schlickFresnel(1.0, 1.46), absoluteTolerance = 1e-12)
        assertEquals(1.0, schlickFresnel(0.0, 1.46), absoluteTolerance = 1e-12)
        assertTrue(schlickFresnel(0.5, 1.46) in dielectricF0(1.46)..1.0)
    }

    @Test
    fun onlyEssentialPlasticAndIlluminationControlsAreExposed() {
        composition = renderComposable(host) { ConfigPopup(RootParams()) }
        val text = host.textContent.orEmpty()

        for (label in listOf("Lighting", "Key light", "Fill light", "Material", "Roughness", "IOR")) {
            assertTrue(label in text, "Missing config control: $label")
        }
        for (obsolete in listOf("Ambient", "Diffuse", "Specular", "Shininess")) {
            assertFalse(obsolete in text, "Obsolete non-physical control remains: $obsolete")
        }
    }

    @Test
    fun nonDefaultLightingAndMaterialSettingsRoundTripThroughUrl() {
        val source = RootParams()
        source.render.lighting.keyLight.updateValue(3.1, Param.TargetValue)
        source.render.lighting.fillLight.updateValue(0.3, Param.TargetValue)
        source.render.lighting.roughness.updateValue(0.7, Param.TargetValue)
        source.render.lighting.ior.updateValue(1.52, Param.TargetValue)
        val serialized = source.toString()
        assertTrue("d(3.1)" in serialized, serialized)
        assertTrue("a(0.3)" in serialized, serialized)
        assertTrue("r(0.7)" in serialized, serialized)
        assertTrue("i(1.52)" in serialized, serialized)

        val restored = RootParams()
        restored.loadFromString(serialized)
        assertEquals(3.1, restored.render.lighting.keyLight.targetValue)
        assertEquals(0.3, restored.render.lighting.fillLight.targetValue)
        assertEquals(0.7, restored.render.lighting.roughness.targetValue)
        assertEquals(1.52, restored.render.lighting.ior.targetValue)
    }

    @Test
    fun legacyLightingUrlKeepsCompatibleControlsAndIgnoresObsoleteOnes() {
        val restored = RootParams()
        restored.loadFromString("l(d(1.7)a(0.4)s(0.9)sp(150))")

        assertEquals(1.7, restored.render.lighting.keyLight.targetValue)
        assertEquals(0.4, restored.render.lighting.fillLight.targetValue)
        assertEquals(0.45, restored.render.lighting.roughness.targetValue)
        assertEquals(1.46, restored.render.lighting.ior.targetValue)
    }

    @Test
    fun facePbrShaderCompilesInWebGl() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = assertNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)

        assertNotNull(FaceProgram(gl).program)
    }
}
