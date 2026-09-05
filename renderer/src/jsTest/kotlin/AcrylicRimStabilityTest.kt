package polyhedra.renderer

import kotlinx.coroutines.test.runTest
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.WebGLRenderingContext as GL
import polyhedra.core.api.inspectCompactConfiguration
import polyhedra.web.main.RootParams
import polyhedra.web.params.Param
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.DrawContext
import polyhedra.web.poly.drawScene
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcrylicRimStabilityTest {
    @Test
    fun hiddenCubeRimsStayContinuousAroundTheReportedGrazingView() = runTest {
        val configuration = "a(r(n))s(C)hf(α)v(r(-170.5,-28.9,175.7)t(y)fw(0.08)fr(0.04))e(s(50))"
        val inspection = inspectCompactConfiguration(configuration, calculateTweakRanges = false, detectSeed = false)
        val params = RootParams()
        params.loadFromString(configuration)
        params.render.poly.applyCoreResponse(inspection.configuration.state, inspection.response)
        val width = 480
        val height = 400
        val gl = createContext(width, height)
        val draw = DrawContext(gl, params.render) {}
        try {
            val baseline = listOf(-170.5, -28.9, 175.7)
            var largestJump = 0
            var worst = ""
            for (axis in 0..2) {
                var previous: Uint8Array? = null
                for (step in -150..150) {
                    val angles = baseline.mapIndexed { index, angle -> angle + if (axis == index) step * 0.01 else 0.0 }
                    params.render.view.rotate.updateValue(
                        params.render.view.rotate.parseValue(angles.joinToString(","))!!, Param.TargetValue,
                    )
                    params.performUpdate(null, 0.0)
                    gl.clearColor(0.95f, 0.95f, 0.95f, 1.0f)
                    draw.drawScene(width, height)
                    val pixels = Uint8Array(width * height * 4)
                    gl.readPixels(0, 0, width, height, GL.RGBA, GL.UNSIGNED_BYTE, pixels)
                    previous?.let { before ->
                        var jump = 0
                        // Readback is bottom-up. This region contains the bottom rim and excludes
                        // the distant shadow. Count large color changes, not ordinary AA motion.
                        for (y in 100 until 210) for (x in 100 until 370) {
                            val offset = (y * width + x) * 4
                            var delta = 0
                            for (c in 0..2) delta = max(delta,
                                abs((pixels.asDynamic()[offset + c] as Int) - (before.asDynamic()[offset + c] as Int)))
                            if (delta > 24) jump++
                        }
                        if (jump > largestJump) {
                            largestJump = jump
                            worst = "axis=$axis angles=$angles"
                        }
                    }
                    previous = pixels
                }
            }
            println("Acrylic grazing rim: max $largestJump abrupt pixels at $worst")
            assertEquals(GL.NO_ERROR, gl.getError())
            assertTrue(largestJump < 100, "A 0.01-degree rotation must not reclassify a visible rim wall: $largestJump at $worst")
        } finally {
            draw.destroy()
            params.destroy()
            destroyContext(gl)
        }
    }
}
