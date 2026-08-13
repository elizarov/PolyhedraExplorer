package polyhedra.core.api

import polyhedra.core.poly.polyhedron
import polyhedra.core.poly.validateProperGeometry
import polyhedra.core.transform.resolvedTriangleSoup
import polyhedra.core.transform.TriangleSoupTriangle
import polyhedra.core.transform.TransformApplicabilityException
import polyhedra.core.util.OperationProgressContext
import polyhedra.model.api.*
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.VertexKind
import polyhedra.model.util.MutableVec3
import polyhedra.model.util.Vec3
import polyhedra.model.util.EPS
import polyhedra.model.util.cross
import polyhedra.model.util.minus
import polyhedra.model.util.plus
import polyhedra.model.util.norm
import polyhedra.model.util.times
import polyhedra.model.util.unit
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.floor
import kotlin.time.TimeSource
import kotlin.time.TimeMark

private class StlConversionFailure(val issue: CoreStlError) : IllegalArgumentException(issue.reason)

private data class Bounds(
    val index: Int,
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val minZ: Double,
    val maxZ: Double,
)

private data class PointKey(val x: Double, val y: Double, val z: Double)
private data class InputTriangle(val points: List<Vec3>, val surface: Int)
private data class WeldBucket(val x: Long, val y: Long, val z: Long)

suspend fun convertStl(
    request: CoreStlRequest,
    reportProgress: (Int) -> Unit = {},
): CoreStlResponse {
    val started = TimeSource.Monotonic.markNow()
    fun checkTime(stage: CoreStlStage) {
        val elapsed = started.elapsedNow().inWholeMilliseconds
        if (elapsed > MAX_STL_ELAPSED_MILLISECONDS) {
            failLimit(stage, "elapsed conversion time", MAX_STL_ELAPSED_MILLISECONDS, elapsed)
        }
    }
    val presentation = request.presentation ?: return convertStlInternal(request, reportProgress, started)
    val triangleRequest = try {
        presentation.toTriangleRequest { progress ->
            checkTime(CoreStlStage.Input)
            reportProgress(progress)
        }.also { checkTime(CoreStlStage.Input) }
    } catch (failure: Throwable) {
        return CoreStlResponse(
            error = CoreStlError(
                CoreStlStage.Input,
                failure.message ?: failure::class.simpleName.orEmpty(),
                kind = if (failure is TransformApplicabilityException) {
                    CoreStlErrorKind.Topology
                } else {
                    CoreStlErrorKind.InvalidInput
                },
            ),
        )
    }
    return convertStlInternal(
        triangleRequest,
        reportProgress = { progress -> reportProgress(25 + progress * 75 / 100) },
        started = started,
    )
}

