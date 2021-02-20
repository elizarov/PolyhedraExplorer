package polyhedra.js.util

import org.khronos.webgl.*
import polyhedra.common.*
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

    fun useProgram() {
        gl.useProgram(program)
    }

    fun <T : ShaderType> shader(type: T, builder: ShaderBuilder.() -> Unit): Shader<T> {
        val s = ShaderBuilder()
        s.builder()
        return Shader(loadShader(gl, type.glType, s.source()))
    }

    interface Expr<T> {
        fun dependencies(): Set<Decl<*, *>>
    }

    private class BinaryOp<T>(val a: Expr<*>, val op: String, val b: Expr<*>) : Expr<T> {
        override fun dependencies(): Set<Decl<*, *>> = a.dependencies() + b.dependencies()
        override fun toString(): String = "($a $op $b)"
    }

    private class Call<T>(val name: String, vararg val a: Expr<*>) : Expr<T> {
        override fun dependencies(): Set<Decl<*, *>> = a.fold(emptySet()) { acc, e -> acc + e.dependencies() }
        override fun toString(): String = "$name(${a.joinToString(", ")})"
    }

    private class Literal<T>(val s: String): Expr<T> {
        override fun dependencies(): Set<Decl<*, *>> = emptySet()
        override fun toString(): String = s
    }

    private class DotCall<T>(val a: Expr<*>, val name: String) : Expr<T> {
        override fun dependencies(): Set<Decl<*, *>> = a.dependencies()
        override fun toString(): String = "$a.$name"
    }

    val Double.literal: Expr<GLType.float> get() {
        val s0 = toString()
        val s = if (s0.contains('.')) s0 else "$s0.0"
        return Literal(s)
    }

    operator fun Expr<GLType.mat4>.times(other: Expr<GLType.mat4>): Expr<GLType.mat4> = BinaryOp(this, "*", other)
    operator fun Expr<GLType.mat4>.times(other: Expr<GLType.vec4>): Expr<GLType.vec4> = BinaryOp(this, "*", other)
    operator fun Expr<GLType.mat3>.times(other: Expr<GLType.vec3>): Expr<GLType.vec3> = BinaryOp(this, "*", other)
    operator fun Expr<GLType.vec3>.times(other: Expr<GLType.vec3>): Expr<GLType.vec3> = BinaryOp(this, "*", other)
    operator fun Expr<GLType.vec3>.times(other: Expr<GLType.float>): Expr<GLType.vec3> = BinaryOp(this, "*", other)

    operator fun Expr<GLType.float>.plus(other: Expr<GLType.float>): Expr<GLType.float> = BinaryOp(this, "+", other)
    operator fun Expr<GLType.vec3>.plus(other: Expr<GLType.vec3>): Expr<GLType.vec3> = BinaryOp(this, "+", other)
    operator fun Expr<GLType.vec4>.plus(other: Expr<GLType.vec4>): Expr<GLType.vec4> = BinaryOp(this, "+", other)

    fun dot(a: Expr<GLType.vec3>, b: Expr<GLType.vec3>): Expr<GLType.float> = Call("dot", a, b)
    fun dot(a: Expr<GLType.vec4>, b: Expr<GLType.vec4>): Expr<GLType.float> = Call("dot", a, b)

    fun max(a: Expr<GLType.float>, b: Expr<GLType.float>): Expr<GLType.float> = Call("max", a, b)
    fun max(a: Expr<GLType.float>, b: Double) = max(a, b.literal)

    fun vec4(a: Expr<GLType.vec3>, b: Expr<GLType.float>): Expr<GLType.vec4> = Call("vec4", a, b)
    
    val Expr<GLType.vec4>.rgb: Expr<GLType.vec3> get() = DotCall(this, "rgb")
    val Expr<GLType.vec4>.a: Expr<GLType.float> get() = DotCall(this, "a")

    open inner class Decl<T : GLType<T>, SELF: Decl<T, SELF>>(
        val kind: String,
        val type: GLType<T>,
        prop: KProperty<*>
    ) : Expr<T> {
        val name: String = prop.name

        @Suppress("UNCHECKED_CAST")
        operator fun getValue(program: GLProgram, prop: KProperty<*>): SELF = this as SELF

        override fun dependencies(): Set<Decl<*, *>> = setOf(this)
        override fun toString(): String = name
        open fun declaration(): String = "$kind $type $name"
    }

    inner class Uniform<T : GLType<T>>(type: GLType<T>, prop: KProperty<*>) : Decl<T, Uniform<T>>("uniform", type, prop) {
        val location by lazy { gl.getUniformLocation(program, name)!! }
    }

    inner class Attribute<T : GLType<T>>(type: GLType<T>, prop: KProperty<*>) : Decl<T, Attribute<T>>("attribute", type, prop) {
        val location by lazy { gl.getAttribLocation(program, name) }
    }

    inner class Varying<T : GLType<T>>(val precision: GLPrecision?, type: GLType<T>, prop: KProperty<*>) : Decl<T, Varying<T>>("varying", type, prop) {
        override fun declaration(): String =
            if (precision == null) super.declaration() else "$kind $precision $type $name"
    }

    inner class Builtin<T : GLType<T>>(type: GLType<T>, prop: KProperty<*>) : Decl<T, Builtin<T>>("builtin", type, prop) {
        override fun dependencies(): Set<Decl<*, *>> = emptySet()
    }

    inner class Shader<T : ShaderType>(
        val glShader: WebGLShader
    )

    inner class ShaderBuilder {
        private val dependencies = mutableSetOf<Decl<*, *>>()
        private val lines = ArrayList<String>()

        fun main(builder: BlockBuilder.() -> Unit) = function(GLType.void, "main", builder)

        fun <T : GLType<T>> function(returnType: GLType<T>, name: String, builder: BlockBuilder.() -> Unit): Expr<T> {
            lines += "$returnType $name() {"
            BlockBuilder("\t").builder()
            lines += "}"
            return Call(name)
        }

        inner class BlockBuilder(val indent: String) {
            operator fun String.unaryPlus()  { lines.add("$indent$this") }

            fun <T : GLType<T>> Decl<T, *>.assign(expr: Expr<T>) {
                dependencies += dependencies()
                dependencies += expr.dependencies()
                +"$this = $expr;"
            }
        }

        fun source(): String = buildString {
            for (d in dependencies) appendLine("${d.declaration()};")
            for (l in lines) appendLine(l)
        }
    }

    inner class Provider<R>(val factory: (prop: KProperty<*>) -> R) {
        operator fun provideDelegate(program: GLProgram, prop: KProperty<*>): R {
            require(program === this@GLProgram)
            return factory(prop)
        }
    }

    fun <T : GLType<T>> uniform(type: GLType<T>): Provider<Uniform<T>> = Provider { Uniform(type, it) }
    fun <T : GLType<T>> attribute(type: GLType<T>): Provider<Attribute<T>> = Provider { Attribute(type, it) }
    fun <T : GLType<T>> varying(type: GLType<T>, precision: GLPrecision? = null): Provider<Varying<T>> = Provider { Varying(precision, type, it) }
    
    private fun <T : GLType<T>> builtin(type: GLType<T>): Provider<Builtin<T>> = Provider { Builtin(type, it) }
}

enum class GLPrecision { lowp, mediump, highp }

sealed class GLType<T : GLType<T>> {
    object void : GLType<void>()
    object float : GLType<float>()
    object vec3 : GLType<vec3>()
    object vec4 : GLType<vec4>()
    object mat3 : GLType<mat3>()
    object mat4 : GLType<mat4>()

    override fun toString(): String = this::class.simpleName!!
}

sealed class ShaderType(val glType: Int) {
    object Vertex : ShaderType(GL.VERTEX_SHADER)
    object Fragment : ShaderType(GL.FRAGMENT_SHADER)
}
