package polyhedra.renderer

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.WebGLRenderingContext as GL
import polyhedra.model.util.Vec3
import polyhedra.model.util.unit
import polyhedra.model.util.times
import polyhedra.web.glsl.*
import polyhedra.web.poly.FaceProgram
import polyhedra.web.util.*
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pixel-level tests of the application's actual GLSL, with geometry/lighting held constant. */
class FaceLightingTest {
    @Test
    fun cutBandMarksTheLipAndFadesToUnmodifiedSurfaceLighting() {
        val ordinary = sample(tilt = 0.75)
        val lip = sample(tilt = 0.75, cutDepth = 0.002)
        val innerEdge = sample(tilt = 0.75, cutDepth = 0.0054)
        assertTrue(lip.red > ordinary.red + 40, "The lip must contrast with the material")
        assertTrue(innerEdge.red < ordinary.red, "The inner line must separate the lip from the cavity")
        assertEquals(ordinary, sample(tilt = 0.75, cutDepth = 0.1), "The band must be local to the cut")
        assertEquals(lip, sample(tilt = 0.75, cutDepth = 0.004, radius = 2.0), "Band scales with the model")
        assertEquals(lip.copy(alpha = 102), sample(tilt = 0.75, cutDepth = 0.002, opacity = 0.4))
        assertEquals(0, sample(tilt = 0.75, cutDepth = -0.01).alpha, "The removed side must stay discarded")
        assertEquals(sample(), sample(cutDepth = 0.0), "A coplanar face must not become an all-over band")
    }

    @Test
    fun reverseFacesAndExplicitUndersidesReceiveTheSameInteriorShading() {
        val front = sample()
        val reverse = sample(reverseWinding = true)
        val underside = sample(underside = true)
        assertEquals(reverse, underside, "Culling direction must not change the treatment of an underside")
        assertTrue(reverse.red in 30 until front.red - 20, "$reverse should be darker than $front, not black")
        assertTrue(reverse.red > reverse.green && reverse.green > reverse.blue, "Keep the material's hue")
    }

    @Test
    fun interiorOcclusionAttenuatesBothKeyAndFillIncludingGlossyReflections() {
        for ((key, fill) in listOf(2.5 to 0.0, 0.0 to 0.22, 2.5 to 0.22)) {
            for (roughness in listOf(0.15, 0.45, 1.0)) {
                val front = sample(key = key, fill = fill, roughness = roughness)
                val back = sample(underside = true, key = key, fill = fill, roughness = roughness)
                assertTrue(back.red < front.red * 0.85, "key=$key fill=$fill roughness=$roughness: $front / $back")
                assertTrue(back.red > 0)
            }
        }
        assertEquals(Pixel(0, 0, 0, 255), sample(underside = true, key = 0.0, fill = 0.0))
    }

    @Test
    fun cutDepthDarkensTheInteriorSmoothlyButNeverChangesTheExterior() {
        val front = sample()
        val depths = listOf(0.0, 0.25, 0.5, 1.0, 2.0)
        val back = depths.map { sample(underside = true, cutDepth = it) }
        assertTrue(back.zipWithNext().all { (a, b) -> a.red > b.red }, back.toString())
        assertTrue(back.last().red > 30, "Deep interiors must remain legible")
        for (depth in depths) assertEquals(front, sample(cutDepth = depth))
        assertEquals(sample(underside = true), sample(underside = true, cutDepth = 0.0))
        assertEquals(sample(underside = true, cutDepth = 1.0), sample(underside = true, cutDepth = 2.0, radius = 2.0))
    }

    @Test
    fun rimWallsDoNotUseTheThicknessFlagAsAMaterialSide() {
        val front = sample()
        assertEquals(front, sample(wall = true))
        assertEquals(front, sample(wall = true, underside = true))
    }

    @Test
    fun lookingIntoACutRimShadesTheReverseOfBothItsUndersideAndItsWalls() {
        for (depth in listOf(null, 0.0, 1.0)) {
            val interior = sample(reverseWinding = true, cutDepth = depth)
            val reverseUnderside = sample(reverseWinding = true, underside = true, cutDepth = depth)
            val reverseWall = sample(reverseWinding = true, wall = true, cutDepth = depth)
            assertEquals(interior, reverseUnderside, "Back of a rim underside is inside material, not an exterior")
            assertEquals(interior, reverseWall, "Back of a rim wall is inside material too")
        }
    }

