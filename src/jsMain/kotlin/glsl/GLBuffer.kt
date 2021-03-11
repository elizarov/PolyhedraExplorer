/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.glsl

import org.khronos.webgl.*
import org.khronos.webgl.WebGLRenderingContext as GL

fun <T : GLType.Floats<T>> createBuffer(gl: GL, type: T): Float32Buffer<T> =
    Float32Buffer(type, gl.createBuffer()!!)

fun <T : GLType.Floats<T>> GLProgram.Attribute<T>.createBuffer(): Float32Buffer<T> =
    Float32Buffer(type, gl.createBuffer()!!)

fun GLProgram.createUint16Buffer(): Uint16Buffer = Uint16Buffer(gl.createBuffer()!!)

abstract class GLBuffer<D : ArrayBufferView>(val glBuffer: WebGLBuffer) {
    var data: D? = null
    abstract fun takeData(length: Int): D
}

class Float32Buffer<T : GLType<T>>(val type: T, glBuffer: WebGLBuffer) : GLBuffer<Float32Array>(glBuffer) {
    override fun takeData(length: Int): Float32Array {
        data?.takeIf { it.length == length }?.let { return it }
        return Float32Array(length).also { data = it }
    }
}

fun Float32Buffer<*>.bindBufferData(gl: GL) {
    gl.bindBuffer(GL.ARRAY_BUFFER, glBuffer)
    gl.bufferData(GL.ARRAY_BUFFER, data as Float32Array, GL.STATIC_DRAW)
}

class Uint16Buffer(glBuffer: WebGLBuffer) : GLBuffer<Uint16Array>(glBuffer) {
    override fun takeData(length: Int): Uint16Array {
        data?.takeIf { it.length == length }?.let { return it }
        return Uint16Array(length).also { data = it }
    }
}
