package polyhedra.js.poly

import polyhedra.js.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

class EdgeProgram(gl: GL) : SharedPolyProgram(gl) {
    val uVertexColor by uniform(GLType.vec4, GLPrecision.lowp)

    override val vertexShader = shader(ShaderType.Vertex) {
        main {
            val position = aVertexPosition + aVertexNormal * uExpand
            gl_Position.by(uProjectionMatrix * uModelMatrix * vec4(position, 1.0))
        }
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        main {
            gl_FragColor.by(uVertexColor)
        }
    }
}