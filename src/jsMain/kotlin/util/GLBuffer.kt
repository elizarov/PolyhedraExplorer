package polyhedra.js.util

import org.khronos.webgl.*

fun <T : GLType<T, U>, U : Float32Array> GLProgram.Attribute<T, U>.createBuffer(): Float32Buffer<T> =
    Float32Buffer(type, gl.createBuffer()!!)

fun GLProgram.createUint16Buffer(): Uint16Buffer = Uint16Buffer(gl.createBuffer()!!)

abstract class GLBuffer<D : ArrayBufferView>(val glBuffer: WebGLBuffer) {
    protected var data: D? = null
    abstract fun takeData(length: Int): D
}

class Float32Buffer<T : GLType<T, *>>(val type: T, glBuffer: WebGLBuffer) : GLBuffer<Float32Array>(glBuffer) {
    override fun takeData(length: Int): Float32Array {
        data?.takeIf { it.length == length }?.let { return it }
        return Float32Array(length).also { data = it }
    }
}

class Uint16Buffer(glBuffer: WebGLBuffer) : GLBuffer<Uint16Array>(glBuffer) {
    override fun takeData(length: Int): Uint16Array {
        data?.takeIf { it.length == length }?.let { return it }
        return Uint16Array(length).also { data = it }
    }
}
