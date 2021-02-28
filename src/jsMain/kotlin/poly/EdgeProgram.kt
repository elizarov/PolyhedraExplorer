package polyhedra.js.poly

import polyhedra.js.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

class EdgeProgram(gl: GL) : PolyProgram(gl) {
    val uVertexColor by uniform(GLType.vec4, GLPrecision.lowp)
    val uCullMode by uniform(GLType.float) // 0 - no, 1 - cull front, -1 - cull back
    val vColorMul by varying(GLType.float)

    override val vertexShader = shader(ShaderType.Vertex) {
        val position by fPosition()
        gl_Position by uProjectionMatrix * position
        vColorMul by select(
            uCullMode eq 0.0.literal,
            1.0.literal,
            select(fFaceDirection(position, fNormal()) * uCullMode ge 0.0.literal, 1.0.literal, 0.0.literal)
        )
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        gl_FragColor by uVertexColor * vColorMul
    }
}