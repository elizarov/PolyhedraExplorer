/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.web.poly

import kotlinx.browser.*
import org.w3c.dom.*
import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.math.abs

fun Polyhedron.exportGeometryToScad(name: String, description: String): String = buildString {
    val exportFaces = fs.flatMap { face ->
        if (face.isPlanar && face.isConvex) {
            listOf(ScadFace(face.fvs.map(Vertex::id), face.kind, face.id))
        } else {
            face.triangles.map { triangle ->
                ScadFace(
                    listOf(face[triangle.c].id, face[triangle.b].id, face[triangle.a].id),
                    face.kind,
                    face.id,
                )
            }
        }
    }
    appendLine("// polyhedron($name[0], $name[1]);")
    appendLine("// $description")
    appendLine()
    appendLine("// Elements of the $name array")
    appendLine("//    0 - vertices coordinates")
    appendLine("//    1 - face descriptions clockwise")
    appendLine("//    2 - vertex kinds")
    appendLine("//    3 - face kinds")
    appendLine("$name = [[")
    for ((i, v) in vs.withIndex()) {
        append("  ${v.toPreciseString()}")
        appendSeparator(i, vs.size)
        appendLine(" // ${v.id} ${v.kind} vertex")
    }
    appendLine("], [")
    for ((i, f) in exportFaces.withIndex()) {
        append(" [${f.vertexIds.joinToString()}]")
        appendSeparator(i, exportFaces.size)
        appendLine(" // ${f.sourceFaceId} ${f.kind} face")
    }
    appendLine("], [")
    appendLine(vs.joinToStringRows("  ") { it.kind.id.toString() })
    appendLine("], [")
    appendLine(exportFaces.joinToStringRows("  ") { it.kind.id.toString() })
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

private data class ScadFace(
    val vertexIds: List<Int>,
    val kind: FaceKind,
    val sourceFaceId: Int,
)

external fun encodeURIComponent(content: String): String

/**
 * Emits a directly renderable OpenSCAD solid while retaining polygonal face and rim regions.
 * Embedded, presentation-closed geometry is one polyhedron. Immersed or opened presentation
 * geometry is a union of individually closed extrusions and leaves the final Boolean to OpenSCAD.
 */
fun Polyhedron.exportSolidToScad(
    name: String,
    description: String,
    exportParams: FaceExportParams,
    hiddenFaceKinds: Set<FaceKind>,
    resolvedRims: List<ResolvedRimGeometry>,
    embeddedBoundary: Boolean,
): String {
    val presentationHiddenKinds = hiddenFaceKinds + nonPlanarFaceKinds
    val closedBoundary = embeddedBoundary && presentationHiddenKinds.isEmpty() && exportParams.expand == 0.0
    return buildString {
        appendLine("// Solid: $name")
        appendLine("// $description")
        appendLine("// Polygonal geometry; OpenSCAD performs final tessellation and Boolean evaluation.")
        appendLine()
        if (closedBoundary) {
            appendClosedPolyhedron(this@exportSolidToScad, exportParams.scale)
        } else {
            require(exportParams.width > 0.0) {
                "OpenSCAD piece-union export requires a positive face width"
            }
            val rimByFace = resolvedRims.associateBy(ResolvedRimGeometry::sourceFaceId)
            if (exportParams.rim > 0.0) {
                val missingRims = fs.filter { face -> face.kind in presentationHiddenKinds && face.id !in rimByFace }
                require(missingRims.isEmpty()) {
                    "OpenSCAD export is waiting for polygonal rim geometry for face(s) " +
                        missingRims.joinToString { face -> face.id.toString() }
                }
            }
            var emittedPieces = 0
            appendLine("union() {")
            for (face in fs) {
                if (face.kind !in presentationHiddenKinds) {
                    val resolved = resolvedFaces[face.id]
                    if (face.isPlanar) {
                        for (cell in resolved.cells) {
                            appendExtrudedRegion(
                                label = "face ${face.id}, cell ${cell.id}",
                                outer = cell.boundary.map { index -> resolved.vertices[index].position },
                                holes = emptyList(),
                                face = face,
                                exportParams = exportParams,
                            )
                            emittedPieces++
                        }
                    } else {
                        for ((index, triangle) in resolved.triangles.withIndex()) {
                            appendExtrudedRegion(
                                label = "non-planar face ${face.id}, triangle $index",
                                outer = listOf(
                                    resolved.vertices[triangle.a].position,
                                    resolved.vertices[triangle.b].position,
                                    resolved.vertices[triangle.c].position,
                                ),
                                holes = emptyList(),
                                face = triangleFace(face, resolved, triangle),
                                exportParams = exportParams,
                            )
                            emittedPieces++
                        }
                    }
                } else if (exportParams.rim > 0.0) {
                    for ((index, region) in rimByFace[face.id]?.regions.orEmpty().withIndex()) {
                        val regionFace = if (region.triangulationPatch) {
                            planarPatchFace(face, region.outer.vertices)
                        } else face
                        appendExtrudedRegion(
                            label = "hidden face ${face.id}, rim $index",
                            outer = region.outer.vertices,
                            holes = region.holes.map(ResolvedRimCycle::vertices),
                            face = regionFace,
                            expansionDirection = face,
                            exportParams = exportParams,
                        )
                        emittedPieces++
                    }
                }
            }
            require(emittedPieces > 0) { "OpenSCAD presentation does not contain any solid pieces" }
            appendLine("}")
        }
    }
}

private fun StringBuilder.appendClosedPolyhedron(poly: Polyhedron, scale: Double) {
    val points = arrayListOf<Vec3>()
    fun pointIndex(point: Vec3): Int {
        val existing = points.indexOfFirst { candidate -> (candidate - point).norm <= 1e-10 * poly.circumradius }
        if (existing >= 0) return existing
        points += point
        return points.lastIndex
    }
    val faces = arrayListOf<List<Int>>()
    for (face in poly.fs) {
        val resolved = poly.resolvedFaces[face.id]
        if (face.isPlanar) {
            for (cell in resolved.cells) {
                faces += cell.boundary.map { index -> pointIndex(resolved.vertices[index].position) }
            }
        } else {
            for (triangle in resolved.triangles) {
                faces += listOf(triangle.c, triangle.b, triangle.a).map { index ->
                    pointIndex(resolved.vertices[index].position)
                }
            }
        }
    }
    appendLine("polyhedron(")
    appendLine("  points = [")
    points.forEachIndexed { index, point ->
        append("    ${(point * scale).toPreciseString()}")
        if (index != points.lastIndex) append(',')
        appendLine()
    }
    appendLine("  ],")
    appendLine("  faces = [")
    faces.forEachIndexed { index, face ->
        append("    [${face.joinToString()}]")
        if (index != faces.lastIndex) append(',')
        appendLine()
    }
    appendLine("  ],")
    appendLine("  convexity = 20")
    appendLine(");")
}

private fun StringBuilder.appendExtrudedRegion(
    label: String,
    outer: List<Vec3>,
    holes: List<List<Vec3>>,
    face: Face,
    expansionDirection: Vec3 = face,
    exportParams: FaceExportParams,
) {
    if (outer.size < 3) return
    val normal = face.unit
    require(normal.norm > 0.0 && face.d.isFinite()) { "OpenSCAD region $label has no finite plane" }
    val axis = if (abs(normal.x) < 0.8) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
    val u = (axis cross normal).unit
    val v = (normal cross u).unit
    val origin = normal * face.d
    val translatedOrigin = (origin + expansionDirection * exportParams.expand) * exportParams.scale
    val depthDirection = if (face.d >= 0.0) -normal else normal
    val extrusionHeight = exportParams.width * exportParams.scale
    require(extrusionHeight > 0.0) { "OpenSCAD region $label has no positive thickness" }
    val cycles = listOf(outer) + holes
    val points = cycles.flatten()
    val paths = arrayListOf<List<Int>>()
    var offset = 0
    for (cycle in cycles) {
        paths += List(cycle.size) { index -> offset + index }
        offset += cycle.size
    }

    appendLine("  // $label")
    appendLine("  multmatrix([")
    appendLine("    [${u.x}, ${v.x}, ${depthDirection.x}, ${translatedOrigin.x}],")
    appendLine("    [${u.y}, ${v.y}, ${depthDirection.y}, ${translatedOrigin.y}],")
    appendLine("    [${u.z}, ${v.z}, ${depthDirection.z}, ${translatedOrigin.z}],")
    appendLine("    [0, 0, 0, 1]")
    appendLine("  ]) linear_extrude(height = $extrusionHeight, convexity = 20)")
    appendLine("    polygon(")
    appendLine("      points = [")
    points.forEachIndexed { index, point ->
        val relative = point - origin
        append("        [${relative * u * exportParams.scale}, ${relative * v * exportParams.scale}]")
        if (index != points.lastIndex) append(',')
        appendLine()
    }
    appendLine("      ],")
    appendLine("      paths = [${paths.joinToString { path -> "[${path.joinToString()}]" }}]")
    appendLine("    );")
}

private fun triangleFace(
    source: Face,
    resolved: ResolvedFaceGeometry,
    triangle: ResolvedFaceTriangle,
): Face {
    val vertices = listOf(triangle.a, triangle.b, triangle.c).mapIndexed { index, resolvedIndex ->
        MutableVertex(index, resolved.vertices[resolvedIndex].position, VertexKind(0))
    }
    return MutableFace(source.id, vertices, source.kind)
}

private fun planarPatchFace(source: Face, points: List<Vec3>): Face {
    val origin = points.first()
    val second = points.first { point -> (point - origin).norm > 1e-12 }
    val third = points.first { point -> ((second - origin) cross (point - origin)).norm > 1e-12 }
    val vertices = listOf(origin, second, third).mapIndexed { index, point ->
        MutableVertex(index, point, VertexKind(0))
    }
    return MutableFace(source.id, vertices, source.kind)
}

