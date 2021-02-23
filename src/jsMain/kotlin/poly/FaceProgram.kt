package polyhedra.js.poly

import polyhedra.js.glsl.*
import org.khronos.webgl.WebGLRenderingContext as GL

class FaceProgram(gl: GL) : SharedPolyProgram(gl) {
    val uAmbientLightColor by uniform(GLType.vec3)
    val uDiffuseLightColor by uniform(GLType.vec3)
    val uSpecularLightColor by uniform(GLType.vec3)
    val uSpecularLightPower by uniform(GLType.float)
    val uLightPosition by uniform(GLType.vec3)

    val aVertexColor by attribute(GLType.vec3)

    private val vNormal by varying(GLType.vec3)
    private val vToCamera by varying(GLType.vec3)
    private val vToLight by varying(GLType.vec3)
    private val vColor by varying(GLType.vec3, GLPrecision.lowp)

    override val vertexShader = shader(ShaderType.Vertex) {
        main {
            // position
            val expandedPosition by aVertexPosition + aVertexNormal * uExpand
            val worldPosition by uModelMatrix * vec4(expandedPosition, 1.0)
            gl_Position by uProjectionMatrix * worldPosition
            // lighting & color
            vNormal by uNormalMatrix * aVertexNormal
            vToCamera by uCameraPosition - worldPosition.xyz
            vToLight by uLightPosition - worldPosition.xyz
            vColor by aVertexColor
        }
    }

    override val fragmentShader = shader(ShaderType.Fragment) {
        main {
            val normToCamera by normalize(vToCamera)
            val normToLight by normalize(vToLight)
            val halfVector by normalize(normToCamera + normToLight)
            val light by uAmbientLightColor + uDiffuseLightColor * max(dot(vNormal, normToLight), 0.0)
            val specular by uSpecularLightColor * pow(max(dot(vNormal, halfVector), 0.0), uSpecularLightPower)
            gl_FragColor by vec4(vColor * light + specular, uColorAlpha)
        }
    }
}