package polyhedra.js.poly

import polyhedra.common.util.*

fun FaceContext.exportSolidToStl(
    name: String,
    scale: Double,
    faceWidth: Double,
    faceRim: Double,
) = buildString {
    appendLine("solid $name")
    exportTriangles(scale, faceWidth, faceRim) { v1, v2, v3 ->
        val normal = ((v2 - v1) cross (v3 - v1)).unit
        appendLine(normal.toStl("facet normal"))
        appendLine("outer loop")
        appendLine(v1.toStl("vertex"))
        appendLine(v2.toStl("vertex"))
        appendLine(v3.toStl("vertex"))
        appendLine("endloop")
        appendLine("endfacet")
    }
    appendLine("endsolid $name")
}

private fun Vec3.toStl(lbl: String) = "$lbl ${x.fmt} ${y.fmt} ${z.fmt}"