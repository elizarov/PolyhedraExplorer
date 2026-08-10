/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import org.khronos.webgl.Float32Array
import polyhedra.model.util.Tagged
import polyhedra.model.util.Vec3
import polyhedra.model.util.minus
import polyhedra.model.util.plus
import polyhedra.model.util.times
import polyhedra.web.glsl.GLType
import polyhedra.web.glsl.createBuffer
import polyhedra.web.glsl.set
import polyhedra.web.glsl.use
import polyhedra.web.params.Param
import org.khronos.webgl.WebGLRenderingContext as GL

enum class SceneEnvironment(override val tag: String) : Tagged {
    None("n"),
    Table("t"),
}

val SceneEnvironments: List<SceneEnvironment> = SceneEnvironment.entries

private const val TABLE_FLOAT_GAP = 0.18
private const val TABLE_ROUGHNESS = 0.76
private const val TABLE_IOR = 1.46
private const val TABLE_SHADOW_LIFT = 0.002
private const val SHADOW_OPACITY = 0.42

private val tableVertices = listOf(
    Vec3(-40.0, 0.0, 3.8),
    Vec3(40.0, 0.0, 3.8),
    Vec3(40.0, 0.0, -25.0),
    Vec3(-40.0, 0.0, 3.8),
    Vec3(40.0, 0.0, -25.0),
    Vec3(-40.0, 0.0, -25.0),
)

internal fun environmentTableHeight(circumradius: Double, scaleFactor: Double): Double {
    require(circumradius > 0.0)
    require(scaleFactor > 0.0)
    return -(circumradius + TABLE_FLOAT_GAP) * scaleFactor
}

internal fun projectPointToTable(point: Vec3, light: Vec3, tableHeight: Double): Vec3 {
    val ray = point - light
    val intersection = (tableHeight - light.y) / ray.y
    return light + ray * intersection
}

class EnvironmentContext(private val gl: GL, params: RenderParams) : Param.Context(params) {
    private val environment by { params.view.environment.value }
    private val circumradius by { params.poly.poly?.circumradius ?: 1.0 }

    private val tableProgram by lazy { TableProgram(gl) }
    private val shadowProgram by lazy { TableShadowProgram(gl) }
    private val tablePositionBuffer by lazy {
        createBuffer(gl, GLType.vec3).also { buffer ->
            buffer.ensureCapacity(tableVertices.size)
            for (index in tableVertices.indices) buffer[index] = tableVertices[index]
            buffer.bindBufferData(gl)
        }
    }
    private val tableColor = Float32Array(3).apply {
        this[0] = 0.63
        this[1] = 0.63
        this[2] = 0.63
    }

    init { setup() }

    override fun update() = Unit

    fun draw(view: ViewContext, lighting: LightingContext, faces: FaceContext) {
        if (environment != SceneEnvironment.Table) return
        val tableHeight = environmentTableHeight(circumradius, view.scaleFactor)
        drawTable(view, lighting, tableHeight)
        drawShadow(view, lighting, faces, tableHeight)
    }

    private fun drawTable(view: ViewContext, lighting: LightingContext, tableHeight: Double) {
        tableProgram.use {
            uProjectionMatrix by view.projectionMatrix
            uCameraPosition by view.cameraPosition
            uLightColor by lighting.lightColor
            uFillColor by lighting.fillColor
            uLightPosition by lighting.lightPosition
            uKeyLightIntensity by lighting.keyLightIntensity
            uFillLightIntensity by lighting.fillLightIntensity
            uRoughness by TABLE_ROUGHNESS
            uFresnelF0 by dielectricF0(TABLE_IOR)
            uTableColor by tableColor
            uTableHeight by tableHeight
            aPosition by tablePositionBuffer
        }
        gl[GL.DEPTH_TEST] = true
        gl[GL.BLEND] = false
        gl[GL.STENCIL_TEST] = false
        gl[GL.CULL_FACE] = false
        gl.depthMask(true)
        gl.drawArrays(GL.TRIANGLES, 0, tableVertices.size)
        gl[GL.CULL_FACE] = true
    }

    private fun drawShadow(
        view: ViewContext,
        lighting: LightingContext,
        faces: FaceContext,
        tableHeight: Double,
    ) {
        if (!faces.drawFaces || faces.indexSize == 0) return
        val materialOpacity = 1.0 - view.transparentFaces
        val shadowOpacity = SHADOW_OPACITY * lighting.keyLightIntensity /
            (lighting.keyLightIntensity + 1.5 * lighting.fillLightIntensity + 0.8) * materialOpacity
        if (shadowOpacity <= 0.001) return

        val animation = faces.animation
        val previousOrTarget = if (animation != null) faces.prev else faces.target
        shadowProgram.use {
            assignView(view)
            uLightPosition by lighting.lightPosition
            uTableHeight by tableHeight
            uShadowLift by TABLE_SHADOW_LIFT * view.scaleFactor
            uShadowOpacity by shadowOpacity
            uTargetFraction by (animation?.targetFraction ?: 1.0)
            uPrevFraction by (animation?.prevFraction ?: 0.0)
            aPosition by faces.target.positionBuffer
            aExpandDir by faces.target.expandDirBuffer
            aRimDir by faces.target.rimDirBuffer
            aRimMax by faces.target.rimMaxBuffer
            aPrevPosition by previousOrTarget.positionBuffer
            aPrevExpandDir by previousOrTarget.expandDirBuffer
            aPrevRimDir by previousOrTarget.rimDirBuffer
            aPrevRimMax by previousOrTarget.rimMaxBuffer
            aInner by faces.innerBuffer
        }
        gl.bindBuffer(GL.ELEMENT_ARRAY_BUFFER, faces.indexBuffer.glBuffer)
        gl.depthMask(false)
        gl[GL.BLEND] = true
        // Darken the receiver while keeping its opaque canvas alpha. Letting the
        // shadow fragment lower destination alpha would make the white page show
        // through and turn the silhouette into a false light reflection.
        gl.blendFuncSeparate(GL.ZERO, GL.ONE_MINUS_SRC_ALPHA, GL.ZERO, GL.ONE)
        gl[GL.CULL_FACE] = false
        gl[GL.STENCIL_TEST] = true
        gl.clearStencil(0)
        gl.stencilMask(0xff)
        gl.stencilFunc(GL.EQUAL, 0, 0xff)
        gl.stencilOp(GL.KEEP, GL.KEEP, GL.INCR)
        gl.drawElements(GL.TRIANGLES, faces.indexSize, GL.UNSIGNED_INT, 0)
        gl[GL.STENCIL_TEST] = false
        gl[GL.CULL_FACE] = true
        gl[GL.BLEND] = false
        gl.blendFunc(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA)
        gl.depthMask(true)
    }
}
