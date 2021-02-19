@file:JsModule("gl-matrix")
@file:JsNonModule
package polyhedra.js.util

import kotlin.js.*
import org.khronos.webgl.*

external class quat {
    companion object {
        fun create(): quat
        fun setAxisAngle(out: quat, axis: Float32Array, rad: Number): quat
        fun multiply(out: quat, a: quat, b: quat): quat
    }
}

external object mat3 {
    fun create(): Float32Array

    fun fromQuat(out: Float32Array, q: quat): Float32Array
    fun invert(out: Float32Array, a: Float32Array): Float32Array
    fun transpose(out: Float32Array, a: Float32Array): Float32Array
}

external object mat4 {
    fun create(): Float32Array

    fun identity(out: Float32Array): Float32Array
    fun invert(out: Float32Array, a: Float32Array): Float32Array
    fun transpose(out: Float32Array, a: Float32Array): Float32Array
    fun translate(out: Float32Array, a: Float32Array, v: Float32Array): Float32Array
    fun rotate(out: Float32Array, a: Float32Array, rad: Number, axis: Float32Array): Float32Array
    fun rotateX(out: Float32Array, a: Float32Array, rad: Number)
    fun rotateY(out: Float32Array, a: Float32Array, rad: Number)
    fun rotateZ(out: Float32Array, a: Float32Array, rad: Number)
    fun perspective(out: Float32Array, fovy: Number, aspect: Number, near: Number, far: Number): Float32Array
    fun scale(out: Float32Array, a: Float32Array, v: Float32Array): Float32Array

    fun fromTranslation(out: Float32Array, v: Float32Array): Float32Array
    fun fromRotationTranslation(out: Float32Array, q: quat, v: Float32Array): Float32Array
    fun fromRotationTranslationScale(out: Float32Array, q: quat, v: Float32Array, s: Float32Array): Float32Array
}


