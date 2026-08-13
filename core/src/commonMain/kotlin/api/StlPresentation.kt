package polyhedra.core.api

import polyhedra.core.poly.resolvedRims
import polyhedra.core.transform.resolved
import polyhedra.core.util.OperationProgressContext
import polyhedra.model.api.CoreStlPresentation
import polyhedra.model.api.CoreStlRequest
import polyhedra.model.api.CoreStlTriangle
import polyhedra.model.poly.Face
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.ResolvedRimCycle
import polyhedra.model.poly.area
import polyhedra.model.poly.essence
import polyhedra.model.poly.get
import polyhedra.model.poly.size
import polyhedra.model.poly.triangulatePlanarPolygon
import polyhedra.model.util.MutableVec3
import polyhedra.model.util.Quat
import polyhedra.model.util.Vec3
import polyhedra.model.util.plus
import polyhedra.model.util.rotated
import polyhedra.model.util.rotationBetweenQuat
import polyhedra.model.util.times

/** Builds presentation solids from authoritative polygonal geometry before STL arrangement. */
internal suspend fun CoreStlPresentation.toTriangleRequest(
    reportProgress: (Int) -> Unit,
): CoreStlRequest {
    require(scale.isFinite() && scale > 0.0) { "STL scale must be finite and positive" }
    require(width.isFinite() && width >= 0.0) { "STL width must be finite and non-negative" }
    require(rim.isFinite() && rim >= 0.0) { "STL rim must be finite and non-negative" }
    require(expand.isFinite() && expand >= 0.0) { "STL expansion must be finite and non-negative" }

    val source = poly
    val physical = source.resolved(OperationProgressContext { progress -> reportProgress(progress / 5) })
    val hiddenKinds = hiddenFaceKinds.toSet()
    val hiddenPhysicalFaces = physical.fs.filterTo(linkedSetOf()) { face ->
        physical.sourceFaceKinds(face, source, physical !== source).any(hiddenKinds::contains)
    }
    val rimBySourceFace = if (rim > 0.0 && hiddenKinds.isNotEmpty()) {
        source.resolvedRims(rim).associateBy { geometry -> geometry.sourceFaceId }
    } else {
        emptyMap()
    }
    val rotation = physical.rotationWithLargestFaceDown()
    val builder = PresentationMeshBuilder(this, rotation, physical.circumradius)
    val hasHiddenFaces = hiddenPhysicalFaces.isNotEmpty()

    physical.fs.forEachIndexed { index, face ->
        if (face !in hiddenPhysicalFaces) {
            builder.addFace(face, inner = false)
            if (hasHiddenFaces || expand > 0.0) builder.addFace(face, inner = true)
        } else if (rim <= 0.0 && width > 0.0) {
            builder.addFaceBoundaryWall(face)
        }
        if (expand > 0.0 && width > 0.0) builder.addFaceBoundaryWall(face)
        reportProgress(20 + 5 * (index + 1) / physical.fs.size)
    }
    if (rim > 0.0) for (face in source.fs.filter { candidate -> candidate.kind in hiddenKinds }) {
        val geometry = rimBySourceFace[face.id] ?: continue
        for (region in geometry.regions) {
            builder.addRimCycle(region.outer, face, inner = false, hole = false)
            region.holes.forEach { hole -> builder.addRimCycle(hole, face, inner = false, hole = true) }
            builder.addRimCycle(region.outer, face, inner = true, hole = false)
            region.holes.forEach { hole -> builder.addRimCycle(hole, face, inner = true, hole = true) }
            if (width > 0.0) {
                builder.addRimWall(region.outer, face)
                region.holes.forEach { hole -> builder.addRimWall(hole, face) }
            }
        }
    }
    return builder.request()
}

