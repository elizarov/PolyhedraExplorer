package polyhedra.core.api

import polyhedra.core.poly.GeometricOrbitDetails
import polyhedra.core.poly.geometricOrbitDetails
import polyhedra.model.api.CoreProgress
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreResponse
import polyhedra.model.api.CoreState
import polyhedra.model.poly.Edge
import polyhedra.model.poly.EdgeKind
import polyhedra.model.poly.Face
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.Vertex
import polyhedra.model.poly.VertexKind
import polyhedra.model.poly.fev
import polyhedra.model.poly.len
import polyhedra.model.serialization.ParsedParameter
import polyhedra.model.serialization.composite
import polyhedra.model.serialization.normalizeCompactConfiguration
import polyhedra.model.serialization.parseCompactParameters
import polyhedra.model.serialization.value
import polyhedra.model.util.norm
import kotlin.math.round
import kotlin.time.TimeSource

private const val DEFAULT_SEED_TAG = "T"
private const val DEFAULT_SCALE_TAG = "c"
private const val DEFAULT_FACE_RIM = 0.05
private const val DEFAULT_FACE_WIDTH = 0.10
private const val DEFAULT_PROGRESS_BAR_WIDTH = 32

/** Core-relevant values decoded from the same compact configuration used by browser URLs. */
data class CompactCoreConfiguration(
    val normalized: String,
    val state: CoreState,
    val rimWidth: Double?,
    val faceWidth: Double?,
)

/** A timed core response and the complete human-readable diagnostics derived from it. */
data class CoreInspection(
    val configuration: CompactCoreConfiguration,
    val response: CoreResponse,
    val coreTimeMicros: Long,
    val orbitAnalysisTimeMicros: Long,
    val report: String,
)

fun formatCoreProgress(
    progress: CoreProgress,
    transformTags: List<String>,
    barWidth: Int = DEFAULT_PROGRESS_BAR_WIDTH,
): String {
    require(barWidth > 0)
    val done = progress.done.coerceIn(0, 100)
    val filled = done * barWidth / 100
    val bar = "#".repeat(filled) + "-".repeat(barWidth - filled)
    val stage = transformTags.getOrNull(progress.transformIndex) ?: "seed"
    val position = if (transformTags.isEmpty()) "" else
        " ${progress.transformIndex + 1}/${transformTags.size}"
    return "Core [$bar] ${done.toString().padStart(3)}%$position  $stage"
}

fun parseCompactCoreConfiguration(configuration: String): CompactCoreConfiguration {
    val normalized = normalizeCompactConfiguration(configuration)
    val root = parseCompactParameters(normalized) as? ParsedParameter.Composite
    val seedTag = root?.value("s")?.takeIf(String::isNotEmpty) ?: DEFAULT_SEED_TAG
    val transformTags = root?.value("t")
        ?.takeIf(String::isNotEmpty)
        ?.split(',')
        .orEmpty()
    val scaleTag = root?.value("bs")?.takeIf(String::isNotEmpty) ?: DEFAULT_SCALE_TAG
    val view = root?.composite("v")
    val rimWidth = view?.value("fr")?.toDoubleOrNull() ?: DEFAULT_FACE_RIM
    val faceWidth = view?.value("fw")?.toDoubleOrNull() ?: DEFAULT_FACE_WIDTH
    return CompactCoreConfiguration(
        normalized = normalized,
        state = CoreState(seedTag, transformTags, scaleTag),
        rimWidth = rimWidth.takeIf { it > 0.0 },
        faceWidth = faceWidth.takeIf { rimWidth > 0.0 && it > 0.0 },
    )
}

/**
 * Runs the real core and expands its geometric symmetry into explicit memberships for debugging.
 * The core timing deliberately excludes the additional diagnostic orbit expansion.
 */
suspend fun inspectCompactConfiguration(
    configuration: String,
    calculateTweakRanges: Boolean = true,
    detectSeed: Boolean = true,
    reportProgress: (CoreProgress) -> Unit = {},
): CoreInspection {
    val compact = parseCompactCoreConfiguration(configuration)
    val request = CoreRequest(
        state = compact.state,
        calculateTweakRanges = calculateTweakRanges,
        detectSeed = detectSeed,
        rimWidth = compact.rimWidth,
        faceWidth = compact.faceWidth,
    )
    val coreStart = TimeSource.Monotonic.markNow()
    val response = evaluateCore(request, reportProgress)
    val coreTimeMicros = coreStart.elapsedNow().inWholeMicroseconds
    val orbitStart = TimeSource.Monotonic.markNow()
    val orbitDetails = response.poly.geometricOrbitDetails()
    val orbitAnalysisTimeMicros = orbitStart.elapsedNow().inWholeMicroseconds
    val report = formatCoreInspection(
        compact,
        response,
        orbitDetails,
        coreTimeMicros,
        orbitAnalysisTimeMicros,
        calculateTweakRanges,
        detectSeed,
    )
    return CoreInspection(compact, response, coreTimeMicros, orbitAnalysisTimeMicros, report)
}

