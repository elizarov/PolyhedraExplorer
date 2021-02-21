package polyhedra.js.glsl

import kotlin.reflect.*

enum class GLDeclKind { local, builtin, uniform, attribute, varying }

open class GLDecl<T : GLType<T>, SELF: GLDecl<T, SELF>>(
    val kind: GLDeclKind,
    val precision: GLPrecision?,
    override val type: T,
    val name: String
) : GLExpr<T> {
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(program: GLProgram, prop: KProperty<*>): SELF = this as SELF

    override fun externalsTo(destination: MutableCollection<GLDecl<*, *>>) {
        destination.add(this)
    }

    override fun localsTo(destination: MutableCollection<GLLocal<*>>) {}

    override fun toString(): String = name

    open fun emitDeclaration(): String =
        if (precision == null) "$kind $type $name" else "$kind $precision $type $name"
}

class GLLocal<T : GLType<T>>(
    precision: GLPrecision?,
    type: T,
    name: String,
    val value: GLExpr<T>
) : GLDecl<T, GLLocal<T>>(GLDeclKind.local, precision, type, name) {
    override fun externalsTo(destination: MutableCollection<GLDecl<*, *>>) {
        value.externalsTo(destination)
    }

    override fun localsTo(destination: MutableCollection<GLLocal<*>>) {
        value.localsTo(destination)
        destination.add(this)
    }

    override fun emitDeclaration(): String = buildString {
        append(if (precision == null) "$type $name" else "$precision $type $name")
        append(" = ")
        append(value)
    }
}

operator fun <T : GLType<T>> GLExpr<T>.provideDelegate(thisRef: Any?, prop: KProperty<*>): LocalProvider<T> =
    LocalProvider(prop.name, this)

class LocalProvider<T : GLType<T>>(private val name: String, private val value: GLExpr<T>) {
    private val local = GLLocal<T>(null, value.type, name, value)
    operator fun getValue(thisRef: Any?, prop: KProperty<*>): GLLocal<T> = local
}