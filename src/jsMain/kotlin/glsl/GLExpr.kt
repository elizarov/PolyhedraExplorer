/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.glsl

interface GLExpr<T : GLType<T>> {
    val type: T
    fun visitDecls(visitor: (GLDecl<*, *>) -> Unit)
}

private class BinaryOp<T : GLType<T>>(
    override val type: T,
    val a: GLExpr<*>, val op: String, val b: GLExpr<*>
) : GLExpr<T> {
    override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) {
        a.visitDecls(visitor)
        b.visitDecls(visitor)
    }

    override fun toString(): String = "($a $op $b)"
}

private class Call<T : GLType<T>>(
    override val type: T,
    val name: String, vararg val a: GLExpr<*>
) : GLExpr<T> {
    override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) {
        a.forEach { it.visitDecls(visitor) }
    }

    override fun toString(): String = "$name(${a.joinToString(", ")})"
}

private class Literal<T : GLType<T>>(
    override val type: T,
    val s: String
): GLExpr<T> {
    override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) {}
    override fun toString(): String = s
}

private class DotCall<T : GLType<T>>(
    override val type: T,
    val a: GLExpr<*>, val name: String
) : GLExpr<T> {
    override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) { a.visitDecls(visitor) }
    override fun toString(): String = "$a.$name"
}

val Boolean.literal: GLExpr<GLType.bool>
    get() = Literal(GLType.bool, toString())

val Int.literal: GLExpr<GLType.int>
    get() = Literal(GLType.int, toString())

val Double.literal: GLExpr<GLType.float>
    get() {
        val s0 = toString()
        val s = if (s0.contains('.')) s0 else "$s0.0"
        return Literal(GLType.float, s)
    }

// basic algebraic operations on any numbers, vectors, matrices
operator fun <T : GLType.Numbers<T>> GLExpr<T>.plus(other: GLExpr<T>): GLExpr<T> = BinaryOp(type, this, "+", other)
operator fun <T : GLType.Numbers<T>> GLExpr<T>.minus(other: GLExpr<T>): GLExpr<T> = BinaryOp(type, this, "-", other)
operator fun <T : GLType.Numbers<T>> GLExpr<T>.times(other: GLExpr<T>): GLExpr<T> = BinaryOp(type, this, "*", other)
operator fun <T : GLType.Numbers<T>> GLExpr<T>.div(other: GLExpr<T>): GLExpr<T> = BinaryOp(type, this, "/", other)

// matrix or vector with float
operator fun <T : GLType.VecOrMatrixFloats<T>> GLExpr<T>.times(other: GLExpr<GLType.float>): GLExpr<T> = BinaryOp(type, this, "*", other)
operator fun <T : GLType.VecOrMatrixFloats<T>> GLExpr<GLType.float>.times(other: GLExpr<T>): GLExpr<T> = BinaryOp(other.type, this, "*", other)
operator fun <T : GLType.VecOrMatrixFloats<T>> GLExpr<T>.times(other: Double): GLExpr<T> = this * other.literal
operator fun <T : GLType.VecOrMatrixFloats<T>> Double.times(other: GLExpr<T>): GLExpr<T> = literal * other

operator fun <T : GLType.VecOrMatrixFloats<T>> GLExpr<T>.div(other: GLExpr<GLType.float>): GLExpr<T> = BinaryOp(type, this, "/", other)
operator fun <T : GLType.VecOrMatrixFloats<T>> GLExpr<T>.div(other: Double): GLExpr<T> = div(other.literal)

// matrix vector multiplication
operator fun GLExpr<GLType.mat2>.times(other: GLExpr<GLType.vec2>): GLExpr<GLType.vec2> = BinaryOp(GLType.vec2, this, "*", other)
operator fun GLExpr<GLType.mat3>.times(other: GLExpr<GLType.vec3>): GLExpr<GLType.vec3> = BinaryOp(GLType.vec3, this, "*", other)
operator fun GLExpr<GLType.mat4>.times(other: GLExpr<GLType.vec4>): GLExpr<GLType.vec4> = BinaryOp(GLType.vec4, this, "*", other)

// vector binary funs
fun <T : GLType.VecFloats<T>> dot(a: GLExpr<T>, b: GLExpr<T>): GLExpr<GLType.float> = Call(GLType.float, "dot", a, b)
fun <T : GLType.VecFloats<T>> cross(a: GLExpr<T>, b: GLExpr<T>): GLExpr<T> = Call(a.type, "cross", a, b)

// vector unary funs
fun <T : GLType.VecFloats<T>> normalize(a: GLExpr<T>): GLExpr<T> = Call(a.type, "normalize", a)

