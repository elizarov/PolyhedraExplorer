package polyhedra.js.glsl

import org.khronos.webgl.*
import polyhedra.common.*
import polyhedra.js.poly.*
import polyhedra.js.util.*
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

    fun <T : ShaderType> shader(type: T, builder: ShaderBuilder.() -> Unit): Shader<T> {
        val s = ShaderBuilder()
        s.builder()
        return Shader(loadShader(gl, type.glType, s.source()))
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

    // todo: rename
    fun Uniform<GLType.float>.assign(value: Double) {
        if (isUsed) {
            gl.uniform1f(location, value.toFloat())
        }
    }

    // todo: rename
    fun <T : GLType.VecOrMatrixFloats<T>> Uniform<T>.assign(value: Float32Array) {
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

    // todo: rename
    fun <T : GLType.Floats<T>> Attribute<T>.assign(buffer: Float32Buffer<T>) {
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

    inner class Shader<T : ShaderType>(
        val glShader: WebGLShader
    )

    inner class ShaderBuilder {
        private val uniforms = mutableSetOf<Uniform<*>>()
        private val attributes = mutableSetOf<Attribute<*>>()
        private val varyings = mutableSetOf<Varying<*>>()
        private val lines = ArrayList<String>()

        fun main(builder: BlockBuilder.() -> Unit) = function(GLType.void, "main", builder)

        fun <T : GLType<T>> function(returnType: T, name: String, builder: BlockBuilder.() -> Unit): GLExpr<T> {
            lines += "$returnType $name() {"
            BlockBuilder("\t").builder()
            lines += "}"
            return glFunctionCallExpr<T>(returnType, name)
        }

        inner class BlockBuilder(val indent: String) {
            private val locals = mutableSetOf<GLLocal<*>>()
            operator fun String.unaryPlus()  { lines.add("$indent$this") }

            infix fun <T : GLType<T>> GLDecl<T, *>.by(expr: GLExpr<T>) {
                visitDecls(::declVisitor)
                expr.visitDecls(::declVisitor)
                +"$this = $expr;"
            }

            private fun declVisitor(decl: GLDecl<*, *>) {
                when (decl) {
                    is GLLocal<*> -> if (locals.add(decl)) {
                        +decl.emitDeclaration()
                    }
                    is Uniform<*> -> uniforms += decl
                    is Attribute<*> -> attributes += decl
                    is Varying<*> -> varyings += decl
                }
            }
        }

        fun source(): String = buildString {
            appendLine("precision mediump float;") // default precision
            for (d in uniforms) appendLine(d.emitDeclaration())
            for (d in attributes) appendLine(d.emitDeclaration())
            for (d in varyings) appendLine(d.emitDeclaration())
            for (l in lines) appendLine(l)
        }
    }

    inner class Provider<R>(val factory: (prop: KProperty<*>) -> R) {
        operator fun provideDelegate(program: GLProgram, prop: KProperty<*>): R {
            require(program === this@GLProgram)
            return factory(prop)
        }
    }

    fun <T : GLType<T>> uniform(type: T, precision: GLPrecision? = null): Provider<Uniform<T>> =
        Provider { Uniform(precision, type, it.name) }
    fun <T : GLType<T>> attribute(type: T, precision: GLPrecision? = null): Provider<Attribute<T>> =
        Provider { Attribute(precision, type, it.name) }
    fun <T : GLType<T>> varying(type: T, precision: GLPrecision? = null): Provider<Varying<T>> =
        Provider { Varying(precision, type, it.name) }
    
    private fun <T : GLType<T>> builtin(type: T): Provider<Builtin<T>> = Provider { Builtin(type, it.name) }
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
