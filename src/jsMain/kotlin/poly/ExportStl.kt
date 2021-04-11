package polyhedra.js.poly

import polyhedra.common.util.*
import kotlin.math.*

fun FaceContext.exportSolidToStl(name: String, description: String, exportParams: FaceExportParams): String {
    val ofs = MutableVec3(0.0, 0.0, Double.POSITIVE_INFINITY)
    exportVertices(exportParams) { v ->
        ofs.z = min(ofs.z, v.z)
    }
    return buildString {
        appendLine("solid $name ; $description")
        val normal = MutableVec3()
        exportTriangles(exportParams) { v1, v2, v3 ->
            normal.setToZero()
            crossCenteredAddTo(normal, v1, v2, v3)
            normal /= normal.norm
            appendLine(normal.toStl("facet normal"))
            appendLine("outer loop")
            appendLine((v1 - ofs).toStl("vertex"))
            appendLine((v2 - ofs).toStl("vertex"))
            appendLine((v3 - ofs).toStl("vertex"))
            appendLine("endloop")
            appendLine("endfacet")
        }
        appendLine("endsolid $name")
    }
}

private fun Vec3.toStl(lbl: String) = "$lbl ${x.fmt} ${y.fmt} ${z.fmt}"