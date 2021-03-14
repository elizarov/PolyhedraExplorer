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

fun GLProgram.createUint8Buffer(): Uint8Buffer = Uint8Buffer(gl.createBuffer()!!)

fun GLProgram.createUint16Buffer(): Uint16Buffer = Uint16Buffer(gl.createBuffer()!!)

abstract class GLBuffer<T : GLType<T>, D : BufferDataSource>(
    val type: T,
    val glBuffer: WebGLBuffer
) {
    var data: D? = null
    abstract fun takeData(length: Int): D
}

class Float32Buffer<T : GLType<T>>(type: T, glBuffer: WebGLBuffer) : GLBuffer<T, Float32Array>(type, glBuffer) {
    override fun takeData(length: Int): Float32Array {
        data?.takeIf { it.length >= length }?.let { return it }
        return Float32Array(length).also { data = it }
    }
}

fun GLBuffer<*, *>.bindBufferData(gl: GL) {
    gl.bindBuffer(GL.ARRAY_BUFFER, glBuffer)
    gl.bufferData(GL.ARRAY_BUFFER, data, GL.STATIC_DRAW)
}

class Uint8Buffer(glBuffer: WebGLBuffer) : GLBuffer<GLType.int, Uint8Array>(GLType.int, glBuffer) {
    override fun takeData(length: Int): Uint8Array {
        data?.takeIf { it.length >= length }?.let { return it }
        return Uint8Array(length).also { data = it }
    }
}

class Uint16Buffer(glBuffer: WebGLBuffer) : GLBuffer<GLType.int, Uint16Array>(GLType.int, glBuffer) {
    override fun takeData(length: Int): Uint16Array {
        data?.takeIf { it.length >= length }?.let { return it }
        return Uint16Array(length).also { data = it }
    }
}
