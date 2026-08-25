package polyhedra.core.debug

import kotlinx.coroutines.runBlocking
import polyhedra.core.api.formatCoreProgress
import polyhedra.core.api.inspectCompactConfiguration
import polyhedra.model.api.CoreProgress

private class ConsoleProgress(private val transformTags: List<String>) {
    private var visible = false
    private var previousLength = 0
    private var previousLine: String? = null

    fun update(progress: CoreProgress) {
        val line = formatCoreProgress(progress, transformTags)
        if (line == previousLine) return
        val padding = " ".repeat((previousLength - line.length).coerceAtLeast(0))
        System.out.print("\r$line$padding")
        System.out.flush()
        previousLength = line.length
        previousLine = line
        visible = true
    }

    fun finish() {
        if (visible) println()
        visible = false
        previousLength = 0
        previousLine = null
    }
}

fun main(args: Array<String>) = runBlocking {
    require(args.size == 1) {
        "Usage: inspect-config <serialized-configuration>"
    }
    val tags = polyhedra.core.api.parseCompactCoreConfiguration(args.single()).state.transformTags
    val progress = ConsoleProgress(tags)
    try {
        val inspection = inspectCompactConfiguration(args.single(), reportProgress = progress::update)
        progress.finish()
        println(inspection.report)
    } catch (cause: Throwable) {
        progress.finish()
        throw cause
    }
}
