/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

@file:JsModule("gl-matrix")
@file:JsNonModule
package polyhedra.js.util

import org.khronos.webgl.*

external object quat {
    fun create(): quat_t
    fun setAxisAngle(out: quat_t, axis: Float32Array, rad: Number): quat_t
    fun multiply(out: quat_t, a: quat_t, b: quat_t): quat_t
    fun conjugate(out: quat_t, a: quat_t): quat_t
}

external object mat3 {
    fun create(): Float32Array

    fun fromQuat(out: Float32Array, q: quat_t): Float32Array
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
    fun fromRotationTranslation(out: Float32Array, q: quat_t, v: Float32Array): Float32Array
    fun fromRotationTranslationScale(out: Float32Array, q: quat_t, v: Float32Array, s: Float32Array): Float32Array
}