private suspend fun convertStlInternal(
    request: CoreStlRequest,
    reportProgress: (Int) -> Unit,
    started: TimeMark,
): CoreStlResponse {
    var stage = CoreStlStage.Input
    fun checkTime() {
        val elapsed = started.elapsedNow().inWholeMilliseconds
        if (elapsed > MAX_STL_ELAPSED_MILLISECONDS) {
            failLimit(
                stage,
                "elapsed conversion time",
                MAX_STL_ELAPSED_MILLISECONDS,
                elapsed,
            )
        }
    }
    return try {
        reportProgress(0)
        require(request.triangles.isNotEmpty()) { "STL input does not contain triangles" }
        checkLimit(
            CoreStlStage.Input,
            "input triangles",
            MAX_STL_INPUT_TRIANGLES.toLong(),
            request.triangles.size.toLong(),
        )
        val inputBytes = request.vertices.size.toLong() * 3L * Double.SIZE_BYTES +
            request.triangles.size.toLong() * 3L * Int.SIZE_BYTES
        checkLimit(
            CoreStlStage.Input,
            "working memory bytes",
            MAX_STL_WORKING_MEMORY_BYTES,
            inputBytes,
        )
        val radius = request.vertices.maxOfOrNull(Vec3::norm) ?: 0.0
        require(radius.isFinite() && radius > 0.0) { "STL input has no finite nonzero extent" }
        val coordinateFactor = 10.0.pow(STL_COORDINATE_PRECISION)
        // WebGL presentation buffers are float32. Weld only their sub-pixel representation noise;
        // final STL coordinates retain six decimal places independently of this input repair.
        val weldTolerance = maxOf(25e-6, radius * 5e-7)
        val welded = arrayListOf<Vec3>()
        val weldBuckets = hashMapOf<WeldBucket, MutableList<Int>>()
        fun weldBucket(point: Vec3) = WeldBucket(
            floor(point.x / weldTolerance).toLong(),
            floor(point.y / weldTolerance).toLong(),
            floor(point.z / weldTolerance).toLong(),
        )
        val inputVertices = request.vertices.map { point ->
            val bucket = weldBucket(point)
            var match: Vec3? = null
            for (dx in -1L..1L) for (dy in -1L..1L) for (dz in -1L..1L) {
                for (candidate in weldBuckets[WeldBucket(bucket.x + dx, bucket.y + dy, bucket.z + dz)].orEmpty()) {
                    val existing = welded[candidate]
                    if ((existing - point).norm <= weldTolerance) {
                        match = existing
                        break
                    }
                }
            }
            match ?: MutableVec3(point).also { canonical ->
                welded += canonical
                weldBuckets.getOrPut(bucket, ::arrayListOf) += welded.lastIndex
            }
        }
        val minimumArea2 = radius * radius * 1e-14
        val bounds = request.triangles.mapIndexed { index, triangle ->
            val points = triangle.points(request.vertices)
            require(points.all(Vec3::isFinite)) { "STL triangle $index contains a non-finite coordinate" }
            require(points.toSetByCoordinates().size == 3) { "STL triangle $index repeats a vertex" }
            Bounds(
                index,
                points.minOf(Vec3::x), points.maxOf(Vec3::x),
                points.minOf(Vec3::y), points.maxOf(Vec3::y),
                points.minOf(Vec3::z), points.maxOf(Vec3::z),
            )
        }.sortedWith(compareBy(Bounds::minX, Bounds::index))
        reportProgress(3)

        stage = CoreStlStage.BroadPhase
        var candidatePairs = 0L
        for (firstIndex in bounds.indices) {
            val first = bounds[firstIndex]
            for (secondIndex in (firstIndex + 1) until bounds.size) {
                val second = bounds[secondIndex]
                if (second.minX > first.maxX) break
                if (first.minY <= second.maxY && second.minY <= first.maxY &&
                    first.minZ <= second.maxZ && second.minZ <= first.maxZ
                ) {
                    candidatePairs++
                    checkLimit(
                        stage,
                        "broad-phase candidate triangle pairs",
                        MAX_STL_CANDIDATE_PAIRS.toLong(),
                        candidatePairs,
                    )
                }
            }
            if (firstIndex % 256 == 0) checkTime()
        }
        reportProgress(5)

        stage = CoreStlStage.Arrangement
        val distinctTriangles = linkedMapOf<List<PointKey>, InputTriangle>()
        for (triangle in request.triangles) {
            val points = triangle.points(inputVertices)
            if (points.toSetByCoordinates().size < 3 ||
                ((points[1] - points[0]) cross (points[2] - points[0])).norm <= minimumArea2
            ) continue
            val key = points.map { point -> PointKey(point.x, point.y, point.z) }
                .sortedWith(compareBy(PointKey::x, PointKey::y, PointKey::z))
            if (key !in distinctTriangles) {
                distinctTriangles[key] = InputTriangle(points, triangle.surface)
            }
        }
        val surfaces = distinctTriangles.values.toList()
        val parent = IntArray(surfaces.size) { it }
        fun find(index: Int): Int {
            var root = index
            while (parent[root] != root) root = parent[root]
            var current = index
            while (parent[current] != current) {
                val next = parent[current]
                parent[current] = root
                current = next
            }
            return root
        }
        fun union(a: Int, b: Int) {
            val first = find(a)
            val second = find(b)
            if (first != second) parent[maxOf(first, second)] = minOf(first, second)
        }
        val edgeUses = linkedMapOf<List<PointKey>, MutableList<Int>>()
        surfaces.forEachIndexed { triangleIndex, triangle ->
            val points = triangle.points
            for (index in points.indices) {
                val a = points[index]
                val b = points[(index + 1) % points.size]
                val edge = listOf(PointKey(a.x, a.y, a.z), PointKey(b.x, b.y, b.z))
                    .sortedWith(compareBy(PointKey::x, PointKey::y, PointKey::z))
                edgeUses.getOrPut(edge, ::arrayListOf) += triangleIndex
            }
        }
        for (uses in edgeUses.values) for (firstIndex in uses.indices) {
            for (secondIndex in (firstIndex + 1) until uses.size) {
                val a = surfaces[uses[firstIndex]].points
                val b = surfaces[uses[secondIndex]].points
                val normalA = ((a[1] - a[0]) cross (a[2] - a[0])).unit
                val normalB = ((b[1] - b[0]) cross (b[2] - b[0])).unit
                if ((normalA cross normalB).norm <= EPS * 32.0) {
                    union(uses[firstIndex], uses[secondIndex])
                }
            }
        }
        val inferredSurfaceIds = linkedMapOf<Int, Int>()
        val inferredOffset = (surfaces.maxOfOrNull(InputTriangle::surface) ?: -1) + 1
        val triangleSoup = surfaces.mapIndexed { index, triangle ->
            val surfaceId = if (triangle.surface >= 0) {
                triangle.surface
            } else {
                inferredOffset + inferredSurfaceIds.getOrPut(find(index)) { inferredSurfaceIds.size }
            }
            val points = triangle.points
            TriangleSoupTriangle(points[0], points[1], points[2], surfaceId)
        }
        val directBoundary = runCatching {
            val positions = arrayListOf<Vec3>()
            val ids = linkedMapOf<PointKey, Int>()
            val inputFloor = triangleSoup.minOf { triangle -> minOf(triangle.a.z, triangle.b.z, triangle.c.z) }
            fun Double.quantized(): Double = round(this * coordinateFactor) / coordinateFactor
            fun vertex(point: Vec3): Int {
                val key = PointKey(point.x.quantized(), point.y.quantized(), (point.z - inputFloor).quantized())
                return ids.getOrPut(key) {
                    positions += MutableVec3(key.x, key.y, key.z)
                    positions.lastIndex
                }
            }
            val faceVertexIds = triangleSoup.mapNotNull { triangle ->
                listOf(vertex(triangle.a), vertex(triangle.c), vertex(triangle.b))
                    .takeIf { face -> face.toSet().size == 3 }
            }.distinctBy(List<Int>::sorted)
            polyhedron {
                positions.forEach { point -> vertex(point, VertexKind(0)) }
                faceVertexIds.forEachIndexed { index, faceVertices ->
                    face(faceVertices, FaceKind(index))
                }
            }.also { candidate -> candidate.validateProperGeometry() }
        }.getOrNull()
        val resolved = directBoundary ?: resolvedTriangleSoup(
                triangleSoup,
                OperationProgressContext { done ->
                    checkTime()
                    reportProgress(5 + done * 80 / 100)
                },
                maximumEdges = Int.MAX_VALUE,
                toleranceFloor = weldTolerance,
                mergeFaces = false,
            )
        val arrangementTriangles = resolved.fs.sumOf { face -> face.triangles.size }
        checkLimit(
            stage,
            "generated arrangement fragments",
            MAX_STL_ARRANGEMENT_FRAGMENTS.toLong(),
            arrangementTriangles.toLong(),
        )
        // Never let a numerically unsupported arrangement advance to serialization. A successful
        // STL response is an embedded two-manifold; an arrangement that cannot establish that
        // contract is a topology rejection, not a malformed partial export.
        resolved.validateProperGeometry()
        reportProgress(86)

        stage = CoreStlStage.Quantization
        val floor = resolved.vs.minOf(Vec3::z)
        val vertices = arrayListOf<MutableVec3>()
        val vertexIds = linkedMapOf<Triple<Double, Double, Double>, Int>()
        fun vertex(point: Vec3): Int {
            fun Double.quantized(): Double = round(this * coordinateFactor) / coordinateFactor
            val key = Triple(point.x.quantized(), point.y.quantized(), (point.z - floor).quantized())
            return vertexIds.getOrPut(key) {
                vertices += MutableVec3(key.first, key.second, key.third)
                vertices.lastIndex
            }
        }
        val triangles = resolved.fs.flatMap { face ->
            val geometry = resolved.resolvedFaces[face.id]
            geometry.triangles.map { triangle ->
                val ids = listOf(triangle.a, triangle.b, triangle.c).map { index ->
                    vertex(geometry.vertices[index].position)
                }
                require(ids.toSet().size == 3) {
                    "STL quantization merged vertices of resolved face ${face.id} triangle $triangle"
                }
                CoreStlTriangle(ids[0], ids[1], ids[2])
            }
        }.toMutableList()
        require(triangles.isNotEmpty()) { "STL quantization removed every triangle" }
        checkLimit(
            stage,
            "final triangles",
            MAX_STL_FINAL_TRIANGLES.toLong(),
            triangles.size.toLong(),
        )
        val outputBytes = inputBytes + vertices.size.toLong() * 3L * Double.SIZE_BYTES +
            triangles.size.toLong() * 3L * Int.SIZE_BYTES
        checkLimit(
            stage,
            "working memory bytes",
            MAX_STL_WORKING_MEMORY_BYTES,
            outputBytes,
        )
        val duplicate = triangles.groupBy { triangle -> listOf(triangle.a, triangle.b, triangle.c).sorted() }
            .entries.firstOrNull { entry -> entry.value.size != 1 }
        require(duplicate == null) { "STL quantization produced duplicate triangle ${duplicate?.key}" }
        reportProgress(90)

        stage = CoreStlStage.Validation
        var volume6 = triangles.sumOf { triangle ->
            val a = vertices[triangle.a]
            val b = vertices[triangle.b]
            val c = vertices[triangle.c]
            a * (b cross c)
        }
        if (volume6 < 0.0) {
            triangles.indices.forEach { index ->
                val triangle = triangles[index]
                triangles[index] = CoreStlTriangle(triangle.a, triangle.c, triangle.b)
            }
            volume6 = -volume6
        }
        require(volume6.isFinite() && volume6 > radius * radius * radius * 1e-12) {
            "STL final mesh has non-positive volume"
        }
        polyhedron {
            vertices.forEach { point -> vertex(point, VertexKind(0)) }
            triangles.forEachIndexed { index, triangle ->
                face(listOf(triangle.a, triangle.c, triangle.b), FaceKind(index))
            }
        }.validateProperGeometry()
        checkTime()
        reportProgress(100)
        CoreStlResponse(vertices, triangles)
    } catch (failure: StlConversionFailure) {
        CoreStlResponse(error = failure.issue)
    } catch (failure: Throwable) {
        CoreStlResponse(
            error = CoreStlError(
                stage,
                failure.message ?: failure::class.simpleName.orEmpty(),
                kind = when {
                    failure is TransformApplicabilityException -> CoreStlErrorKind.Topology
                    stage == CoreStlStage.Input -> CoreStlErrorKind.InvalidInput
                    else -> CoreStlErrorKind.Topology
                },
            ),
        )
    }
}

private fun checkLimit(stage: CoreStlStage, name: String, limit: Long, observed: Long) {
    if (observed > limit) failLimit(stage, name, limit, observed)
}

private fun failLimit(stage: CoreStlStage, name: String, limit: Long, observed: Long): Nothing =
    throw StlConversionFailure(
        CoreStlError(
            stage = stage,
            reason = "STL $name limit exceeded: $observed > $limit",
            limitName = name,
            limit = limit,
            observed = observed,
            kind = CoreStlErrorKind.Limit,
        ),
    )

private fun CoreStlTriangle.points(vertices: List<Vec3>): List<Vec3> {
    require(a in vertices.indices && b in vertices.indices && c in vertices.indices) {
        "STL triangle index is outside the vertex array: $this"
    }
    return listOf(vertices[a], vertices[b], vertices[c])
}

private fun List<Vec3>.toSetByCoordinates(): Set<Triple<Double, Double, Double>> =
    mapTo(linkedSetOf()) { point -> Triple(point.x, point.y, point.z) }

private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()
