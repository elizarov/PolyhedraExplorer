package polyhedra.js.poly

import polyhedra.js.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

class EdgeProgram(gl: GL) : SharedPolyProgram(gl) {
    val uVertexColor by uniform(GLType.vec4, GLPrecision.lowp)

    override val vertexShader = shader(ShaderType.Vertex) {
        main {
            gl_Position by uProjectionMatrix * fPosition()
        }
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        main {
            gl_FragColor by uVertexColor
        }
    }
}