package polyhedra.js.poly

import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceProgram(gl: GL) : ViewProgram(gl) {
    val uAmbientLightColor by uniform(GLType.vec3)
    val uDirectionalLightColor by uniform(GLType.vec3)
    val uDirectionalLightVector by uniform(GLType.vec3)

    val aVertexPosition by attribute(GLType.vec3)
    val aVertexNormal by attribute(GLType.vec3)
    val aVertexColor by attribute(GLType.vec4)

    val vColor by varying(GLType.vec4, GLPrecision.lowp)
    val vLighting by varying(GLType.vec3, GLPrecision.highp)

    override val vertexShader = shader(ShaderType.Vertex) {
        main {
            gl_Position.assign(uProjectionMatrix * uModelViewMatrix * vec4(aVertexPosition, 1.0))
            val transformedNormal = uNormalMatrix * aVertexNormal
            val directional = max(dot(transformedNormal, uDirectionalLightVector), 0.0)
            vColor.assign(aVertexColor);
            vLighting.assign(uAmbientLightColor + (uDirectionalLightColor * directional))
        }
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        main {
            gl_FragColor.assign(vec4(vColor.rgb * vLighting, vColor.a))
        }
    }
}