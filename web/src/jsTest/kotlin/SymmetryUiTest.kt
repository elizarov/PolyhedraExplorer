/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web

import androidx.compose.runtime.Composition
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import polyhedra.model.api.CoreSymmetry
import polyhedra.model.api.PointGroup
import polyhedra.model.api.PointGroupFamily
import polyhedra.model.api.PointGroupSuffix
import polyhedra.model.poly.FEV
import polyhedra.model.util.MutableVec3
import polyhedra.model.util.norm
import polyhedra.model.util.times
import polyhedra.model.util.unit
import polyhedra.web.main.SymmetryControl
import polyhedra.web.main.ConfigPopup
import polyhedra.web.main.RootParams
import polyhedra.web.main.elementCount
import polyhedra.web.params.loadFromString
import polyhedra.web.params.BooleanParam
import polyhedra.web.poly.symmetryAxisLines
import polyhedra.web.poly.symmetryPlaneTriangles
import kotlin.math.abs
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SymmetryUiTest {
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
    fun elementCountsOnlyAppendMultipleOrbitCounts() {
        assertEquals("38/3", elementCount(38, 3))
        assertEquals("24", elementCount(24, 1))
    }

    @Test
    fun symmetryPlaneMeshIsACircumradiusLimitedCircle() {
        val normal = MutableVec3(1.0, 2.0, 3.0).unit
        val radius = 2.5
        val triangles = symmetryPlaneTriangles(listOf(normal), radius, segments = 16)

        assertEquals(16 * 3, triangles.size)
        for (index in triangles.indices step 3) {
            assertEquals(0.0, triangles[index].norm, tolerance)
            for (point in triangles.subList(index + 1, index + 3)) {
                assertTrue(abs(point * normal) < tolerance, "$point must lie in the mirror plane")
                assertEquals(radius, point.norm, tolerance)
            }
        }
    }

    @Test
    fun symmetryAxisMeshUsesItsCircumradiusMultiplier() {
        val direction = MutableVec3(1.0, 2.0, 3.0).unit
        val lines = symmetryAxisLines(listOf(direction), radius = 2.5, size = 1.2)

        assertEquals(2, lines.size)
        assertEquals(3.0, lines[0].norm, tolerance)
        assertEquals(3.0, lines[1].norm, tolerance)
        assertTrue((lines[0] * lines[1]) < 0.0, "Axis endpoints must lie on opposite rays")
    }

    @Test
    fun symmetryPillTogglesAxesAndReflectionPlanesAndUpdatesItsTooltip(): Promise<Unit> {
        val showSymmetry = BooleanParam("sym", false)
        val symmetry = CoreSymmetry(
            pointGroup = PointGroup(
                PointGroupFamily.Octahedral,
                suffix = PointGroupSuffix.Horizontal,
            ),
            orbitCounts = FEV(3, 3, 1),
            reflectionPlaneNormals = List(9) { MutableVec3(1.0, 0.0, 0.0) },
            rotationAxisDirections = List(13) { MutableVec3(0.0, 1.0, 0.0) },
        )
        composition = renderComposable(host) { SymmetryControl(symmetry, showSymmetry) }
        var button = symmetryButton()

        assertEquals("O", button.firstChild?.textContent)
        assertEquals("h", button.querySelector("sub")?.textContent)
        assertEquals(
            "Full octahedral point group (O_h); show 13 rotation axes and 9 reflection planes",
            button.getAttribute("aria-label"),
        )
        assertFalse(button.disabled)
        button.click()
        assertTrue(showSymmetry.value)

        return awaitRecomposition().then {
            button = symmetryButton()
            assertTrue(button.parentElement!!.classList.contains("active"))
            assertEquals(
                "Full octahedral point group (O_h); hide 13 rotation axes and 9 reflection planes",
                button.getAttribute("aria-label"),
            )
        }
    }

    @Test
    fun chiralSymmetryPillStillOffersRotationAxes() {
        val symmetry = CoreSymmetry(
            pointGroup = PointGroup(PointGroupFamily.Icosahedral),
            orbitCounts = FEV(3, 3, 1),
            reflectionPlaneNormals = emptyList(),
            rotationAxisDirections = List(31) { MutableVec3(0.0, 0.0, 1.0) },
        )
        composition = renderComposable(host) { SymmetryControl(symmetry, BooleanParam("sym", false)) }

        val button = symmetryButton()
        assertFalse(button.disabled)
        assertEquals(null, button.querySelector("sub"))
        assertEquals(
            "Chiral icosahedral point group (I); show 31 rotation axes and no reflection planes",
            button.getAttribute("aria-label"),
        )
    }

    @Test
    fun axialPointGroupRendersFoldAndSuffixAsHtmlSubscript() {
        val symmetry = CoreSymmetry(
            pointGroup = PointGroup(
                PointGroupFamily.Dihedral,
                fold = 7,
                suffix = PointGroupSuffix.Horizontal,
            ),
            orbitCounts = FEV(2, 2, 1),
            reflectionPlaneNormals = List(8) { MutableVec3(1.0, 0.0, 0.0) },
            rotationAxisDirections = List(8) { MutableVec3(0.0, 1.0, 0.0) },
        )
        composition = renderComposable(host) { SymmetryControl(symmetry, BooleanParam("sym", false)) }

        val button = symmetryButton()
        assertEquals("D", button.firstChild?.textContent)
        assertEquals("7h", button.querySelector("sub")?.textContent)
        assertEquals(
            "7-fold prismatic point group (D_7h); show 8 rotation axes and 8 reflection planes",
            button.getAttribute("aria-label"),
        )
    }

    @Test
    fun symmetrySizesHaveConfiguredDefaultsRangesAndSerialization() {
        val source = RootParams()
        val planeSize = source.render.view.symmetryPlaneSize
        val axisSize = source.render.view.symmetryAxisSize

        assertEquals(1.1, planeSize.targetValue)
        assertEquals(1.0, planeSize.min)
        assertEquals(2.0, planeSize.max)
        assertEquals(1.2, axisSize.targetValue)
        assertEquals(1.0, axisSize.min)
        assertEquals(2.0, axisSize.max)
        assertFalse(source.toString().contains("ps("))
        assertFalse(source.toString().contains("as("))

        planeSize.updateValue(1.4)
        axisSize.updateValue(1.7)
        val serialized = source.toString()
        assertTrue(serialized.contains("ps(1.4)"), serialized)
        assertTrue(serialized.contains("as(1.7)"), serialized)

        val restored = RootParams()
        restored.loadFromString(serialized)
        assertEquals(1.4, restored.render.view.symmetryPlaneSize.targetValue)
        assertEquals(1.7, restored.render.view.symmetryAxisSize.targetValue)
    }

    @Test
    fun symmetryOverlayVisibilityRoundTripsThroughUrlState() {
        val source = RootParams()
        assertFalse(source.render.poly.showSymmetry.value)
        assertFalse(source.toString().contains("sym("))

        source.render.poly.showSymmetry.updateValue(true)
        val serialized = source.toString()
        assertTrue(serialized.contains("sym(y)"), serialized)

        val restored = RootParams()
        restored.loadFromString(serialized)
        assertTrue(restored.render.poly.showSymmetry.value)
    }

    @Test
    fun configPopupContainsSymmetrySizeControls() {
        composition = renderComposable(host) { ConfigPopup(RootParams()) }

        val text = host.textContent.orEmpty()
        assertTrue(text.contains("Symmetry"), text)
        assertTrue(text.contains("Plane size"), text)
        assertTrue(text.contains("Axis size"), text)
    }

    private fun symmetryButton() = host.querySelector(".symmetry > button") as HTMLButtonElement

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }

    private companion object {
        const val tolerance = 1e-8
    }
}
