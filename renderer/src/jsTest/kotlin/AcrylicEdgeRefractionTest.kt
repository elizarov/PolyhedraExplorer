package polyhedra.renderer

import kotlinx.coroutines.test.runTest
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.WebGLRenderingContext as GL
import polyhedra.core.api.inspectCompactConfiguration
import polyhedra.web.main.RootParams
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.DrawContext
import polyhedra.web.poly.drawScene
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcrylicEdgeRefractionTest {
    @Test
    fun edgesOnlyBypassesAcrylicAndKeepsIdenticalPixels() = runTest {
        val configuration = "a(r(n))s(I)v(env(n)d(e)fw(0.15)c(y)e(0.1))"
        val inspection = inspectCompactConfiguration(configuration, calculateTweakRanges = false, detectSeed = false)
        val params = RootParams()
        params.loadFromString(configuration)
        params.render.poly.applyCoreResponse(inspection.configuration.state, inspection.response)
        val gl = createContext(320, 240)
        val draw = DrawContext(gl, params.render) {}
        try {
            var snapshots = 0
            val copyImage = gl.asDynamic().copyTexImage2D
            val copySubImage = gl.asDynamic().copyTexSubImage2D
            gl.asDynamic().copyTexImage2D = { target: Int, level: Int, format: Int, x: Int, y: Int, width: Int, height: Int, border: Int ->
                snapshots++
                copyImage.call(gl, target, level, format, x, y, width, height, border)
            }
            gl.asDynamic().copyTexSubImage2D = { target: Int, level: Int, dx: Int, dy: Int, x: Int, y: Int, width: Int, height: Int ->
                snapshots++
                copySubImage.call(gl, target, level, dx, dy, x, y, width, height)
            }
            fun pixels(acrylic: Boolean): Uint8Array {
                params.render.view.transparencyEnabled.updateValue(acrylic)
                params.performUpdate(null, 0.0)
                gl.clearColor(0.95f, 0.95f, 0.95f, 1.0f)
                draw.drawScene(320, 240)
                return Uint8Array(320 * 240 * 4).also {
                    gl.readPixels(0, 0, 320, 240, GL.RGBA, GL.UNSIGNED_BYTE, it)
                }
            }
            val opaque = pixels(false)
            val acrylic = pixels(true)
            assertTrue((0 until opaque.length).all { opaque.asDynamic()[it] == acrylic.asDynamic()[it] },
                "Edges-only must ignore acrylic even with cut, expansion and nonzero optical thickness")
            assertTrue((0 until opaque.length step 4).any { (opaque.asDynamic()[it] as Int) < 200 },
                "The test must actually draw edge lines")
            assertEquals(0, snapshots, "Edges-only must not run transmission framebuffer passes")
            assertEquals(GL.NO_ERROR, gl.getError())
        } finally {
            draw.destroy()
            params.destroy()
            destroyContext(gl)
        }
    }

    @Test
    fun rearIcosahedronEdgesMoveWithRefractionWhileFrontEdgesRemainSharp() = runTest {
        fun configuration(thickness: Double, facesOnly: Boolean) =
            "a(r(n))s(I)v(r(10,20,30)env(n)t(y)ta(1)fw($thickness)" +
                (if (facesOnly) "d(f)" else "") + ")l(ar(0.08))"
        suspend fun edgePixels(thickness: Double): Set<Int> {
            val faces = renderConfiguration(configuration(thickness, true), 480, 400)
            val edges = renderConfiguration(configuration(thickness, false), 480, 400)
            return buildSet {
                for (i in 0 until faces.rgba.length step 4) {
                    var darkening = 0
                    for (channel in 0..2) darkening = max(darkening,
                        (faces.rgba.asDynamic()[i + channel] as Int) - (edges.rgba.asDynamic()[i + channel] as Int))
                    if (darkening > 12) add(i / 4)
                }
            }
        }
        val thin = edgePixels(0.001)
        val thick = edgePixels(0.15)
        val shifted = thick - thin
        val vacated = thin - thick
        val retained = thick.intersect(thin)
        println("Acrylic edge refraction: ${shifted.size} shifted pixels, ${vacated.size} vacated pixels, ${retained.size} stationary pixels")
        assertTrue(shifted.size > 150, "Rear edge lines must move with transmitted geometry, not remain an unrefracted overlay")
        assertTrue(vacated.size > 150, "Rear lines must not also remain at their unrefracted positions")
        assertTrue(retained.size > 150, "Directly visible front edges must remain at their geometric positions")
    }
}
