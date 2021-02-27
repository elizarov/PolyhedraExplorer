package polyhedra.js.glsl

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.poly.*
import polyhedra.js.util.*
import kotlin.properties.*
import kotlin.reflect.*
import org.khronos.webgl.WebGLRenderingContext as GL

abstract class GLProgram(val gl: GL) {
    val gl_Position by builtin(GLType.vec4)
    val gl_FragColor by builtin(GLType.vec4)

    abstract val vertexShader: Shader<ShaderType.Vertex>
    abstract val fragmentShader: Shader<ShaderType.Fragment>

    val program by lazy {
        initShaderProgram(gl, vertexShader.glShader, fragmentShader.glShader)
    }

    fun <S : ShaderType> shader(type: S, builder: GLBlockBuilder<GLType.void>.() -> Unit): Shader<S> {
        val main by functionVoid(builder)
        return Shader(loadShader(gl, type.glType, shaderSource(main)))
    }

    inner class Uniform<T : GLType<T>>(
        precision: GLPrecision?, type: T, name: String
    ) : GLDecl<T, Uniform<T>>(GLDeclKind.uniform, precision, type, name) {
        val location by lazy { gl.getUniformLocation(program, name)!! }
        var isUsed = false

        override fun emitDeclaration(): String {
            isUsed = true
            return super.emitDeclaration()
        }
    }

    infix fun Uniform<GLType.int>.by(value: Int) {
        if (isUsed) {
            gl.uniform1i(location, value)
        }
    }

    infix fun Uniform<GLType.float>.by(value: Double) {
        if (isUsed) {
            gl.uniform1f(location, value.toFloat())
        }
    }

    infix fun <T : GLType.VecOrMatrixFloats<T>> Uniform<T>.by(value: Float32Array) {
        if (isUsed) {
            type.uniformFloat32Array(gl, location, value)
        }
    }

    inner class Attribute<T : GLType<T>>(
        precision: GLPrecision?, type: T, name: String
    ) : GLDecl<T, Attribute<T>>(GLDeclKind.attribute, precision, type, name) {
        val gl: GL get() = this@GLProgram.gl
        val location by lazy { gl.getAttribLocation(program, name) }
    }

    infix fun <T : GLType.Floats<T>> Attribute<T>.by(buffer: Float32Buffer<T>) {
        gl.bindBuffer(GL.ARRAY_BUFFER, buffer.glBuffer)
        enable()
    }

    fun <T : GLType.Numbers<T>> Attribute<T>.enable() {
        gl.vertexAttribPointer(location, type.bufferSize, GL.FLOAT, false, 0, 0)
        gl.enableVertexAttribArray(location)
    }

    inner class Varying<T : GLType<T>>(
        precision: GLPrecision?, type: T, name: String
    ) : GLDecl<T, Varying<T>>(GLDeclKind.varying, precision, type, name)

    inner class Builtin<T : GLType<T>>(
        type: T, name: String
    ) : GLDecl<T, Builtin<T>>(GLDeclKind.builtin, null, type, name) {
        override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) {}
    }

    inner class Shader<S : ShaderType>(
        val glShader: WebGLShader
    )

    private fun shaderSource(main: GLFun0<GLType.void>): String = buildString {
        val decls = mutableSetOf<GLDecl<*, *>>()
        main.visitDecls { decl ->
            if (decl.kind.isGlobal) decls += decl
        }
        appendLine("precision mediump float;") // default precision
        val sd = decls.sortedBy { it.kind }
        for (d in sd) appendLine(d.emitDeclaration())
    }

    fun <T : GLType<T>> uniform(type: T, precision: GLPrecision? = null): DelegateProvider<Uniform<T>> =
        DelegateProvider { Uniform(precision, type, it) }
    fun <T : GLType<T>> attribute(type: T, precision: GLPrecision? = null): DelegateProvider<Attribute<T>> =
        DelegateProvider { Attribute(precision, type, it) }
    fun <T : GLType<T>> varying(type: T, precision: GLPrecision? = null): DelegateProvider<Varying<T>> =
        DelegateProvider { Varying(precision, type, it) }
    private fun <T : GLType<T>> builtin(type: T): DelegateProvider<Builtin<T>> =
        DelegateProvider { Builtin(type, it) }
}

fun <P : GLProgram> P.use() {
    gl.useProgram(program)
}

fun <P : GLProgram> P.use(block: P.() -> Unit) {
    use()
    block()
}

sealed class ShaderType(val glType: Int) {
    object Vertex : ShaderType(GL.VERTEX_SHADER)
    object Fragment : ShaderType(GL.FRAGMENT_SHADER)
}
