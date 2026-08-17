package polyhedra.core.api

import polyhedra.core.poly.resolvedRimsForExport
import polyhedra.core.poly.resolvedRimsForStableExport
import polyhedra.core.transform.resolved
import polyhedra.core.util.OperationProgressContext
import polyhedra.model.api.CoreStlPresentation
import polyhedra.model.api.CoreStlRequest
import polyhedra.model.api.CoreStlTriangle
import polyhedra.model.poly.Face
import polyhedra.model.poly.FaceThicknessJoins
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.MutableFace
import polyhedra.model.poly.MutableVertex
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.ResolvedFaceGeometry
import polyhedra.model.poly.ResolvedRimCycle
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.area
import polyhedra.model.poly.essence
import polyhedra.model.poly.get
import polyhedra.model.poly.size
import polyhedra.model.poly.triangulatePlanarPolygon
import polyhedra.model.poly.triangulatePlanarRegion
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
import polyhedra.model.util.unaryMinus
import kotlin.math.abs

/** Builds presentation solids from authoritative polygonal geometry before STL arrangement. */
internal suspend fun CoreStlPresentation.toTriangleRequest(
    stableJoinsFallback: Boolean = false,
    reportProgress: (Int) -> Unit,
): CoreStlRequest {
    require(scale.isFinite() && scale > 0.0) { "STL scale must be finite and positive" }
    require(width.isFinite() && width >= 0.0) { "STL width must be finite and non-negative" }
    require(rim.isFinite() && rim >= 0.0) { "STL rim must be finite and non-negative" }
    require(expand.isFinite() && expand >= 0.0) { "STL expansion must be finite and non-negative" }

    val source = poly
    val hiddenKinds = (hiddenFaceKinds + source.nonPlanarFaceKinds).toSet()
    val thicknessJoins = FaceThicknessJoins(source)
    val useClosedSourcePieces = hiddenKinds.isNotEmpty() && width > 0.0 && expand == 0.0
    val useMiteredShell = !stableJoinsFallback && useClosedSourcePieces && thicknessJoins.usesExactMiterJoins
    val allSourceFacesHidden = source.fs.all { face -> face.kind in hiddenKinds }
    val physical = if (useClosedSourcePieces || allSourceFacesHidden) {
        reportProgress(20)
        source
    } else {
        source.resolved(OperationProgressContext { progress -> reportProgress(progress / 5) })
    }
    val hiddenPhysicalFaces = physical.fs.filterTo(linkedSetOf()) { face ->
        physical.sourceFaceKinds(face, source, physical !== source).any(hiddenKinds::contains)
    }
    val rimBySourceFace = if (rim > 0.0 && hiddenKinds.isNotEmpty()) {
        val geometries = if (stableJoinsFallback) {
            source.resolvedRimsForStableExport(rim)
        } else {
            source.resolvedRimsForExport(rim, width)
        }
        geometries.associateBy { geometry -> geometry.sourceFaceId }
    } else {
        emptyMap()
    }
    val rotation = physical.rotationWithLargestFaceDown()
    val shellReferenceDistance = physical.fs.map { face -> abs(face.d) }
        .filter { distance -> distance > 1e-12 }
        .minOrNull() ?: physical.circumradius
    val requiresRadialJoins = source.nonPlanarFaceKinds.isNotEmpty() ||
        source.resolvedFaces.any { geometry -> geometry.sourceBoundarySelfIntersects }
    val joinMode = when {
        useMiteredShell -> StlJoinMode.Mitered
        stableJoinsFallback -> StlJoinMode.GlobalRadial
        requiresRadialJoins -> StlJoinMode.FaceRadial
        else -> StlJoinMode.NearlyNormal
    }
    val builder = PresentationMeshBuilder(this, rotation, shellReferenceDistance, joinMode)
    val hasHiddenFaces = hiddenPhysicalFaces.isNotEmpty()

    if (useMiteredShell) {
        source.fs.forEachIndexed { index, face ->
            if (face.kind !in hiddenKinds) {
                builder.addMiteredFace(face, source.resolvedFaces[face.id], thicknessJoins)
            }
            reportProgress(20 + 5 * (index + 1) / source.fs.size)
        }
        if (rim > 0.0) for (face in source.fs.filter { candidate -> candidate.kind in hiddenKinds }) {
            val geometry = rimBySourceFace[face.id] ?: continue
            for (region in geometry.regions) builder.addMiteredRimRegion(region, face, thicknessJoins)
        }
        return builder.request()
    }

    physical.fs.forEachIndexed { index, face ->
        if (face !in hiddenPhysicalFaces) {
            val geometry = physical.resolvedFaces[face.id]
            if ((hasHiddenFaces || expand > 0.0) && width > 0.0) {
                builder.addClosedFace(face, geometry)
            } else {
                builder.addFace(face, geometry, inner = false)
            }
        } else if (rim <= 0.0 && width > 0.0) {
            builder.addFaceBoundaryWall(face)
        }
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

private enum class StlJoinMode { Mitered, NearlyNormal, FaceRadial, GlobalRadial }

private class PresentationMeshBuilder(
    private val settings: CoreStlPresentation,
    private val rotation: Quat,
    shellReferenceDistance: Double,
    private val joinMode: StlJoinMode,
) {
    private val vertices = arrayListOf<MutableVec3>()
    private val vertexIds = linkedMapOf<Triple<Double, Double, Double>, Int>()
    private val triangles = arrayListOf<CoreStlTriangle>()
    private var nextSurface = 0
    private var nextSolid = 0

    private val innerScale = (1.0 - settings.width / shellReferenceDistance).coerceAtLeast(0.0)

    private fun Face.originOutwardNormal(): Vec3 = if (d >= 0.0) this else -this
    private fun transformed(
        point: Vec3,
        expansionDirection: Vec3,
        thicknessDirection: Vec3,
        inner: Boolean,
    ): Vec3 {
        val insetPoint = if (!inner) {
            point
        } else if (joinMode == StlJoinMode.Mitered) {
            point - thicknessDirection * settings.width
        } else if (joinMode == StlJoinMode.GlobalRadial) {
            point * innerScale
        } else {
            val planeDistance = abs(thicknessDirection * point)
            val radialScale = (1.0 - settings.width / planeDistance).coerceAtLeast(0.0)
            val radialOffset = point * radialScale
            if (joinMode == StlJoinMode.FaceRadial) {
                radialOffset
            } else {
                val normalOffset = point - thicknessDirection * settings.width
                // This half-percent radial component disambiguates neighboring Boolean pieces;
                // both endpoints have exactly the requested perpendicular depth.
                normalOffset * 0.995 + radialOffset * 0.005
            }
        }
        return ((insetPoint + expansionDirection * settings.expand) * settings.scale).rotated(rotation)
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
        val presentationSurface = if (joinMode == StlJoinMode.Mitered) -1 else surface
        if (ids.toSet().size == 3) {
            triangles += CoreStlTriangle(ids[0], ids[1], ids[2], presentationSurface, solid)
        }
    }

    fun addFace(face: Face, geometry: ResolvedFaceGeometry, inner: Boolean, solid: Int = -1) {
        addFace(face, geometry, inner, solid) { face.originOutwardNormal() }
    }

    private fun addFace(
        face: Face,
        geometry: ResolvedFaceGeometry,
        inner: Boolean,
        solid: Int,
        thicknessDirection: (Vec3) -> Vec3,
    ) {
        val surface = nextSurface++
        for (triangle in geometry.triangles) {
            val a = geometry.vertices[triangle.a].position
            val b = geometry.vertices[triangle.b].position
            val c = geometry.vertices[triangle.c].position
            triangle(
                transformed(a, face, thicknessDirection(a), inner),
                transformed(b, face, thicknessDirection(b), inner),
                transformed(c, face, thicknessDirection(c), inner),
                surface,
                reverse = inner,
                solid = solid,
            )
        }
    }

    fun addMiteredFace(face: Face, geometry: ResolvedFaceGeometry, joins: FaceThicknessJoins) {
        addFace(face, geometry, inner = false, solid = MITERED_SHELL_SOLID)
        addFace(face, geometry, inner = true, solid = MITERED_SHELL_SOLID) { point ->
            joins.direction(face, point)
        }
    }

    fun addClosedFace(face: Face, geometry: ResolvedFaceGeometry) {
        val solid = nextSolid++
        addFace(face, geometry, inner = false, solid = solid)
        addFace(face, geometry, inner = true, solid = solid)
        addFaceBoundaryWall(face, solid)
    }

    private fun addRimCycle(
        cycle: ResolvedRimCycle,
        face: Face,
        expansionDirection: Vec3,
        inner: Boolean,
        hole: Boolean,
        solid: Int,
        thicknessDirection: (Vec3) -> Vec3 = { face.originOutwardNormal() },
    ) {
        val points = cycle.vertices
        val surface = nextSurface++
        val counterClockwise = !hole
        for (part in triangulatePlanarPolygon(points, face, counterClockwise)) {
            triangle(
                transformed(points[part.a], expansionDirection, thicknessDirection(points[part.a]), inner),
                transformed(points[part.b], expansionDirection, thicknessDirection(points[part.b]), inner),
                transformed(points[part.c], expansionDirection, thicknessDirection(points[part.c]), inner),
                surface,
                reverse = inner xor hole,
                solid = solid,
            )
        }
    }

    private fun addRimWall(
        cycle: ResolvedRimCycle,
        expansionDirection: Vec3,
        thicknessDirection: Vec3,
        solid: Int,
        includeSegment: (Int) -> Boolean = { true },
    ) {
        for (index in cycle.vertices.indices) {
            if (!includeSegment(index)) continue
            val next = (index + 1) % cycle.vertices.size
            val outerA = transformed(cycle.vertices[index], expansionDirection, thicknessDirection, inner = false)
            val outerB = transformed(cycle.vertices[next], expansionDirection, thicknessDirection, inner = false)
            val innerA = transformed(cycle.vertices[index], expansionDirection, thicknessDirection, inner = true)
            val innerB = transformed(cycle.vertices[next], expansionDirection, thicknessDirection, inner = true)
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
            val thicknessDirection = regionFace.originOutwardNormal()
            addRimWall(region.outer, face, thicknessDirection, solid)
            region.holes.forEach { hole -> addRimWall(hole, face, thicknessDirection, solid) }
        }
    }

    fun addMiteredRimRegion(
        region: polyhedra.model.poly.ResolvedRimRegion,
        face: Face,
        joins: FaceThicknessJoins,
    ) {
        val regionFace = if (region.triangulationPatch) region.patchFace(face) else face
        val patchNormal = regionFace.originOutwardNormal()
        val innerDirection: (Vec3) -> Vec3 = { point ->
            if (joins.sourceEdgeOrNull(face, point) != null) joins.direction(face, point) else patchNormal
        }
        val solid = MITERED_SHELL_SOLID
        addMiteredRimSurface(region, regionFace, face, inner = false, solid = solid) { patchNormal }
        addMiteredRimSurface(
            region,
            regionFace,
            face,
            inner = true,
            solid = solid,
            thicknessDirection = innerDirection,
        )
        val cycles = listOf(region.outer) + region.holes
        for (cycle in cycles) {
            addRimWall(cycle, face, patchNormal, solid) { index ->
                if (region.triangulationPatch && cycle.segmentSources[index].isEmpty()) {
                    false
                } else {
                    val next = (index + 1) % cycle.vertices.size
                    joins.sourceEdgeOrNull(face, cycle.vertices[index], cycle.vertices[next]) == null
                }
            }
        }
    }

    private fun addMiteredRimSurface(
        region: polyhedra.model.poly.ResolvedRimRegion,
        regionFace: Face,
        expansionDirection: Vec3,
        inner: Boolean,
        solid: Int,
        thicknessDirection: (Vec3) -> Vec3,
    ) {
        val mesh = triangulatePlanarRegion(
            region.outer.vertices,
            region.holes.map(ResolvedRimCycle::vertices),
            regionFace.originOutwardNormal(),
        )
        val surface = nextSurface++
        for (part in mesh.triangles) {
            val a = mesh.vertices[part.a]
            val b = mesh.vertices[part.b]
            val c = mesh.vertices[part.c]
            val reversed = ((b - a) cross (c - a)) * regionFace.originOutwardNormal() < 0.0
            triangle(
                transformed(a, expansionDirection, thicknessDirection(a), inner),
                transformed(b, expansionDirection, thicknessDirection(b), inner),
                transformed(c, expansionDirection, thicknessDirection(c), inner),
                surface,
                reverse = inner xor reversed,
                solid = solid,
            )
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

    fun addFaceBoundaryWall(face: Face, solid: Int = -1) {
        for (index in face.fvs.indices) {
            val next = (index + 1) % face.size
            val thicknessDirection = face.originOutwardNormal()
            val outerA = transformed(face[index], face, thicknessDirection, inner = false)
            val outerB = transformed(face[next], face, thicknessDirection, inner = false)
            val innerA = transformed(face[index], face, thicknessDirection, inner = true)
            val innerB = transformed(face[next], face, thicknessDirection, inner = true)
            val surface = nextSurface++
            triangle(outerA, outerB, innerA, surface, solid = solid)
            triangle(innerA, outerB, innerB, surface, solid = solid)
        }
    }

    fun request(): CoreStlRequest {
        require(triangles.isNotEmpty()) { "STL presentation does not contain geometry" }
        return CoreStlRequest(vertices, triangles)
    }

    private companion object {
        /** One watertight presentation shell whose coplanar pieces may be merged before resolving. */
        const val MITERED_SHELL_SOLID = 0
    }
}
