package polyhedra.js.poly

import kotlinx.browser.*
import org.w3c.dom.*
import polyhedra.common.*
import polyhedra.common.util.*

fun Polyhedron.exportGeometryToScad(name: String): String = buildString {
    appendLine("// polyhedron($name[0], $name[1]);")
    appendLine()
    appendLine("// Elements of the $name array")
    appendLine("//    0 - vertices coordinates")
    appendLine("//    1 - face descriptions clockwise")
    appendLine("//    2 - vertex kinds")
    appendLine("//    3 - face kinds")
    appendLine("$name = [[")
    for ((i, v) in vs.withIndex()) {
        append("  ${v.pt.toPreciseString()}")
        appendSeparator(i, vs.size)
        appendLine(" // ${v.id} ${v.kind} vertex")
    }
    appendLine("], [")
    for ((i, f) in fs.withIndex()) {
        append(" [${f.fvs.joinToString { it.id.toString() }}]")
        appendSeparator(i, fs.size)
        appendLine(" // ${f.id} ${f.kind} face")
    }
    appendLine("], [")
    appendLine(vs.joinToStringRows("  ") { it.kind.id.toString() })
    appendLine("], [")
    appendLine(fs.joinToStringRows("  ") { it.kind.id.toString() })
    appendLine("]];")
}

private fun <T> List<T>.joinToStringRows(prefix: String, transform: (T) -> String): String = buildString {
    for ((i, e) in this@joinToStringRows.withIndex()) {
        val rowStart = i % 20 == 0
        if (i > 0) {
            append(",")
            if (rowStart) appendLine() else append(' ')
        }
        if (rowStart) append(prefix)
        append(transform(e))
    }
}

private fun StringBuilder.appendSeparator(i: Int, size: Int) {
    if (i < size - 1) append(',')
}

fun download(filename: String, content: String) {
    val body = document.body!!
    val node = (document.createElement("a") as HTMLAnchorElement).apply {
        setAttribute("style", "download")
        setAttribute("download", filename)
        setAttribute("href", "data:text/plain;charset=utf-8,${encodeURIComponent(content)}")
    }
    body.appendChild(node)
    node.click()
    body.removeChild(node)

}

external fun encodeURIComponent(content: String): String

