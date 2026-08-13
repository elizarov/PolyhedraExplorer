package polyhedra.core.api

import polyhedra.core.poly.resolvedRimsForExport
import polyhedra.core.transform.resolved
import polyhedra.core.util.OperationProgressContext
import polyhedra.model.api.CoreStlPresentation
import polyhedra.model.api.CoreStlRequest
import polyhedra.model.api.CoreStlTriangle
import polyhedra.model.poly.Face
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.MutableFace
import polyhedra.model.poly.MutableVertex
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.ResolvedRimCycle
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.area
import polyhedra.model.poly.essence
import polyhedra.model.poly.get
import polyhedra.model.poly.size
import polyhedra.model.poly.triangulatePlanarPolygon
import polyhedra.model.util.MutableVec3
import polyhedra.model.util.Quat
import polyhedra.model.util.Vec3
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.norm
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
    val hiddenKinds = (hiddenFaceKinds + source.nonPlanarFaceKinds).toSet()
    val allSourceFacesHidden = source.fs.all { face -> face.kind in hiddenKinds }
    val physical = if (allSourceFacesHidden) {
        reportProgress(20)
        source
    } else {
        source.resolved(OperationProgressContext { progress -> reportProgress(progress / 5) })
    }
    val hiddenPhysicalFaces = physical.fs.filterTo(linkedSetOf()) { face ->
        physical.sourceFaceKinds(face, source, physical !== source).any(hiddenKinds::contains)
    }
    val rimBySourceFace = if (rim > 0.0 && hiddenKinds.isNotEmpty()) {
        source.resolvedRimsForExport(rim).associateBy { geometry -> geometry.sourceFaceId }
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
            builder.addRimRegion(region, face)
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
    private var nextSolid = 0
    private val innerScale = (1.0 - settings.width / circumradius).coerceAtLeast(0.0)

    private fun transformed(point: Vec3, expansionDirection: Vec3, inner: Boolean): Vec3 {
        val inset = if (inner) point * innerScale else point
        return ((inset + expansionDirection * settings.expand) * settings.scale).rotated(rotation)
    }

    private fun vertex(point: Vec3): Int {
        val key = Triple(point.x, point.y, point.z)
        return vertexIds.getOrPut(key) {
            vertices += MutableVec3(point)
            vertices.lastIndex
        }
    }

    private fun triangle(
        a: Vec3,
        b: Vec3,
        c: Vec3,
        surface: Int,
        reverse: Boolean = false,
        solid: Int = -1,
    ) {
        val ids = if (reverse) listOf(vertex(a), vertex(c), vertex(b)) else listOf(vertex(a), vertex(b), vertex(c))
        if (ids.toSet().size == 3) triangles += CoreStlTriangle(ids[0], ids[1], ids[2], surface, solid)
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

    private fun addRimCycle(
        cycle: ResolvedRimCycle,
        face: Face,
        expansionDirection: Vec3,
        inner: Boolean,
        hole: Boolean,
        solid: Int,
    ) {
        val points = cycle.vertices
        val surface = nextSurface++
        val counterClockwise = !hole
        for (part in triangulatePlanarPolygon(points, face, counterClockwise)) {
            triangle(
                transformed(points[part.a], expansionDirection, inner),
                transformed(points[part.b], expansionDirection, inner),
                transformed(points[part.c], expansionDirection, inner),
                surface,
                reverse = inner xor hole,
                solid = solid,
            )
        }
    }

    private fun addRimWall(cycle: ResolvedRimCycle, expansionDirection: Vec3, solid: Int) {
        for (index in cycle.vertices.indices) {
            val next = (index + 1) % cycle.vertices.size
            val outerA = transformed(cycle.vertices[index], expansionDirection, inner = false)
            val outerB = transformed(cycle.vertices[next], expansionDirection, inner = false)
            val innerA = transformed(cycle.vertices[index], expansionDirection, inner = true)
            val innerB = transformed(cycle.vertices[next], expansionDirection, inner = true)
            val surface = nextSurface++
            triangle(outerA, innerA, outerB, surface, solid = solid)
            triangle(innerA, innerB, outerB, surface, solid = solid)
        }
    }

    fun addRimRegion(region: polyhedra.model.poly.ResolvedRimRegion, face: Face) {
        val regionFace = if (region.triangulationPatch) region.patchFace(face) else face
        val solid = if (settings.width > 0.0) nextSolid++ else -1
        addRimCycle(region.outer, regionFace, face, inner = false, hole = false, solid = solid)
        region.holes.forEach { hole ->
            addRimCycle(hole, regionFace, face, inner = false, hole = true, solid = solid)
        }
        addRimCycle(region.outer, regionFace, face, inner = true, hole = false, solid = solid)
        region.holes.forEach { hole ->
            addRimCycle(hole, regionFace, face, inner = true, hole = true, solid = solid)
        }
        if (settings.width > 0.0) {
            addRimWall(region.outer, face, solid)
            region.holes.forEach { hole -> addRimWall(hole, face, solid) }
        }
    }

    private fun polyhedra.model.poly.ResolvedRimRegion.patchFace(source: Face): Face {
        val origin = outer.vertices.first()
        val second = outer.vertices.first { point -> (point - origin).norm > 1e-12 }
        val third = outer.vertices.first { point -> ((second - origin) cross (point - origin)).norm > 1e-12 }
        val positions = listOf(origin, second, third)
        val vertices = positions.mapIndexed { index, point -> MutableVertex(index, point, VertexKind(0)) }
        return MutableFace(source.id, vertices, source.kind)
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
