package polyhedra.js.glsl

import kotlin.reflect.*

enum class GLDeclKind(val isGlobal: Boolean = false) {
    builtin, uniform(true), attribute(true), varying(true), function(true), local;
}

open class GLDecl<T : GLType<T>, SELF: GLDecl<T, SELF>>(
    val kind: GLDeclKind,
    val precision: GLPrecision?,
    override val type: T,
    val name: String
) : GLExpr<T> {
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, prop: KProperty<*>): SELF = this as SELF

    override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) {
        visitor(this)
    }

    override fun toString(): String = name

    open fun emitDeclaration(): String =
        if (precision == null) "$kind $type $name;" else "$kind $precision $type $name;"
}

private class FunctionCall<T : GLType<T>>(
    val function: GLFunction<T>,
    val name: String, vararg val a: GLExpr<*>
) : GLExpr<T> {
    override val type: T = function.resultType

    override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) {
        function.visitDecls(visitor)
        a.forEach { it.visitDecls(visitor) }
    }

    override fun toString(): String = "$name(${a.joinToString(", ")})"
}

class GLFunction<T : GLType<T>>(
    val resultType: T,
    name: String,
    private val deps: Set<GLDecl<*, *>>,
    private val body: List<String>
) : GLDecl<GLType.fun0<T>, GLFunction<T>>(GLDeclKind.function, null, GLType.fun0(resultType), name) {
    override fun emitDeclaration() = buildString {
        appendLine("$resultType $name() {")
        body.forEach { appendLine(it) }
        append("}")
    }

    override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) {
        deps.forEach { visitor(it) }
        visitor(this)
    }

    operator fun invoke(): GLExpr<T> = FunctionCall(this, name)
}

class GLLocal<T : GLType<T>>(
    precision: GLPrecision?,
    type: T,
    name: String,
    val value: GLExpr<T>
) : GLDecl<T, GLLocal<T>>(GLDeclKind.local, precision, type, name) {
    override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) {
        value.visitDecls(visitor)
        visitor(this)
    }

    override fun emitDeclaration(): String = buildString {
        append(if (precision == null) "$type $name" else "$precision $type $name")
        append(" = ")
        append(value)
        append(";")
    }
}

operator fun <T : GLType<T>> GLExpr<T>.provideDelegate(thisRef: Any?, prop: KProperty<*>): LocalProvider<T> =
    LocalProvider(prop.name, this)

class LocalProvider<T : GLType<T>>(private val name: String, private val value: GLExpr<T>) {
    private val local = GLLocal<T>(null, value.type, name, value)
    operator fun getValue(thisRef: Any?, prop: KProperty<*>): GLLocal<T> = local
}