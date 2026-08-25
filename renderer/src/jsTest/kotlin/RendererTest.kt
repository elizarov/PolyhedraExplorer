package polyhedra.renderer

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RendererTest {
    @Test
    fun fragmentCutExposesBackFacesAndClipsTheFrontInViewSpace() = runTest {
        fun configuration(cut: String = "") = "s(C)v(env(n)d(f)fr(0)$cut)"
        val width = 320
        val height = 240
        val uncut = renderConfiguration(configuration(), width, height)
        val frontLimit = renderConfiguration(configuration("c(y)cp(1)"), width, height)
        val centerCut = renderConfiguration(configuration("c(y)"), width, height)
        val backLimit = renderConfiguration(configuration("c(y)cp(-1)"), width, height)

        assertTrue(
            changedPixels(uncut, frontLimit) < 10,
            "A cut at +1 base radius must leave the normalized cube unchanged",
        )
        assertTrue(
            changedPixels(uncut, centerCut) > 1_000,
            "A center cut must remove a substantial part of the front surface",
        )
        assertTrue(
            !centerCut.isBackground(width / 2, height / 2),
            "The newly exposed back face must remain visible with face culling disabled",
        )
        assertTrue(
            nonBackgroundPixels(backLimit) < 10,
            "A cut at -1 base radius must remove the normalized cube",
        )
    }

    @Test
    fun rendersExactImmersedAntiprismConfigurationWithActualShaders() = runTest {
        val prefix = "a(r(n))s(SA5_2)"
        val suffix =
            "v(r(-42,-22.1,-110.3)s(0.11)fw(0.06666667)fr(0.03333333))" +
            "p(l(0.55)c(0.16)h(298))e(s(60))"
        val image = renderConfiguration(
            prefix + "hf(γ,β,α)" + suffix,
            width = 480,
            height = 360,
        )
        val facesShown = renderConfiguration(prefix + suffix, width = 480, height = 360)

        assertEquals(480 * 360 * 4, image.rgba.length)
        var nonBackground = 0
        var opaque = 0
        var changedByHiddenFaces = 0
        for (offset in 0 until image.rgba.length step 4) {
            val red = image.rgba.unsigned(offset)
            val green = image.rgba.unsigned(offset + 1)
            val blue = image.rgba.unsigned(offset + 2)
            val alpha = image.rgba.unsigned(offset + 3)
            if (alpha == 255) opaque++
            if (red !in 240..244 || green !in 240..244 || blue !in 240..244) nonBackground++
            if (
                red != facesShown.rgba.unsigned(offset) ||
                green != facesShown.rgba.unsigned(offset + 1) ||
                blue != facesShown.rgba.unsigned(offset + 2)
            ) {
                changedByHiddenFaces++
            }
        }
        assertEquals(480 * 360, opaque, "The PNG input must be fully composited over the page background")
        assertTrue(nonBackground > 10_000, "The image must contain the rendered polyhedron and table")
        assertTrue(changedByHiddenFaces > 5_000, "Serialized hidden face orbits must switch to rim geometry")
    }
}

private fun changedPixels(first: RenderedImage, second: RenderedImage): Int {
    assertEquals(first.rgba.length, second.rgba.length)
    var changed = 0
    for (offset in 0 until first.rgba.length step 4) {
        if (
            first.rgba.unsigned(offset) != second.rgba.unsigned(offset) ||
            first.rgba.unsigned(offset + 1) != second.rgba.unsigned(offset + 1) ||
            first.rgba.unsigned(offset + 2) != second.rgba.unsigned(offset + 2)
        ) {
            changed++
        }
    }
    return changed
}

private fun nonBackgroundPixels(image: RenderedImage): Int {
    var count = 0
    for (offset in 0 until image.rgba.length step 4) {
        val red = image.rgba.unsigned(offset)
        val green = image.rgba.unsigned(offset + 1)
        val blue = image.rgba.unsigned(offset + 2)
        if (red !in 240..244 || green !in 240..244 || blue !in 240..244) count++
    }
    return count
}

private fun RenderedImage.isBackground(x: Int, y: Int): Boolean {
    val offset = (y * width + x) * 4
    return rgba.unsigned(offset) in 240..244 &&
        rgba.unsigned(offset + 1) in 240..244 &&
        rgba.unsigned(offset + 2) in 240..244
}

private fun org.khronos.webgl.Uint8Array.unsigned(index: Int): Int =
    js("(pixels, index) => pixels[index]").unsafeCast<(org.khronos.webgl.Uint8Array, Int) -> Int>()(this, index)