private fun formatCoreInspection(
    configuration: CompactCoreConfiguration,
    response: CoreResponse,
    orbits: GeometricOrbitDetails,
    coreTimeMicros: Long,
    orbitAnalysisTimeMicros: Long,
    calculatedTweakRanges: Boolean,
    detectedSeed: Boolean,
): String = buildString {
    val poly = response.poly
    val storedCounts = poly.storedKindCounts()
    val geometricCounts = response.symmetry.orbitCounts
    appendLine("Core configuration inspection")
    appendLine("=============================")
    appendLine("Configuration: ${configuration.normalized.ifEmpty { "<empty>" }.asciiSafe()}")
    appendLine("Seed: ${configuration.state.seedTag}")
    appendLine("Transforms: ${configuration.state.transformTags.ifEmpty { listOf("<none>") }.joinToString(" -> ").asciiSafe()}")
    appendLine("Scale: ${configuration.state.scaleTag}")
    appendLine("Rim / face width: ${configuration.rimWidth ?: "off"} / ${configuration.faceWidth ?: "off"}")
    appendLine()
    appendLine("Timing")
    appendLine("------")
    appendLine("Core construction: ${formatMicros(coreTimeMicros)}")
    appendLine("Diagnostic orbit expansion: ${formatMicros(orbitAnalysisTimeMicros)}")
    appendLine("Total: ${formatMicros(coreTimeMicros + orbitAnalysisTimeMicros)}")
    appendLine()
    appendLine("Result")
    appendLine("------")
    appendLine("Name: ${response.polyName}")
    appendLine("Status: ${response.error?.let { "ERROR ${it.code}: ${it.detail.orEmpty()}" } ?: "success"}")
    appendLine("Failed transform index: ${response.errorIndex ?: "none"}")
    appendLine("Recognized seed: ${if (detectedSeed) response.recognizedSeedTag ?: "none" else "not requested"}")
    appendLine("Elements: ${poly.fev()}")
    appendLine("Stored kinds: $storedCounts")
    appendLine("Geometric orbits: $geometricCounts")
    appendLine("Kind/orbit consistency: ${if (storedCounts == geometricCounts) "OK" else "MISMATCH"}")
    appendLine("Radii: in=${formatNumber(poly.inradius)}, mid=${formatNumber(poly.midradius)}, circum=${formatNumber(poly.circumradius)}")
    appendLine("Planar / non-planar faces: ${poly.fs.count(Face::isPlanar)} / ${poly.fs.count { !it.isPlanar }}")
    appendLine("Resolved face cells: ${poly.resolvedFaces.sumOf { it.cells.size }}")
    appendLine("Resolved rims: ${response.resolvedRims.size} faces, " +
        "${response.resolvedRims.sumOf { it.regions.size }} regions")
    response.geometryAnalysis?.let { analysis ->
        appendLine("Geometry contract: ${analysis.strongestContract}")
        appendLine("Intersections: ${analysis.intersectionCounts.entries.joinToString().ifEmpty { "none" }}")
    }
    appendLine()
    appendLine("Symmetry")
    appendLine("--------")
    appendLine("Point group: ${response.symmetry.pointGroup.notation} (${response.symmetry.pointGroup.fullName})")
    appendLine("Proper / reversing operations: ${orbits.properOperationCount} / ${orbits.reversingOperationCount}")
    appendLine("Rotation axes / mirror planes: ${response.symmetry.rotationAxisDirections.size} / " +
        response.symmetry.reflectionPlaneNormals.size)
    appendLine()
    appendLine("Transform stages")
    appendLine("----------------")
    if (response.transformedPolys.isEmpty()) {
        appendLine("<none>")
    } else {
        response.transformedPolys.forEachIndexed { index, stage ->
            appendLine("${index + 1}. ${(response.validTransformTags.getOrNull(index) ?: "<failed>").asciiSafe()}: " +
                "${stage.fev()}, stored kinds ${stage.storedKindCounts()}")
        }
    }
    appendLine("Valid transforms: ${response.validTransformTags.joinToString().ifEmpty { "none" }.asciiSafe()}")
    appendLine("Warnings: ${response.warnings.mapIndexedNotNull { index, warning -> warning?.let { "$index:${it.code}" } }.joinToString().ifEmpty { "none" }}")
    appendLine("Orbit actions by stage:")
    response.availableOrbitTransforms.forEachIndexed { index, tags ->
        appendLine("  $index: ${tags.joinToString().ifEmpty { "none" }.asciiSafe()}")
    }
    appendLine("Tweak ranges: ${if (calculatedTweakRanges) "calculated" else "not requested"}")
    if (calculatedTweakRanges) {
        response.transformTweakRanges.forEachIndexed { index, ranges ->
            appendLine("  $index: ${ranges.joinToString { range ->
                "${range.tweak}=${formatNumber(range.min)}..${formatNumber(range.max)}" +
                    if (range.options.isEmpty()) "" else " (${range.options.size} options)"
            }.ifEmpty { "none" }}")
        }
    }
    appendLine()
    appendOrbitSection("Face", orbits.faceOrbits, poly.fs, Face::kind) { face ->
        "face=${face.id}, sides=${face.fvs.size}, planar=${face.isPlanar}, distance=${formatNumber(face.d)}"
    }
    appendLine()
    appendOrbitSection("Edge", orbits.edgeOrbits, poly.es, Edge::kind) { edge ->
        "edge=${poly.es.indexOf(edge)} (${edge.a.id}-${edge.b.id}), length=${formatNumber(edge.len)}"
    }
    appendLine()
    appendOrbitSection("Vertex", orbits.vertexOrbits, poly.vs, Vertex::kind) { vertex ->
        "vertex=${vertex.id}, degree=${vertex.directedEdges.size}, radius=${formatNumber(vertex.norm)}"
    }
    appendLine()
    appendLine("Face-kind sources")
    appendLine("-----------------")
    response.poly.faceKindSources
        ?.forEach { source -> appendLine("${formatStoredKind(source.kind)} <- ${formatStoredKind(source.source)}") }
        ?: appendLine("none")
}

