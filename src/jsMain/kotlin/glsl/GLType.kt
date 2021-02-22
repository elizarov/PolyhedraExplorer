package polyhedra.js.glsl

import org.khronos.webgl.*

enum class GLPrecision { lowp, mediump, highp }

@Suppress("ClassName")
interface GLType<T : GLType<T>> {
    val bufferSize: Int
    
    interface Numbers<T : Numbers<T>> : GLType<T>

    interface Floats<T : Floats<T>> : Numbers<T>

    interface VecOrMatrixFloats<T : VecOrMatrixFloats<T>> : Floats<T> {
        val uniformFloat32Array: (WebGLRenderingContext, WebGLUniformLocation, Float32Array) -> Unit
    }

    interface NonMatrixFloats<T : NonMatrixFloats<T>> : Floats<T>

    interface VecFloats<T : VecFloats<T>> : VecOrMatrixFloats<T>, NonMatrixFloats<T>

    interface MatrixFloats<T : MatrixFloats<T>> : VecOrMatrixFloats<T>

    object void : GLTypeBase<void>(
        bufferSize = 0
    )

    object bool : GLTypeBase<bool>(
        bufferSize = 1
    )

    object int : Numbers<int>, GLTypeBase<int>(
        bufferSize = 1
    )

    object float : NonMatrixFloats<float>, GLTypeBase<float>(
        bufferSize = 1
    )

    object vec2 : VecFloats<vec2>, GLTypeBase<vec2>(
        bufferSize = 2,
        uniformFloat32Array = { gl, loc, a -> gl.uniform2fv(loc, a) }
    )

    object vec3 : VecFloats<vec3>, GLTypeBase<vec3>(
        bufferSize = 3,
        uniformFloat32Array = { gl, loc, a -> gl.uniform3fv(loc, a) }
    )

    object vec4 : VecFloats<vec4>, GLTypeBase<vec4>(
        bufferSize = 4,
        uniformFloat32Array = { gl, loc, a -> gl.uniform4fv(loc, a) }
    )

    object mat2 : MatrixFloats<mat2>, GLTypeBase<mat2>(
        bufferSize = 4,
        uniformFloat32Array = { gl, loc, a -> gl.uniformMatrix2fv(loc, false, a) }
    )

    object mat3 : MatrixFloats<mat3>, GLTypeBase<mat3>(
        bufferSize = 9,
        uniformFloat32Array = { gl, loc, a -> gl.uniformMatrix3fv(loc, false, a) }
    )

    object mat4 : MatrixFloats<mat4>, GLTypeBase<mat4>(
        bufferSize = 16,
        uniformFloat32Array = { gl, loc, a -> gl.uniformMatrix4fv(loc, false, a) }
    )
}

abstract class GLTypeBase<T : GLType<T>>(
    override val bufferSize: Int,
    val uniformFloat32Array: (WebGLRenderingContext, WebGLUniformLocation, Float32Array) -> Unit =
        { _, _, _ -> error("cannot be set from Float32Array") }
) : GLType<T> {
    override fun toString(): String = this::class.simpleName!!
}