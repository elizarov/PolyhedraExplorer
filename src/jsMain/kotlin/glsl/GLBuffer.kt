/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.glsl

import org.khronos.webgl.*
import polyhedra.common.util.*
import polyhedra.js.util.*
import org.khronos.webgl.WebGLRenderingContext as GL

fun <T : GLType.Floats<T>> createBuffer(gl: GL, type: T): Float32Buffer<T> =
    Float32Buffer(type, gl.createBuffer()!!)

fun GLProgram.createUint8Buffer(): Uint8Buffer = Uint8Buffer(gl.createBuffer()!!)
fun GLProgram.createUint16Buffer(): Uint16Buffer = Uint16Buffer(gl.createBuffer()!!)
fun GLProgram.createUint32Buffer(): Uint32Buffer = Uint32Buffer(gl.createBuffer()!!)

abstract class GLBuffer<T : GLType<T>, D : BufferDataSource>(
    val type: T,
    val glBuffer: WebGLBuffer,
) {
    @Suppress("LeakingThis")
    var data: D = allocate(128)

    protected abstract val capacity: Int
    protected abstract fun allocate(capacity: Int): D

    fun ensureCapacity(length: Int) {
        val capacity = capacity
        val size = length * type.bufferSize
        if (capacity < size) data = allocate(maxOf(size, capacity * 2))
    }
}

fun GLBuffer<*, *>.bindBufferData(gl: GL, target: Int = GL.ARRAY_BUFFER) {
    gl.bindBuffer(target, glBuffer)
    gl.bufferData(target, data, GL.STATIC_DRAW)
}

class Float32Buffer<T : GLType<T>>(type: T, glBuffer: WebGLBuffer) : GLBuffer<T, Float32Array>(type, glBuffer) {
    override val capacity: Int get() = data.length
    override fun allocate(capacity: Int): Float32Array = Float32Array(capacity)
}

class Uint8Buffer(glBuffer: WebGLBuffer) : GLBuffer<GLType.int, Uint8Array>(GLType.int, glBuffer) {
    override val capacity: Int get() = data.length
    override fun allocate(capacity: Int): Uint8Array = Uint8Array(capacity)
}

class Uint16Buffer(glBuffer: WebGLBuffer) : GLBuffer<GLType.int, Uint16Array>(GLType.int, glBuffer) {
    override val capacity: Int get() = data.length
    override fun allocate(capacity: Int): Uint16Array = Uint16Array(capacity)
}

class Uint32Buffer(glBuffer: WebGLBuffer) : GLBuffer<GLType.int, Uint32Array>(GLType.int, glBuffer) {
    override val capacity: Int get() = data.length
    override fun allocate(capacity: Int): Uint32Array = Uint32Array(capacity)
}

operator fun Float32Buffer<GLType.vec3>.set(i: Int, v: Vec3) {
    data[3 * i] = v
}

operator fun Float32Buffer<GLType.vec3>.set(i: Int, c: Color) {
    data.setRGB(3 * i, c)
}

operator fun Uint8Buffer.set(i: Int, x: Int) {
    data[i] = x
}

operator fun Uint16Buffer.set(i: Int, x: Int) {
    data[i] = x
}

operator fun Uint32Buffer.set(i: Int, x: Int) {
    data[i] = x
}