private fun Polyhedron.storedKindCounts() =
    polyhedra.model.poly.FEV(faceKinds.size, edgeKinds.size, vertexKinds.size)

private fun <T, K> StringBuilder.appendOrbitSection(
    label: String,
    orbits: List<List<Int>>,
    elements: List<T>,
    kind: (T) -> K,
    representative: (T) -> String,
) {
    appendLine("$label orbits (${orbits.size})")
    appendLine("${"-".repeat(label.length)}-----------")
    val orbitByElement = IntArray(elements.size) { -1 }
    orbits.forEachIndexed { orbitIndex, ids ->
        ids.forEach { id -> orbitByElement[id] = orbitIndex }
        val kinds = ids.map { id -> kind(elements[id]) }.distinct()
        appendLine("${orbitIndex + 1}. size=${ids.size}, members=${formatIds(ids)}, " +
            "stored=${kinds.joinToString(transform = ::formatStoredKind)}")
        appendLine("   ${representative(elements[ids.first()])}")
    }
    appendLine("Stored-kind membership:")
    elements.indices.groupBy { index -> kind(elements[index]) }.forEach { (storedKind, ids) ->
        val geometric = ids.map { id -> orbitByElement[id] }.distinct()
        appendLine("  ${formatStoredKind(storedKind)}: members=${formatIds(ids)}, " +
            "geometric orbits=${geometric.joinToString { (it + 1).toString() }}")
    }
}

private fun formatIds(ids: List<Int>): String {
    if (ids.isEmpty()) return "none"
    val result = ArrayList<String>()
    var first = ids.first()
    var last = first
    fun addRange() {
        result += if (first == last) first.toString() else "$first-$last"
    }
    for (id in ids.drop(1)) {
        if (id == last + 1) {
            last = id
        } else {
            addRange()
            first = id
            last = id
        }
    }
    addRange()
    return result.joinToString(",")
}

private fun formatMicros(micros: Long): String =
    "${micros / 1_000}.${(micros % 1_000).toString().padStart(3, '0')} ms"

private fun formatNumber(value: Double): String {
    if (!value.isFinite()) return value.toString()
    val rounded = round(value * 1_000_000.0) / 1_000_000.0
    return rounded.toString()
}

private fun formatStoredKind(kind: Any?): String = when (kind) {
    is FaceKind -> "F${kind.id}"
    is VertexKind -> kind.toString()
    is EdgeKind -> "${formatStoredKind(kind.a)}-${formatStoredKind(kind.l)}/" +
        "${formatStoredKind(kind.r)}-${formatStoredKind(kind.b)}"
    else -> kind.toString().asciiSafe()
}

private fun String.asciiSafe(): String = buildString {
    for (character in this@asciiSafe) {
        if (character.code in 0x20..0x7e || character == '\n' || character == '\r' || character == '\t') {
            append(character)
        } else {
            append("\\u")
            append(character.code.toString(16).padStart(4, '0'))
        }
    }
}