// unary component-wise funs on floats and vecs
fun <T : GLType.NonMatrixFloats<T>> abs(a: GLExpr<T>): GLExpr<T> = Call(a.type, "abs", a)
fun <T : GLType.NonMatrixFloats<T>> sign(a: GLExpr<T>): GLExpr<T> = Call(a.type, "sign", a)
fun <T : GLType.NonMatrixFloats<T>> floor(a: GLExpr<T>): GLExpr<T> = Call(a.type, "floor", a)
fun <T : GLType.NonMatrixFloats<T>> ceil(a: GLExpr<T>,): GLExpr<T> = Call(a.type, "ceil", a)
fun <T : GLType.NonMatrixFloats<T>> fract(a: GLExpr<T>): GLExpr<T> = Call(a.type, "fract", a) // x - floor(x)
fun <T : GLType.NonMatrixFloats<T>> exp(a: GLExpr<T>): GLExpr<T> = Call(a.type, "exp", a)
fun <T : GLType.NonMatrixFloats<T>> log(a: GLExpr<T>): GLExpr<T> = Call(a.type, "log", a)
fun <T : GLType.NonMatrixFloats<T>> exp2(a: GLExpr<T>): GLExpr<T> = Call(a.type, "exp2", a)
fun <T : GLType.NonMatrixFloats<T>> log2(a: GLExpr<T>): GLExpr<T> = Call(a.type, "log2", a)
fun <T : GLType.NonMatrixFloats<T>> sqrt(a: GLExpr<T>): GLExpr<T> = Call(a.type, "sqrt", a)
fun <T : GLType.NonMatrixFloats<T>> invsersesqrt(a: GLExpr<T>): GLExpr<T> = Call(a.type, "invsersesqrt", a)
fun <T : GLType.NonMatrixFloats<T>> radians(a: GLExpr<T>): GLExpr<T> = Call(a.type, "radians", a)
fun <T : GLType.NonMatrixFloats<T>> degrees(a: GLExpr<T>): GLExpr<T> = Call(a.type, "degrees", a)
fun <T : GLType.NonMatrixFloats<T>> sin(a: GLExpr<T>): GLExpr<T> = Call(a.type, "sin", a)
fun <T : GLType.NonMatrixFloats<T>> cos(a: GLExpr<T>): GLExpr<T> = Call(a.type, "cos", a)
fun <T : GLType.NonMatrixFloats<T>> tan(a: GLExpr<T>): GLExpr<T> = Call(a.type, "tan", a)
fun <T : GLType.NonMatrixFloats<T>> asin(a: GLExpr<T>): GLExpr<T> = Call(a.type, "asin", a)
fun <T : GLType.NonMatrixFloats<T>> acos(a: GLExpr<T>): GLExpr<T> = Call(a.type, "acos", a)
fun <T : GLType.NonMatrixFloats<T>> atan(a: GLExpr<T>): GLExpr<T> = Call(a.type, "atan", a)

// binary component-wise funs on floats and vecs
fun <T : GLType.NonMatrixFloats<T>> atan(a: GLExpr<T>, b: GLExpr<T>): GLExpr<T> = Call(a.type, "atan", a, b)
fun <T : GLType.NonMatrixFloats<T>> step(a: GLExpr<T>, b: GLExpr<T>): GLExpr<T> = Call(a.type, "step", a, b)

//
fun <T : GLType.VecFloats<T>> pow(a: GLExpr<T>, b: GLExpr<T>): GLExpr<T> = Call(a.type, "pow", a, b)
fun <T : GLType.VecFloats<T>> mod(a: GLExpr<T>, b: GLExpr<T>): GLExpr<T> = Call(a.type, "mod", a, b)
fun <T : GLType.VecFloats<T>> min(a: GLExpr<T>, b: GLExpr<T>): GLExpr<T> = Call(a.type, "min", a, b)
fun <T : GLType.VecFloats<T>> max(a: GLExpr<T>, b: GLExpr<T>): GLExpr<T> = Call(a.type, "max", a, b)

// binary component-wise funs on float vec, float pair
fun <T : GLType.NonMatrixFloats<T>> pow(a: GLExpr<T>, b: GLExpr<GLType.float>): GLExpr<T> = Call(a.type, "pow", a, b)
fun <T : GLType.NonMatrixFloats<T>> mod(a: GLExpr<T>, b: GLExpr<GLType.float>): GLExpr<T> = Call(a.type, "mod", a, b)
fun <T : GLType.NonMatrixFloats<T>> min(a: GLExpr<T>, b: GLExpr<GLType.float>): GLExpr<T> = Call(a.type, "min", a, b)
fun <T : GLType.NonMatrixFloats<T>> max(a: GLExpr<T>, b: GLExpr<GLType.float>): GLExpr<T> = Call(a.type, "max", a, b)

