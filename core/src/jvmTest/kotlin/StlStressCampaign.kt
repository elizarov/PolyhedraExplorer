package polyhedra.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import polyhedra.core.api.convertStl
import polyhedra.core.poly.polyhedron
import polyhedra.core.poly.validateProperGeometry
import polyhedra.model.api.CoreStlError
import polyhedra.model.api.CoreStlPresentation
import polyhedra.model.api.CoreStlErrorKind
import polyhedra.model.api.CoreStlRequest
import polyhedra.model.api.CoreStlResponse
import polyhedra.model.poly.FaceKind
import polyhedra.model.poly.Polyhedron
import polyhedra.model.poly.VertexKind
import polyhedra.model.util.MutableVec3
import polyhedra.model.util.cross
import polyhedra.model.util.times
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Opt-in research runner; minimized failures belong in [StlApiTest], not in the normal suite. */
fun main(arguments: Array<String>) = runBlocking {
    val cases = arguments.getOrNull(0)?.toIntOrNull() ?: 10_000
    val seed = arguments.getOrNull(1)?.toLongOrNull() ?: 20_260_813L
    require(cases > 0)
    val random = Random(seed)
    val failures = arrayListOf<String>()
    val errors = linkedMapOf<String, Int>()
    var successCount = 0
    var topologyErrorCount = 0
    var limitErrorCount = 0
    val parallelism = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
    val caseSeeds = List(cases) { random.nextLong() }

    caseSeeds.withIndex().chunked(parallelism).forEach { batch ->
        val outcomes = coroutineScope {
            batch.map { (index, fixtureSeed) ->
                async(Dispatchers.Default) { runCampaignCase(index, fixtureSeed) }
            }.awaitAll()
        }
        for (outcome in outcomes) {
            outcome.error?.let { error ->
                errors.merge(error.campaignCategory(), 1, Int::plus)
                when (error.kind) {
                    CoreStlErrorKind.Topology -> topologyErrorCount++
                    CoreStlErrorKind.Limit -> limitErrorCount++
                    CoreStlErrorKind.InvalidInput -> failures += outcome.failureDescription(error)
                }
            } ?: if (outcome.validationFailure == null) {
                successCount++
            } else {
                failures += "${outcome.description} validation=${outcome.validationFailure}"
            }
            if ((outcome.index + 1) % 1_000 == 0) {
                println("STL stress seed=$seed: ${outcome.index + 1}/$cases, failures=${failures.size}")
            }
        }
    }

    println(
        "STL stress campaign seed=$seed cases=$cases successes=$successCount " +
            "topologyErrors=$topologyErrorCount limitErrors=$limitErrorCount",
    )
    errors.entries.sortedByDescending(Map.Entry<String, Int>::value).take(10).forEach { (reason, count) ->
        println("  $count x $reason")
    }
    check(failures.isEmpty()) {
        "STL stress campaign failed (seed=$seed):\n${failures.take(20).joinToString("\n")}"
    }
    check(limitErrorCount == 0) {
        "Small STL stress fixtures unexpectedly reached a browser resource limit (seed=$seed)"
    }
    check(successCount * 20 >= cases * 7) {
        "STL stress success rate fell below 35% (seed=$seed): $successCount/$cases"
    }
}

private data class CampaignOutcome(
    val index: Int,
    val description: String,
    val error: CoreStlError?,
    val validationFailure: String?,
) {
    fun failureDescription(error: CoreStlError): String =
        "$description error=${error.kind}/${error.stage}: ${error.reason}"
}

