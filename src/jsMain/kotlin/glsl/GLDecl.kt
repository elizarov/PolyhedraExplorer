package polyhedra.js.glsl

import kotlin.reflect.*

enum class GLDeclKind(val isGlobal: Boolean = false) {
    builtin,
    uniform(true),
    attribute(true),
    varying(true),
    function(true),
    parameter,
    local;
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
    override val type: T,
    val function: GLDecl<*, *>,
    val name: String,
    vararg val a: GLExpr<*>
) : GLExpr<T> {
    override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) {
        function.visitDecls(visitor)
        a.forEach { it.visitDecls(visitor) }
    }

    override fun toString(): String = "$name(${a.joinToString(", ")})"
}

abstract class GLFunX<T : GLType<T>, F : GLType<F>, SELF : GLFunX<T, F, SELF>>(
    val resultType: T,
    funType: F,
    name: String,
    private val deps: Set<GLDecl<*, *>>,
    private val body: List<String>,
    private vararg val params: GLParameter<*>
) : GLDecl<F, SELF>(GLDeclKind.function, null, funType, name) {
    override fun emitDeclaration() = buildString {
        appendLine("$resultType $name(${params.joinToString { it.emitDeclaration() }}) {")
        body.forEach { appendLine(it) }
        append("}")
    }

    override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) {
        deps.forEach { visitor(it) }
        visitor(this)
    }
}

class GLFun0<T : GLType<T>>(
    resultType: T,
    name: String,
    deps: Set<GLDecl<*, *>>,
    body: List<String>
) : GLFunX<T, GLType.fun0<T>, GLFun0<T>>(resultType, GLType.fun0(resultType), name, deps, body) {
    operator fun invoke(): GLExpr<T> = FunctionCall(resultType, this, name)
}

class GLFun1<T : GLType<T>, P1 : GLType<P1>>(
    resultType: T,
    name: String,
    deps: Set<GLDecl<*, *>>,
    body: List<String>,
    param1: GLParameter<P1>,
) : GLFunX<T, GLType.fun1<T, P1>, GLFun1<T, P1>>(resultType, GLType.fun1(resultType), name, deps, body, param1) {
    operator fun invoke(p1: GLExpr<P1>): GLExpr<T> = FunctionCall(resultType,this, name, p1)
}

class GLFun2<T : GLType<T>, P1 : GLType<P1>, P2 : GLType<P2>>(
    resultType: T,
    name: String,
    deps: Set<GLDecl<*, *>>,
    body: List<String>,
    param1: GLParameter<P1>,
    param2: GLParameter<P2>,
) : GLFunX<T, GLType.fun2<T, P1, P2>, GLFun2<T, P1, P2>>(resultType, GLType.fun2(resultType), name, deps, body, param1, param2) {
    operator fun invoke(p1: GLExpr<P1>, p2: GLExpr<P2>): GLExpr<T> = FunctionCall(resultType,this, name, p1, p2)
}

class GLParameter<T : GLType<T>>(
    precision: GLPrecision?,
    type: T,
    name: String
) : GLDecl<T, GLLocal<T>>(GLDeclKind.parameter, precision, type, name) {
    override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) {
        visitor(this)
    }

    override fun emitDeclaration(): String = buildString {
        append(if (precision == null) "$type $name" else "$precision $type $name")
    }
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