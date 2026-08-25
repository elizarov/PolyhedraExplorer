package polyhedra.renderer

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.WebGLRenderingContext
import polyhedra.core.api.CoreInspection
import polyhedra.core.api.formatCoreProgress
import polyhedra.core.api.inspectCompactConfiguration
import polyhedra.model.api.CoreProgress
import polyhedra.web.glsl.set
import polyhedra.web.main.RootParams
import polyhedra.web.params.Param
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.DrawContext
import polyhedra.web.poly.drawScene

private const val PAGE_BACKGROUND = 0.95f

data class RenderedImage(
    val width: Int,
    val height: Int,
    val rgba: Uint8Array,
)

suspend fun renderConfiguration(configuration: String, width: Int, height: Int): RenderedImage {
    val inspection = inspectCompactConfiguration(
        configuration,
        calculateTweakRanges = false,
        detectSeed = false,
    )
    return renderConfiguration(inspection, width, height)
}

private fun renderConfiguration(inspection: CoreInspection, width: Int, height: Int): RenderedImage {
    require(width > 0) { "Width must be positive" }
    require(height > 0) { "Height must be positive" }

    val serialized = inspection.configuration.normalized
    val params = RootParams()
    params.loadFromString(serialized)
    params.render.poly.applyCoreResponse(inspection.configuration.state, inspection.response)

    val gl = createContext(width, height)
    val draw = DrawContext(gl, params.render) {}
    try {
        params.performUpdate(null, 0.0)
        gl.clearColor(PAGE_BACKGROUND, PAGE_BACKGROUND, PAGE_BACKGROUND, 1.0f)
        draw.drawScene(width, height)
        val pixels = Uint8Array(width * height * 4)
        gl.readPixels(
            0,
            0,
            width,
            height,
            WebGLRenderingContext.RGBA,
            WebGLRenderingContext.UNSIGNED_BYTE,
            pixels,
        )
        return RenderedImage(width, height, flipVertically(pixels, width, height))
    } finally {
        draw.destroy()
        params.destroy()
        destroyContext(gl)
    }
}

fun writePng(image: RenderedImage, output: String): String {
    val outputPath = NodePath.resolve(output)
    NodeFs.mkdirSync(NodePath.dirname(outputPath), js("({ recursive: true })"))
    val options = js("({})")
    options.width = image.width
    options.height = image.height
    val png = js("Reflect.construct")(PngJs.PNG, arrayOf(options))
    png.data = NodeBuffer.Buffer.from(image.rgba)
    NodeFs.writeFileSync(outputPath, PngJs.PNG.sync.write(png))
    return outputPath
}

private fun createContext(width: Int, height: Int): WebGLRenderingContext {
    val options = js("({})")
    options.alpha = true
    options.depth = true
    options.stencil = true
    options.antialias = true
    options.premultipliedAlpha = false
    options.preserveDrawingBuffer = true
    val gl = requireNotNull(createHeadlessGl(width, height, options)) {
        "headless-gl could not create a ${width}x$height WebGL context"
    }
    // Kotlin's DOM externals emit browser-style static constant reads and instanceof checks.
    globalThis.WebGLRenderingContext = HeadlessGlModule.WebGLRenderingContext
    requireNotNull(gl.getExtension("OES_element_index_uint")) {
        "headless-gl does not provide OES_element_index_uint"
    }
    return gl
}

private fun destroyContext(gl: WebGLRenderingContext) {
    val extension: dynamic = gl.getExtension("STACKGL_destroy_context")
    if (extension != null) extension.destroy()
}

private fun flipVertically(source: Uint8Array, width: Int, height: Int): Uint8Array {
    val stride = width * 4
    val target = Uint8Array(source.length)
    for (y in 0 until height) {
        val sourceOffset = y * stride
        val targetOffset = (height - 1 - y) * stride
        target.set(source.subarray(sourceOffset, sourceOffset + stride), targetOffset)
    }
    return target
}

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    GlobalScope.promise {
        runCommandLine()
    }.catch { error ->
        process.stderr.write("${error.asDynamic().stack ?: error}\n")
        process.exitCode = 1
    }
}

private suspend fun runCommandLine() {
    val args = process.argv.unsafeCast<Array<String>>().drop(2)
    require(args.size == 4) {
        "Usage: render-config <configuration> <output.png> <width> <height>"
    }
    val configuration = args[0]
    val transformTags = polyhedra.core.api.parseCompactCoreConfiguration(configuration).state.transformTags
    val progress = NodeConsoleProgress(transformTags)
    val inspection = try {
        inspectCompactConfiguration(
            configuration,
            calculateTweakRanges = false,
            detectSeed = false,
            reportProgress = progress::update,
        )
    } finally {
        progress.finish()
    }
    println(inspection.report)
    val image = renderConfiguration(
        inspection = inspection,
        width = args[2].toInt(),
        height = args[3].toInt(),
    )
    val output = writePng(image, args[1])
    println("Rendered $output (${image.width}x${image.height})")
}

private class NodeConsoleProgress(private val transformTags: List<String>) {
    private var visible = false
    private var previousLength = 0
    private var previousLine: String? = null

    fun update(progress: CoreProgress) {
        val line = formatCoreProgress(progress, transformTags)
        if (line == previousLine) return
        val padding = " ".repeat((previousLength - line.length).coerceAtLeast(0))
        process.stdout.write("\r$line$padding")
        previousLength = line.length
        previousLine = line
        visible = true
    }

    fun finish() {
        if (visible) process.stdout.write("\n")
        visible = false
        previousLength = 0
        previousLine = null
    }
}
