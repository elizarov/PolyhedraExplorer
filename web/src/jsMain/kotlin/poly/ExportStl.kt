package polyhedra.js.poly

import polyhedra.common.poly.*
import polyhedra.common.util.*
import kotlin.math.*

fun FaceContext.exportSolidToStl(name: String, description: String, exportParams: FaceExportParams): String {
    val q = poly.rotationWithLargestFaceDown()
    val ofs = MutableVec3(0.0, 0.0, Double.POSITIVE_INFINITY)
    exportVertices(exportParams) { av ->
        val v = av.rotated(q)
        ofs.z = min(ofs.z, v.z)
    }
    return buildString {
        appendLine("solid $name ; $description")
        val normal = MutableVec3()
        exportTriangles(exportParams) { av1, av2, av3 ->
            val v1 = av1.rotated(q)
            val v2 = av2.rotated(q)
            val v3 = av3.rotated(q)
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

private fun Polyhedron.rotationWithLargestFaceDown(): Quat {
    val f = faceKinds.values.maxByOrNull { it.essence().area() }!!
    return rotationBetweenQuat(f, Vec3(0.0, 0.0, -1.0))
}