    @Test
    fun transparencyOnlyChangesAlphaAndCutDoesNotChangeIt() {
        val opaque = sample(underside = true, cutDepth = 0.5)
        val transparent = sample(underside = true, cutDepth = 0.5, opacity = 0.4)
        assertEquals(opaque.copy(alpha = 102), transparent)
    }
}

private data class Pixel(val red: Int, val green: Int, val blue: Int, val alpha: Int)

private fun sample(
    reverseWinding: Boolean = false,
    underside: Boolean = false,
    wall: Boolean = false,
    cutDepth: Double? = null,
    radius: Double = 1.0,
    key: Double = 2.5,
    fill: Double = 0.22,
    roughness: Double = 0.45,
    opacity: Double = 1.0,
    tilt: Double = 0.0,
): Pixel {
    val gl = createContext(32, 32)
    try {
        val program = FaceProgram(gl)
        val positions = createBuffer(gl, GLType.vec3)
        // Tilt a real plane around the sampled pixel, whose NDC y is 1/32. Its depth stays zero.
        val vertices = listOf(-1.0 to -1.0, 1.0 to -1.0, 0.0 to 1.0).map { (x, y) ->
            Vec3(x, y, -tilt * (y - 1.0 / 32))
        }
        (if (reverseWinding) vertices.reversed() else vertices).forEachIndexed { i, v -> positions[i] = v }
        positions.bindBufferData(gl)
        val planeNormal = Vec3(0.0, tilt, 1.0).unit
        val normal = planeNormal * if (reverseWinding) -1.0 else 1.0
        val outward = if (wall) Vec3(1.0, 0.0, 0.0) else
            planeNormal * if (reverseWinding xor underside) -1.0 else 1.0
        fun GLProgram.Attribute<GLType.vec3>.constant(v: Vec3) {
            gl.vertexAttrib3f(location, v.x.toFloat(), v.y.toFloat(), v.z.toFloat())
        }
        fun GLProgram.Attribute<GLType.float>.constant(value: Double) {
            gl.vertexAttrib1f(location, value.toFloat())
        }
        program.use {
            uProjectionMatrix by mat4.create()
            uModelMatrix by mat4.create()
            uNormalMatrix by mat3.create()
            uCameraPosition by float32Of(0.0, 0.0, 4.0)
            uLightPosition by float32Of(-2.4, 3.2, 4.3)
            uLightColor by float32Of(1.0, 0.97, 0.92)
            uFillColor by float32Of(0.72, 0.80, 0.95)
            uKeyLightIntensity by key
            uFillLightIntensity by fill
            uRoughness by roughness
            uFresnelF0 by ((1.46 - 1.0) / (1.46 + 1.0)).pow(2)
            uInteriorRadius by radius
            uTargetFraction by 1.0
            uPrevFraction by 0.0
            uColorAlpha by opacity
            uCutEnabled by if (cutDepth == null) 0.0 else 1.0
            uCutPosition by (cutDepth ?: 0.0)
            uFaceWidth by 0.0
            uFaceRim by 0.0
            uExpand by 0.0
            uCullMode by 0.0
            aPosition by positions
            aPrevPosition by positions
            aLightNormal.constant(normal)
            aPrevLightNormal.constant(normal)
            aExpandDir.constant(outward)
            aPrevExpandDir.constant(outward)
            aThicknessDir.constant(Vec3.ZERO)
            aPrevThicknessDir.constant(Vec3.ZERO)
            aRimDir.constant(Vec3.ZERO)
            aPrevRimDir.constant(Vec3.ZERO)
            aRimMax.constant(0.0)
            aPrevRimMax.constant(0.0)
            aColor.constant(Vec3(0.6, 0.25, 0.12))
            aPrevColor.constant(Vec3(0.6, 0.25, 0.12))
            aInner.constant(if (underside) 1.0 else 0.0)
            aFaceMode.constant(1.0)
        }
        gl.viewport(0, 0, 32, 32)
        gl.disable(GL.CULL_FACE)
        gl.drawArrays(GL.TRIANGLES, 0, 3)
        val pixels = Uint8Array(4)
        gl.readPixels(16, 16, 1, 1, GL.RGBA, GL.UNSIGNED_BYTE, pixels)
        assertEquals(GL.NO_ERROR, gl.getError())
        fun channel(i: Int): Int = pixels.asDynamic()[i] as Int
        return Pixel(channel(0), channel(1), channel(2), channel(3))
    } finally {
        destroyContext(gl)
    }
}
