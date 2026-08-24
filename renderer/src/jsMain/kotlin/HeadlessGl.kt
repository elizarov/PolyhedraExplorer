package polyhedra.renderer

import org.khronos.webgl.WebGLRenderingContext

@JsModule("gl")
@JsNonModule
external fun createHeadlessGl(width: Int, height: Int, options: dynamic): WebGLRenderingContext?

@JsModule("gl")
@JsNonModule
external object HeadlessGlModule {
    val WebGLRenderingContext: dynamic
}

@JsModule("pngjs")
@JsNonModule
external object PngJs {
    val PNG: dynamic
}

@JsModule("node:buffer")
@JsNonModule
external object NodeBuffer {
    val Buffer: dynamic
}

@JsModule("node:fs")
@JsNonModule
external object NodeFs {
    fun mkdirSync(path: String, options: dynamic = definedExternally)
    fun writeFileSync(path: String, data: dynamic)
}

@JsModule("node:path")
@JsNonModule
external object NodePath {
    fun dirname(path: String): String
    fun resolve(path: String): String
}

external val process: dynamic
external val globalThis: dynamic

external fun decodeURIComponent(value: String): String
