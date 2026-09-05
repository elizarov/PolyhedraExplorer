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
    fun acrylicModeAndRememberedProfilesRoundTripIncludingDisabledAmount() {
        val source = RootParams()
        assertFalse(source.render.view.transparencyEnabled.value)
        assertEquals(0.85, source.render.view.transparentFaces.targetValue)
        assertEquals(1.49, source.render.lighting.acrylicIor.targetValue)
        assertEquals(0.12, source.render.lighting.acrylicRoughness.targetValue)
        assertFalse("ta(" in source.toString())
        source.render.view.transparentFaces.updateValue(0.72, Param.TargetValue)
        source.render.lighting.acrylicRoughness.updateValue(0.23, Param.TargetValue)
        source.render.lighting.acrylicIor.updateValue(1.52, Param.TargetValue)
        for (enabled in listOf(false, true)) {
            source.render.view.transparencyEnabled.updateValue(enabled)
            val restored = RootParams()
            restored.loadFromString(source.toString())
            assertEquals(enabled, restored.render.view.transparencyEnabled.value)
            assertEquals(0.72, restored.render.view.transparentFaces.targetValue)
            assertEquals(0.23, restored.render.lighting.acrylicRoughness.targetValue)
            assertEquals(1.52, restored.render.lighting.acrylicIor.targetValue)
            assertEquals(0.45, restored.render.lighting.roughness.targetValue)
            assertEquals(1.46, restored.render.lighting.ior.targetValue)
        }
    }

    @Test
    fun legacyTransparencyLoadsAsAcrylicWithoutAmbiguousReserialization() {
        for (amount in listOf(0.0, 0.3, 1.0)) {
            val params = RootParams()
            params.loadFromString("v(t($amount))")
            assertEquals(amount > 0.0, params.render.view.transparencyEnabled.value)
            assertEquals(amount, params.render.view.transparentFaces.targetValue)
            val restored = RootParams()
            restored.loadFromString(params.toString())
            assertEquals(params.render.view.transparencyEnabled.value, restored.render.view.transparencyEnabled.value)
            assertEquals(amount, restored.render.view.transparentFaces.targetValue)
        }
    }

    @Test
    fun facePbrShaderCompilesInWebGl() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val gl = assertNotNull(canvas.getContext("webgl") as? WebGLRenderingContext)

        assertNotNull(FaceProgram(gl).program)
        assertNotNull(FaceProgram(gl, acrylic = true).program)
        assertNotNull(FaceProgram(gl, depthOnly = true).program)
    }
}
