package polyhedra.web.poly

import polyhedra.model.api.CoreStlResponse
import polyhedra.model.api.STL_COORDINATE_PRECISION
import polyhedra.model.poly.*
import polyhedra.model.util.*

fun CoreStlResponse.toAsciiStl(name: String): String {
    require(error == null) { "Cannot serialize an invalid STL response: ${error?.reason}" }
    require(vertices.isNotEmpty() && triangles.isNotEmpty()) { "STL response contains no geometry" }
    return buildString {
        appendLine("solid $name")
        for (triangle in triangles) {
            val a = vertices[triangle.a]
            val b = vertices[triangle.b]
            val c = vertices[triangle.c]
            val normal = ((b - a) cross (c - a))
            val length = normal.norm
            require(length.isFinite() && length > 0.0) { "Validated STL response contains a degenerate triangle" }
            appendLine((normal / length).toStl("facet normal"))
            appendLine("outer loop")
            appendLine(a.toStl("vertex"))
            appendLine(b.toStl("vertex"))
            appendLine(c.toStl("vertex"))
            appendLine("endloop")
            appendLine("endfacet")
        }
        appendLine("endsolid $name")
    }
}

private fun Vec3.toStl(label: String): String =
    "$label ${x.fmt(STL_COORDINATE_PRECISION)} ${y.fmt(STL_COORDINATE_PRECISION)} ${z.fmt(STL_COORDINATE_PRECISION)}"