private fun Polyhedron.sourceFaceKinds(
    face: Face,
    source: Polyhedron,
    useProvenance: Boolean,
): Set<FaceKind> {
    if (!useProvenance) return setOf(face.kind)
    val sourceIds = resolvedTopologyProvenance?.faces?.get(face.id)?.sourceFaceIds
    return if (sourceIds.isNullOrEmpty()) {
        setOf(face.kind)
    } else {
        sourceIds.mapTo(linkedSetOf()) { sourceId -> source.fs[sourceId].kind }
    }
}

private fun Polyhedron.rotationWithLargestFaceDown(): Quat {
    val face = fs.maxBy { candidate -> candidate.essence().area() }
    return rotationBetweenQuat(face, Vec3(0.0, 0.0, -1.0))
}

private class PresentationMeshBuilder(
    private val settings: CoreStlPresentation,
    private val rotation: Quat,
    circumradius: Double,
) {
    private val vertices = arrayListOf<MutableVec3>()
    private val vertexIds = linkedMapOf<Triple<Double, Double, Double>, Int>()
    private val triangles = arrayListOf<CoreStlTriangle>()
    private var nextSurface = 0
    private val innerScale = (1.0 - settings.width / circumradius).coerceAtLeast(0.0)

    private fun transformed(point: Vec3, face: Face, inner: Boolean): Vec3 {
        val inset = if (inner) point * innerScale else point
        return ((inset + face * settings.expand) * settings.scale).rotated(rotation)
    }

    private fun vertex(point: Vec3): Int {
        val key = Triple(point.x, point.y, point.z)
        return vertexIds.getOrPut(key) {
            vertices += MutableVec3(point)
            vertices.lastIndex
        }
    }

    private fun triangle(a: Vec3, b: Vec3, c: Vec3, surface: Int, reverse: Boolean = false) {
        val ids = if (reverse) listOf(vertex(a), vertex(c), vertex(b)) else listOf(vertex(a), vertex(b), vertex(c))
        if (ids.toSet().size == 3) triangles += CoreStlTriangle(ids[0], ids[1], ids[2], surface)
    }

    fun addFace(face: Face, inner: Boolean) {
        val surface = nextSurface++
        for (triangle in face.triangles) {
            triangle(
                transformed(face[triangle.a], face, inner),
                transformed(face[triangle.b], face, inner),
                transformed(face[triangle.c], face, inner),
                surface,
                reverse = inner,
            )
        }
    }

    fun addRimCycle(cycle: ResolvedRimCycle, face: Face, inner: Boolean, hole: Boolean) {
        val points = cycle.vertices
        val surface = nextSurface++
        val counterClockwise = !hole
        for (part in triangulatePlanarPolygon(points, face, counterClockwise)) {
            triangle(
                transformed(points[part.a], face, inner),
                transformed(points[part.b], face, inner),
                transformed(points[part.c], face, inner),
                surface,
                reverse = inner xor hole,
            )
        }
    }

    fun addRimWall(cycle: ResolvedRimCycle, face: Face) {
        for (index in cycle.vertices.indices) {
            val next = (index + 1) % cycle.vertices.size
            val outerA = transformed(cycle.vertices[index], face, inner = false)
            val outerB = transformed(cycle.vertices[next], face, inner = false)
            val innerA = transformed(cycle.vertices[index], face, inner = true)
            val innerB = transformed(cycle.vertices[next], face, inner = true)
            val surface = nextSurface++
            triangle(outerA, innerA, outerB, surface)
            triangle(innerA, innerB, outerB, surface)
        }
    }

    fun addFaceBoundaryWall(face: Face) {
        for (index in face.fvs.indices) {
            val next = (index + 1) % face.size
            val outerA = transformed(face[index], face, inner = false)
            val outerB = transformed(face[next], face, inner = false)
            val innerA = transformed(face[index], face, inner = true)
            val innerB = transformed(face[next], face, inner = true)
            val surface = nextSurface++
            triangle(outerA, outerB, innerA, surface)
            triangle(innerA, outerB, innerB, surface)
        }
    }

    fun request(): CoreStlRequest {
        require(triangles.isNotEmpty()) { "STL presentation does not contain geometry" }
        return CoreStlRequest(vertices, triangles)
    }
}
