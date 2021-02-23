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

    fun <S : ShaderType> shader(type: S, builder: ShaderBuilder<S>.() -> Unit): Shader<S> {
        val s = ShaderBuilder<S>()
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

    inner class Shader<S : ShaderType>(
        val glShader: WebGLShader
    )

    inner class ShaderBuilder<S : ShaderType> {
        private var mainFunction: GLFunction<GLType.void>? = null

        fun main(builder: BlockBuilder<GLType.void>.() -> Unit): GLFunction<GLType.void> {
            val main by functionVoid(builder)
            check(mainFunction == null) { "At most one main function is allowed" }
            mainFunction = main
            return main
        }

        fun source(): String = buildString {
            val mainFunction = mainFunction
            check(mainFunction != null) { "main() function must be defined" }
            val decls = mutableSetOf<GLDecl<*, *>>()
            mainFunction.visitDecls { decl ->
                if (decl.kind.isGlobal) decls += decl
            }
            appendLine("precision mediump float;") // default precision
            val sd = decls.sortedBy { it.kind }
            for (d in sd) appendLine(d.emitDeclaration())
        }
    }

    fun functionVoid(builder: BlockBuilder<GLType.void>.() -> Unit): Provider<GLFunction<GLType.void>> = Provider { name ->
        BlockBuilder(GLType.void, name, "\t").run {
            builder()
            build()
        }
    }

    fun <T : GLType<T>> function(resultType: T, builder: BlockBuilder<T>.() -> GLExpr<T>): Provider<GLFunction<T>> = Provider { name ->
        BlockBuilder(resultType, name, "\t").run {
            val result = builder()
            using(result)
            +"return $result;"
            build()
        }
    }

    inner class BlockBuilder<T : GLType<T>>(
        val resultType: T,
        val name: String,
        private val indent: String
    ) {
        private val locals = mutableSetOf<GLLocal<*>>()
        private val deps = mutableSetOf<GLDecl<*, *>>()
        private val body = ArrayList<String>()

        operator fun String.unaryPlus()  { body.add("$indent$this") }

        fun build(): GLFunction<T> =
            GLFunction(resultType, name, deps, body)

        infix fun <T : GLType<T>> GLDecl<T, *>.by(expr: GLExpr<T>) {
            using(this)
            using(expr)
            +"$this = $expr;"
        }

        fun <T : GLType<T>> using(expr: GLExpr<T>) = expr.visitDecls { decl ->
            when (decl) {
                is GLLocal<*> -> if (locals.add(decl)) {
                    +decl.emitDeclaration()
                }
                else -> deps += decl
            }
        }
    }

    inner class Provider<R>(val factory: (name: String) -> R) {
        operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): R = factory(prop.name)
    }

    fun <T : GLType<T>> uniform(type: T, precision: GLPrecision? = null): Provider<Uniform<T>> =
        Provider { Uniform(precision, type, it) }
    fun <T : GLType<T>> attribute(type: T, precision: GLPrecision? = null): Provider<Attribute<T>> =
        Provider { Attribute(precision, type, it) }
    fun <T : GLType<T>> varying(type: T, precision: GLPrecision? = null): Provider<Varying<T>> =
        Provider { Varying(precision, type, it) }
    
    private fun <T : GLType<T>> builtin(type: T): Provider<Builtin<T>> = Provider { Builtin(type, it) }
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
