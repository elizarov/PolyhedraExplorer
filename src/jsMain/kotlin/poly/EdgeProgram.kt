package polyhedra.js.poly

import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

class EdgeProgram(gl: GL) : ViewProgram(gl) {
    val aVertexPosition by attribute(GLType.vec3)
    val uVertexColor by uniform(GLType.vec4, GLPrecision.lowp)

    override val vertexShader = shader(ShaderType.Vertex) {
        main {
            gl_Position.assign(uProjectionMatrix * uModelViewMatrix * vec4(aVertexPosition, 1.0))
        }
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        main {
            gl_FragColor.assign(uVertexColor)
        }
    }
}