private suspend fun runCampaignCase(index: Int, fixtureSeed: Long): CampaignOutcome {
    val fixtureRandom = Random(fixtureSeed)
    val fixture = randomStarPrism(fixtureRandom)
    val hideCaps = fixtureRandom.nextBoolean()
    val width = fixtureRandom.nextDouble(0.015, 0.12)
    val rim = if (hideCaps) fixtureRandom.nextDouble(0.01, 0.08) else 0.0
    val scale = fixtureRandom.nextDouble(0.01, 1_000.0)
    val expand = if (fixtureRandom.nextInt(10) == 0) {
        fixtureRandom.nextDouble(0.002, 0.04)
    } else {
        0.0
    }
    val response = convertStl(
        CoreStlRequest(
            presentation = CoreStlPresentation(
                poly = fixture.poly,
                hiddenFaceKinds = if (hideCaps) listOf(FaceKind(0)) else emptyList(),
                scale = scale,
                width = width,
                rim = rim,
                expand = expand,
            ),
        ),
    )
    val failure = if (response.error == null) {
        runCatching { response.validateCampaignResult() }.exceptionOrNull()?.message
    } else {
        null
    }
    return CampaignOutcome(
        index,
        "case=$index fixtureSeed=$fixtureSeed ${fixture.description} hidden=$hideCaps " +
            "width=$width rim=$rim scale=$scale expand=$expand",
        response.error,
        failure,
    )
}

private fun CoreStlError.campaignCategory(): String {
    val category = when {
        "Merged Resolve edge" in reason -> "merged boundary is not two-manifold"
        "Resolved boundary edge" in reason -> "triangulated boundary is not two-manifold"
        "disconnected material components" in reason -> "resolved material is disconnected"
        "degenerate triangle" in reason -> "arrangement contains a degenerate triangle"
        "Faces intersect outside" in reason -> "final embedded-boundary validation rejected candidate"
        else -> reason.substringBefore(':')
    }
    return "$kind/$stage: $category"
}

private data class CampaignFixture(val poly: Polyhedron, val description: String)

private fun randomStarPrism(random: Random): CampaignFixture {
    val validPairs = (5..7).flatMap { n ->
        (2 until (n + 1) / 2).mapNotNull { q ->
            (n to q).takeIf { gcd(n, q) == 1 }
        }
    }
    val (n, q) = validPairs[random.nextInt(validPairs.size)]
    val angleOffset = random.nextDouble(0.0, 2.0 * PI)
    // Keep one regular radial orbit so the abstract side faces do not introduce unrelated,
    // arbitrarily close three-dimensional intersections. The random (n, q), rotation,
    // orientation, height, scale, visibility, width, rim, and expansion still exercise the
    // intended planar self-crossing and presentation domains.
    val radii = List(n) { 1.0 }
    val halfHeight = random.nextDouble(0.12, 1.4)
    val reverse = random.nextBoolean()
    val order = List(n) { index -> (index * q) % n }.let { if (reverse) it.asReversed() else it }
    val poly = polyhedron(mergeIndistinguishableKinds = true) {
        for (layer in 0..1) for (index in 0 until n) {
            val angle = angleOffset + 2.0 * PI * index / n
            vertex(
                radii[index] * cos(angle),
                radii[index] * sin(angle),
                if (layer == 0) -halfHeight else halfHeight,
                VertexKind(0),
            )
        }
        face(order, FaceKind(0))
        face(order.asReversed().map { index -> n + index }, FaceKind(0))
        for (index in order.indices) {
            val a = order[index]
            val b = order[(index + 1) % order.size]
            face(listOf(a, n + a, n + b, b), FaceKind(1))
        }
    }
    return CampaignFixture(
        poly,
        "n=$n q=$q angle=$angleOffset halfHeight=$halfHeight reverse=$reverse",
    )
}

private fun gcd(first: Int, second: Int): Int {
    var a = first
    var b = second
    while (b != 0) {
        val remainder = a % b
        a = b
        b = remainder
    }
    return a
}

private fun CoreStlResponse.validateCampaignResult() {
    require(vertices.isNotEmpty() && triangles.isNotEmpty()) { "empty successful response" }
    val poly = polyhedron {
        vertices.forEach { point -> vertex(point, VertexKind(0)) }
        triangles.forEachIndexed { index, triangle ->
            face(listOf(triangle.a, triangle.c, triangle.b), FaceKind(index))
        }
    }
    poly.validateProperGeometry()
    val volume6 = triangles.sumOf { triangle ->
        vertices[triangle.a] * (vertices[triangle.b] cross vertices[triangle.c])
    }
    require(volume6 > 0.0) { "non-positive final volume" }
}