// binary component-wise funs on float vec, float pair (literal version)
fun <T : GLType.NonMatrixFloats<T>> pow(a: GLExpr<T>, b: Double): GLExpr<T> = pow(a, b.literal)
fun <T : GLType.NonMatrixFloats<T>> mod(a: GLExpr<T>, b: Double): GLExpr<T> = mod(a, b.literal)
fun <T : GLType.NonMatrixFloats<T>> min(a: GLExpr<T>, b: Double): GLExpr<T> = min(a, b.literal)
fun <T : GLType.NonMatrixFloats<T>> max(a: GLExpr<T>, b: Double): GLExpr<T> = max(a, b.literal)

// conversions & constructors
fun vec4(xyz: GLExpr<GLType.vec3>, w: GLExpr<GLType.float>): GLExpr<GLType.vec4> = Call(GLType.vec4, "vec4", xyz, w)
fun vec4(xyz: GLExpr<GLType.vec3>, w: Double): GLExpr<GLType.vec4> = vec4(xyz, w.literal)
fun vec4(x: GLExpr<GLType.float>, y: GLExpr<GLType.float>, z: GLExpr<GLType.float>, w: GLExpr<GLType.float>): GLExpr<GLType.vec4> =
    Call(GLType.vec4, "vec4", x, y, z, w)
fun vec4(x: Double, y: Double, z: Double, w: Double): GLExpr<GLType.vec4> =
    vec4(x.literal, y.literal, z.literal, w.literal)

// swizzling
val GLExpr<GLType.vec4>.x: GLExpr<GLType.float> get() = DotCall(GLType.float, this, "x")
val GLExpr<GLType.vec4>.y: GLExpr<GLType.float> get() = DotCall(GLType.float, this, "y")
val GLExpr<GLType.vec4>.z: GLExpr<GLType.float> get() = DotCall(GLType.float, this, "z")
val GLExpr<GLType.vec4>.w: GLExpr<GLType.float> get() = DotCall(GLType.float, this, "w")
val GLExpr<GLType.vec4>.xyz: GLExpr<GLType.vec3> get() = DotCall(GLType.vec3, this, "xyz")
val GLExpr<GLType.vec4>.rgb: GLExpr<GLType.vec3> get() = DotCall(GLType.vec3, this, "rgb")
val GLExpr<GLType.vec4>.a: GLExpr<GLType.float> get() = DotCall(GLType.float, this, "a")

// comparisons
infix fun <T : GLType.Comparable<T>> GLExpr<T>.eq(other: GLExpr<T>): GLExpr<GLType.bool> = BinaryOp(GLType.bool, this, "==", other)
infix fun <T : GLType.Comparable<T>> GLExpr<T>.ne(other: GLExpr<T>): GLExpr<GLType.bool> = BinaryOp(GLType.bool, this, "!=", other)
infix fun <T : GLType.Comparable<T>> GLExpr<T>.lt(other: GLExpr<T>): GLExpr<GLType.bool> = BinaryOp(GLType.bool, this, "<", other)
infix fun <T : GLType.Comparable<T>> GLExpr<T>.gt(other: GLExpr<T>): GLExpr<GLType.bool> = BinaryOp(GLType.bool, this, ">", other)
infix fun <T : GLType.Comparable<T>> GLExpr<T>.le(other: GLExpr<T>): GLExpr<GLType.bool> = BinaryOp(GLType.bool, this, "<=", other)
infix fun <T : GLType.Comparable<T>> GLExpr<T>.ge(other: GLExpr<T>): GLExpr<GLType.bool> = BinaryOp(GLType.bool, this, ">=", other)

// boolean operators
infix fun GLExpr<GLType.bool>.and(other: GLExpr<GLType.bool>): GLExpr<GLType.bool> = BinaryOp(GLType.bool, this, "&&", other)
infix fun GLExpr<GLType.bool>.or(other: GLExpr<GLType.bool>): GLExpr<GLType.bool> = BinaryOp(GLType.bool, this, "||", other)
infix fun GLExpr<GLType.bool>.xor(other: GLExpr<GLType.bool>): GLExpr<GLType.bool> = BinaryOp(GLType.bool, this, "^^", other)

private class SelectOp<T : GLType<T>>(
    override val type: T,
    val condition: GLExpr<GLType.bool>,
    val ifTrue: GLExpr<T>,
    val ifFalse: GLExpr<T>
) : GLExpr<T> {
    override fun visitDecls(visitor: (GLDecl<*, *>) -> Unit) {
        condition.visitDecls(visitor)
        ifTrue.visitDecls(visitor)
        ifFalse.visitDecls(visitor)
    }
    override fun toString(): String = "($condition ? $ifTrue : $ifFalse)"
}

fun <T : GLType<T>> select(condition: GLExpr<GLType.bool>, ifTrue: GLExpr<T>, ifFalse: GLExpr<T>): GLExpr<T> =
    SelectOp(ifTrue.type, condition, ifTrue, ifFalse)