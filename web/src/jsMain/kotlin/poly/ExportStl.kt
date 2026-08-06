package polyhedra.web.poly

import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.math.*

private const val STL_PRECISION = 4
private const val STL_MIN_NORMAL_LENGTH = 1e-9

fun FaceContext.exportSolidToStl(name: String, exportParams: FaceExportParams): String {
    val q = poly.rotationWithLargestFaceDown()
    val ofs = MutableVec3(0.0, 0.0, Double.POSITIVE_INFINITY)
    exportVertices(exportParams) { av ->
        val v = av.rotated(q)
        ofs.z = min(ofs.z, v.z)
    }
    return buildString {
        appendLine("solid $name")
        val normal = MutableVec3()
        exportTriangles(exportParams) { av1, av2, av3 ->
            val v1 = (av1.rotated(q) - ofs).roundedForStl()
            val v2 = (av2.rotated(q) - ofs).roundedForStl()
            val v3 = (av3.rotated(q) - ofs).roundedForStl()
            normal.setToZero()
            crossCenteredAddTo(normal, v1, v2, v3)
            val normalLength = normal.norm
            if (normalLength <= STL_MIN_NORMAL_LENGTH || !normalLength.isFinite()) return@exportTriangles
            normal /= normalLength
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
}

private fun Vec3.roundedForStl(): Vec3 {
    val factor = 10.0.pow(STL_PRECISION)
    fun Double.rounded() = round(this * factor) / factor
    return Vec3(x.rounded(), y.rounded(), z.rounded())
}

private fun Vec3.toStl(lbl: String) =
    "$lbl ${x.fmt(STL_PRECISION)} ${y.fmt(STL_PRECISION)} ${z.fmt(STL_PRECISION)}"

private fun Polyhedron.rotationWithLargestFaceDown(): Quat {
    val f = faceKinds.values.maxByOrNull { it.essence().area() }!!
    return rotationBetweenQuat(f, Vec3(0.0, 0.0, -1.0))
}
