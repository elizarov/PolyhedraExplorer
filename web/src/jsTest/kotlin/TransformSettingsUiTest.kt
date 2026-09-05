package polyhedra.web

import androidx.compose.runtime.Composition
import androidx.compose.runtime.mutableStateOf
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import polyhedra.model.api.CoreTransformTweakRange
import polyhedra.model.api.CoreTransformTweakOption
import polyhedra.model.api.TransformTweak
import polyhedra.web.catalog.Transform
import polyhedra.web.catalog.StellateFace
import polyhedra.web.catalog.withTweak
import polyhedra.web.main.ControlPane
import polyhedra.web.main.Popup
import polyhedra.web.main.RootParams
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.PolyParams
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.FEV

class TransformSettingsUiTest {
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
    fun convexHullCheckboxUpdatesBothWaysAndResetOmitsDefault(): Promise<Unit> {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.ConvexHull))
        composition = renderComposable(host) {
            ControlPane(params, Popup.TransformSettings(0), togglePopup = {})
        }
        fun checkbox() = host.querySelector("input[aria-label='Combine with original']") as HTMLInputElement
        assertNotNull(host.querySelector(".transform-settings-button"))
        assertNull(host.querySelector(".transform-setting-slider"))
        assertTrue(!checkbox().checked)
        checkbox().click()
        assertEquals("H~b=1", params.transforms.value.single().tag)
        return awaitRecomposition().then {
            assertTrue(checkbox().checked)
            val source = RootParams()
            source.render.poly.transforms.updateValue(params.transforms.value)
            val restored = RootParams()
            restored.loadFromString(source.toString())
            assertEquals("H~b=1", restored.render.poly.transforms.value.single().tag)
            (host.querySelector(".transform-settings-reset") as HTMLButtonElement).click()
            awaitRecomposition()
        }.then {
            assertEquals("H", params.transforms.value.single().tag)
            assertTrue(!checkbox().checked)
            params.transforms.updateValue(listOf(Transform.ConvexHull.withTweak(TransformTweak.CombineOriginal, 1.0)))
            awaitRecomposition()
        }.then {
            assertTrue(checkbox().checked)
            checkbox().click()
            assertEquals("H", params.transforms.value.single().tag)
        }
    }

    @Test
    fun gearFollowsNameAndSliderUpdatesTransformInBothDirections(): Promise<Unit> {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Truncated))
        composition = renderComposable(host) {
            ControlPane(params, Popup.TransformSettings(0), togglePopup = {})
        }

        val name = host.querySelector("button.txt") as HTMLElement
        assertTrue(name.nextElementSibling?.classList?.contains("transform-settings-button") == true)
        val slider = host.querySelector(".transform-setting-slider") as HTMLInputElement
        val reset = host.querySelector(".transform-settings-reset") as HTMLButtonElement
        assertEquals("100", slider.value)
        assertTrue(reset.disabled)

        slider.value = "70"
        slider.dispatchEvent(Event("input"))
        assertEquals("t~d=0.7", params.transforms.value.single().tag)

        return awaitRecomposition().then {
            assertEquals("70", (host.querySelector(".transform-setting-slider") as HTMLInputElement).value)
            val valueInput = host.querySelector(".transform-setting-value") as HTMLInputElement
            assertEquals("70", valueInput.value)
            assertEquals("%", host.querySelector(".slider-unit")?.textContent)
            valueInput.value = "43"
            valueInput.dispatchEvent(Event("change"))
            assertEquals("t~d=0.43", params.transforms.value.single().tag)
            awaitRecomposition()
        }.then {
            assertEquals("43", (host.querySelector(".transform-setting-slider") as HTMLInputElement).value)
            val updatedReset = host.querySelector(".transform-settings-reset") as HTMLButtonElement
            assertTrue(!updatedReset.disabled)
            updatedReset.click()
            awaitRecomposition()
        }.then {
            assertEquals("t", params.transforms.value.single().tag)
            assertEquals("100", (host.querySelector(".transform-setting-slider") as HTMLInputElement).value)
        }
    }

    @Test
    fun everyConfigurableTransformHasGearAndChiralityLivesInsideSettings() {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Truncated, Transform.Snub))

        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }
        assertEquals(2, host.querySelectorAll(".transform-settings-button").length)
        assertNull(host.querySelector(".chirality-flip"))

        composition?.dispose()
        composition = renderComposable(host) {
            ControlPane(params, popup = Popup.TransformSettings(0), togglePopup = {})
        }
        assertNotNull(host.querySelector(".transform-settings"))
        assertEquals(2, host.querySelectorAll(".transform-settings-button").length)
        val depth = host.querySelector(".transform-setting-slider") as HTMLInputElement
        depth.value = "70"
        depth.dispatchEvent(Event("input"))
        assertEquals(listOf("t~d=0.7", "s"), params.transforms.value.map { it.tag })

        composition?.dispose()
        composition = renderComposable(host) {
            ControlPane(params, popup = Popup.TransformSettings(1), togglePopup = {})
        }
        assertNotNull(host.querySelector(".chirality-flip"))
        assertEquals(2, host.querySelectorAll(".transform-setting-slider").length)
    }

    @Test
    fun greateningResultStepsKeepDualAndTheSettingsPopup(): Promise<Unit> {
        val params = PolyParams("", null)
        params.seed.updateValue(polyhedra.web.catalog.Seeds.first { it.tag == "I" })
        params.transforms.updateValue(listOf(Transform.Greatened, Transform.Dual))
        params.updateTransformTweakRanges(listOf(
            listOf(CoreTransformTweakRange(TransformTweak.StellationResult, 1.0, 9.0)),
            emptyList(),
        ))
        val popup = mutableStateOf<Popup?>(null)
        composition = renderComposable(host) {
            ControlPane(params, popup.value, togglePopup = { popup.value = it })
        }
        (host.querySelector(".transform-settings-button") as HTMLButtonElement).click()
        return awaitRecomposition().then {
            assertEquals(Popup.TransformSettings(0), popup.value)
            (host.querySelector(".slider-step-next") as HTMLButtonElement).click()
            awaitRecomposition()
        }.then {
            assertEquals(listOf("G~l=2", "d"), params.transforms.value.map { it.tag })
            assertEquals("2", (host.querySelector(".transform-setting-slider") as HTMLInputElement).value)
            assertEquals(Popup.TransformSettings(0), popup.value)
            (host.querySelector(".slider-step-next") as HTMLButtonElement).click()
            awaitRecomposition()
        }.then {
            assertEquals(listOf("G~l=3", "d"), params.transforms.value.map { it.tag })
            assertEquals("3", (host.querySelector(".transform-setting-slider") as HTMLInputElement).value)
            (host.querySelector(".slider-step-previous") as HTMLButtonElement).click()
            awaitRecomposition()
        }.then {
            assertEquals(listOf("G~l=2", "d"), params.transforms.value.map { it.tag })
            (host.querySelector(".transform-settings-reset") as HTMLButtonElement).click()
            awaitRecomposition()
        }.then {
            assertEquals(listOf("G", "d"), params.transforms.value.map { it.tag })
            assertEquals("1", (host.querySelector(".transform-setting-slider") as HTMLInputElement).value)
            assertNotNull(host.querySelector(".transform-settings"))
        }
    }

    @Test
    fun earlierChiralityOnlyTransformCanFlipAndResetWithoutChangingLaterTransforms(): Promise<Unit> {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Propeller, Transform.ConvexHull, Transform.Dual))
        composition = renderComposable(host) {
            ControlPane(params, popup = Popup.TransformSettings(0), togglePopup = {})
        }
        assertEquals(2, host.querySelectorAll(".transform-settings-button").length)
        assertNull(host.querySelector(".transform-setting-slider"))
        (host.querySelector(".chirality-flip") as HTMLButtonElement).click()
        assertEquals(listOf("p'", "H", "d"), params.transforms.value.map { it.tag })
        return awaitRecomposition().then {
            (host.querySelector(".transform-settings-reset") as HTMLButtonElement).click()
            assertEquals(listOf("p", "H", "d"), params.transforms.value.map { it.tag })
        }
    }

    @Test
    fun sliderUsesGeometrySafeRangeReturnedByCore() {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Truncated))
        params.updateTransformTweakRanges(
            listOf(
                listOf(CoreTransformTweakRange(TransformTweak.Depth, min = 0.423, max = 1.137))
            )
        )

        composition = renderComposable(host) {
            ControlPane(params, Popup.TransformSettings(0), togglePopup = {})
        }

        val slider = host.querySelector(".transform-setting-slider") as HTMLInputElement
        assertEquals("43", slider.min)
        assertEquals("113", slider.max)
        slider.value = slider.min
        slider.dispatchEvent(Event("input"))
        assertEquals("t~d=0.43", params.transforms.value.single().tag)
    }

    @Test
    fun nonDefaultTweaksRoundTripWhileDefaultsAreOmitted() {
        val source = RootParams()
        source.render.poly.transforms.updateValue(
            listOf(
                Transform.Truncated.withTweak(TransformTweak.Depth, 0.7),
                Transform.Bevelled.withTweak(TransformTweak.Distance, 0.8),
                Transform.SnubFlipped.withTweak(TransformTweak.Twist, 1.25),
            )
        )
        val encoded = source.toString()
        assertTrue("t~d=0.7" in encoded)
        assertTrue("b~c=0.8" in encoded)
        assertTrue("s'~r=1.25" in encoded)

        val restored = RootParams()
        restored.loadFromString(encoded)

        assertEquals(
            source.render.poly.transforms.value.map { it.tag },
            restored.render.poly.transforms.value.map { it.tag },
        )
        assertEquals("t", Transform.Truncated.withTweak(TransformTweak.Depth, 1.0).tag)
    }

    @Test
    fun stellateFaceStepsPreciselyBetweenCoreSuppliedCoplanarityLandmarks(): Promise<Unit> {
        val landmarks = listOf(0.4305986891668768, 0.8157378651666524, 1.8240453183331915)
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(StellateFace(FaceKind(0))))
        params.updateTransformTweakRanges(
            listOf(
                listOf(
                    CoreTransformTweakRange(
                        TransformTweak.Radius,
                        min = 0.1,
                        max = 2.0,
                        landmarks = landmarks,
                    )
                )
            )
        )
        composition = renderComposable(host) {
            ControlPane(params, Popup.TransformSettings(0), togglePopup = {})
        }

        val previous = host.querySelector(".slider-step-previous") as HTMLButtonElement
        val next = host.querySelector(".slider-step-next") as HTMLButtonElement
        assertEquals("Previous coplanar radius", previous.getAttribute("aria-label"))
        assertEquals("Next coplanar radius", next.getAttribute("aria-label"))
        previous.click()
        assertEquals(
            StellateFace(FaceKind(0)).withTweak(
                TransformTweak.Radius,
                landmarks[1],
            ).tag,
            params.transforms.value.single().tag,
        )
        return awaitRecomposition().then {
            assertEquals("82", (host.querySelector(".transform-setting-slider") as HTMLInputElement).value)
            (host.querySelector(".slider-step-previous") as HTMLButtonElement).click()
            awaitRecomposition()
        }.then {
            assertEquals(
                StellateFace(FaceKind(0)).withTweak(TransformTweak.Radius, landmarks[0]).tag,
                params.transforms.value.single().tag,
            )
            (host.querySelector(".slider-step-next") as HTMLButtonElement).click()
            awaitRecomposition()
        }.then {
            assertEquals(
                StellateFace(FaceKind(0)).withTweak(TransformTweak.Radius, landmarks[1]).tag,
                params.transforms.value.single().tag,
            )
            (host.querySelector(".slider-step-next") as HTMLButtonElement).click()
            awaitRecomposition()
        }.then {
            assertEquals(
                StellateFace(FaceKind(0)).withTweak(TransformTweak.Radius, landmarks[2]).tag,
                params.transforms.value.single().tag,
            )
        }
    }

    @Test
    fun stellationResultGearAppearsOnlyForAlternativesAndUsesDiscreteMetadata(): Promise<Unit> {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Stellated))
        params.updateTransformTweakRanges(
            listOf(
                listOf(
                    CoreTransformTweakRange(
                        TransformTweak.StellationResult,
                        min = 1.0,
                        max = 1.0,
                        options = listOf(CoreTransformTweakOption(1, FEV(12, 30, 12))),
                    ),
                ),
            ),
        )
        composition = renderComposable(host) { ControlPane(params, popup = null, togglePopup = {}) }
        assertNull(host.querySelector(".transform-settings-button"))

        composition?.dispose()
        host.textContent = ""
        params.updateTransformTweakRanges(
            listOf(
                listOf(
                    CoreTransformTweakRange(
                        TransformTweak.StellationResult,
                        min = 1.0,
                        max = 3.0,
                        options = listOf(
                            CoreTransformTweakOption(1, FEV(12, 30, 12)),
                            CoreTransformTweakOption(2, FEV(24, 60, 38)),
                            CoreTransformTweakOption(3, FEV(32, 90, 60)),
                        ),
                    ),
                ),
            ),
        )
        composition = renderComposable(host) {
            ControlPane(params, Popup.TransformSettings(0), togglePopup = {})
        }
        val slider = host.querySelector(".transform-setting-slider") as HTMLInputElement
        assertEquals("1", slider.min)
        assertEquals("3", slider.max)
        assertEquals("1", (host.querySelector(".transform-setting-value") as HTMLInputElement).value)
        assertEquals("of 3 · F 12, E 30, V 12", host.querySelector(".transform-setting-detail")?.textContent)

        assertNull(host.querySelector("button.txt sub"))
        val previous = host.querySelector(".slider-step-previous") as HTMLButtonElement
        val next = host.querySelector(".slider-step-next") as HTMLButtonElement
        assertTrue(previous.parentElement?.parentElement?.classList?.contains("transform-settings-actions") == true)
        assertTrue(previous.parentElement?.nextElementSibling?.classList?.contains("transform-settings-reset") == true)
        assertEquals("Previous stellation", previous.getAttribute("aria-label"))
        assertEquals("Next stellation", next.getAttribute("aria-label"))
        assertTrue(previous.disabled)
        assertTrue(!next.disabled)

        next.click()
        assertEquals("S~l=2", params.transforms.value.single().tag)
        return awaitRecomposition().then {
            assertEquals("2", (host.querySelector(".transform-setting-slider") as HTMLInputElement).value)
            assertEquals("2", host.querySelector("button.txt sub")?.textContent)
            assertTrue(!(host.querySelector(".slider-step-previous") as HTMLButtonElement).disabled)
            (host.querySelector(".slider-step-previous") as HTMLButtonElement).click()
            awaitRecomposition()
        }.then {
            assertEquals("S", params.transforms.value.single().tag)
            assertEquals("1", (host.querySelector(".transform-setting-slider") as HTMLInputElement).value)
            assertNull(host.querySelector("button.txt sub"))
        }
    }

    @Test
    fun laterGreateningUsesSubscriptAndGreateningStepLabels(): Promise<Unit> {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Greatened))
        params.updateTransformTweakRanges(
            listOf(
                listOf(
                    CoreTransformTweakRange(
                        TransformTweak.StellationResult,
                        min = 1.0,
                        max = 3.0,
                        options = (1..3).map { result ->
                            CoreTransformTweakOption(result, FEV(12, result * 30, result * 12))
                        },
                    ),
                ),
            ),
        )
        composition = renderComposable(host) {
            ControlPane(params, Popup.TransformSettings(0), togglePopup = {})
        }

        assertNull(host.querySelector("button.txt sub"))
        assertEquals(
            "Previous greatening",
            host.querySelector(".slider-step-previous")?.getAttribute("aria-label"),
        )
        assertEquals(
            "Next greatening",
            host.querySelector(".slider-step-next")?.getAttribute("aria-label"),
        )
        (host.querySelector(".slider-step-next") as HTMLButtonElement).click()

        return awaitRecomposition().then {
            assertEquals("G~l=2", params.transforms.value.single().tag)
            assertEquals("2", host.querySelector("button.txt sub")?.textContent)
        }
    }

    @Test
    fun stellationGearEnumeratesAllSupportedIcosahedronMainLineResults() {
        val params = PolyParams("", null)
        params.transforms.updateValue(listOf(Transform.Stellated))
        val options = (1..6).map { result ->
            CoreTransformTweakOption(result, FEV(result * 20, result * 30, result * 10 + 2))
        }
        params.updateTransformTweakRanges(
            listOf(
                listOf(
                    CoreTransformTweakRange(
                        TransformTweak.StellationResult,
                        min = 1.0,
                        max = 6.0,
                        options = options,
                    ),
                ),
            ),
        )

        composition = renderComposable(host) {
            ControlPane(params, Popup.TransformSettings(0), togglePopup = {})
        }
        val slider = host.querySelector(".transform-setting-slider") as HTMLInputElement
        assertEquals("1", slider.min)
        assertEquals("6", slider.max)

        slider.value = "6"
        slider.dispatchEvent(Event("input"))
        assertEquals("S~l=6", params.transforms.value.single().tag)
    }

    private fun awaitRecomposition(): Promise<Unit> = Promise { resolve, _ ->
        window.requestAnimationFrame {
            window.requestAnimationFrame { resolve(Unit) }
        }
    }
}
