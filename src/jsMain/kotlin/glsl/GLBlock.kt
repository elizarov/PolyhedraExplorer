/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.glsl

import polyhedra.common.util.*

class GLBlockBuilder<T : GLType<T>>(
    private val resultType: T,
    private val name: String,
    private val indent: String
) {
    private val locals = mutableSetOf<GLLocal<*>>()
    private val deps = mutableSetOf<GLDecl<*, *>>()
    private val body = ArrayList<String>()

    operator fun String.unaryPlus()  { body.add("$indent$this") }

    fun <F> build(factory: (name: String, deps: Set<GLDecl<*, *>>, body: List<String>) -> F): F =
        factory(name, deps, body)

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

fun functionVoid(builder: GLBlockBuilder<GLType.void>.() -> Unit): DelegateProvider<GLFun0<GLType.void>> =
    DelegateProvider { name ->
        GLBlockBuilder(GLType.void, name, "\t").run {
            builder()
            build { name, deps, body ->
                GLFun0(GLType.void, name, deps, body)
            }
        }
    }

fun <T : GLType<T>> function(
    resultType: T,
    builder: GLBlockBuilder<T>.() -> GLExpr<T>
): DelegateProvider<GLFun0<T>> =
    functionX(resultType, builder,
        factory = { name, deps, body ->
            GLFun0(resultType, name, deps, body)
        }
    )

fun <T : GLType<T>, P1 : GLType<P1>> function(
    resultType: T,
    param1Name: String, param1Type: P1,
    builder: GLBlockBuilder<T>.(p1 : GLParameter<P1>) -> GLExpr<T>
): DelegateProvider<GLFun1<T, P1>> {
    val p1 = GLParameter(null, param1Type, param1Name)
    return functionX(resultType,
        builder = { builder(p1) },
        factory = { name, deps, body ->
            GLFun1(resultType, name, deps, body, p1)
        }
    )
}

fun <T : GLType<T>, P1 : GLType<P1>, P2 : GLType<P2>> function(
    resultType: T,
    param1Name: String, param1Type: P1,
    param2Name: String, param2Type: P2,
    builder: GLBlockBuilder<T>.(p1 : GLParameter<P1>, p2 : GLParameter<P2>) -> GLExpr<T>
): DelegateProvider<GLFun2<T, P1, P2>> {
    val p1 = GLParameter(null, param1Type, param1Name)
    val p2 = GLParameter(null, param2Type, param2Name)
    return functionX(resultType,
        builder = { builder(p1, p2) },
        factory = { name, deps, body ->
            GLFun2(resultType, name, deps, body, p1, p2)
        }
    )
}

private fun <T : GLType<T>, F> functionX(
    resultType: T,
    builder: GLBlockBuilder<T>.() -> GLExpr<T>,
    factory: (name: String, deps: Set<GLDecl<*, *>>, body: List<String>) -> F
): DelegateProvider<F> =
    DelegateProvider { name ->
        GLBlockBuilder(resultType, name, "\t").run {
            val result = builder()
            using(result)
            +"return $result;"
            build(factory)
        }
    }


