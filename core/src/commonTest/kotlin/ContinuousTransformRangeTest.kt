package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.*
import polyhedra.core.transform.KisFace
import polyhedra.core.transform.RectifyVertex
import polyhedra.core.transform.TruncateVertex
import polyhedra.core.transform.availableOrbitTransforms
import polyhedra.model.api.CoreIssueCode
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreState
import polyhedra.model.api.CoreTransformTweakRange
import polyhedra.model.api.SeedFamily
import polyhedra.model.api.TransformTweak
import polyhedra.model.api.encodeTransformTag
import polyhedra.model.api.parseTransformTag
import polyhedra.model.api.transformTweakRanges
import polyhedra.model.api.toFamilySeedIdOrNull
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertTrue

class ContinuousTransformRangeTest {
    @Test
    fun geometryDerivedExtremesAreValidForRepresentativeCatalogSeeds() = runTest {
        validateDynamicRanges(representativeCatalogSeeds)
    }

    @Test
    fun geometryDerivedExtremesAreValidAcrossSeedFamilies() = runTest {
        validateDynamicRanges(representativeFamilySeeds)
    }

    private suspend fun validateDynamicRanges(seeds: List<Seed>) {
        val failures = mutableListOf<String>()
        for (seed in seeds) {
            val transformTags = continuousTransformTags + seed.continuousOrbitTransformTags()
            for (tag in transformTags) {
                runCatching { validateDynamicExtremes(seed, tag) }
                    .onFailure { cause -> failures += "${seed.tag} $tag: ${cause.message}" }
            }
        }

        assertTrue(
            failures.isEmpty(),
            failures.joinToString(
                prefix = "Invalid geometry-derived continuous-transform extremes (${failures.size}):\n",
                separator = "\n",
                limit = 100,
                truncated = "\n...",
            ),
        )
    }

    @Test
    fun outerExplorationExtremesNeverExposeInvalidGeometryForRepresentativeCatalogSeeds() = runTest {
        validateOuterExtremes(representativeCatalogSeeds)
    }

    @Test
    fun outerExplorationExtremesNeverExposeInvalidGeometryForPrisms() = runTest {
        validateOuterExtremes(representativeFamilySeeds.withFamily(SeedFamily.Prism))
    }

    @Test
    fun outerExplorationExtremesNeverExposeInvalidGeometryForAntiprisms() = runTest {
        validateOuterExtremes(representativeFamilySeeds.withFamily(SeedFamily.Antiprism))
    }

    @Test
    fun outerExplorationExtremesNeverExposeInvalidGeometryForPyramids() = runTest {
        validateOuterExtremes(representativeFamilySeeds.withFamily(SeedFamily.Pyramid))
    }

    @Test
    fun outerExplorationExtremesNeverExposeInvalidGeometryForBipyramids() = runTest {
        validateOuterExtremes(representativeFamilySeeds.withFamily(SeedFamily.Bipyramid))
    }

    private suspend fun validateOuterExtremes(seeds: List<Seed>) {
        val failures = mutableListOf<String>()
        for (seed in seeds) {
            val transformTags = outerExtremeTransformTags + seed.outerExtremeOrbitTransformTags()
            for (tag in transformTags) {
                runCatching {
                    val response = evaluateCore(
                        CoreRequest(
                            CoreState(seed.tag, listOf(tag), "c"),
                            calculateTweakRanges = false,
                        )
                    )
                    response.poly.validateRenderableImmersion()
                    val error = response.error
                    if (error == null) {
                        check(response.validTransformTags == listOf(tag)) {
                            "Accepted transform was not retained: ${response.validTransformTags}"
                        }
                    } else {
                        check(
                            error.code == CoreIssueCode.InvalidGeometry ||
                                error.code == CoreIssueCode.TransformNotApplicable ||
                                error.code == CoreIssueCode.TooLarge
                        ) { "Unexpected failure: $error" }
                        check(response.validTransformTags.isEmpty()) {
                            "Rejected transform was accepted: ${response.validTransformTags}"
                        }
                    }
                }.onFailure { cause -> failures += "${seed.tag} $tag: ${cause.message}" }
            }
        }

        assertTrue(
            failures.isEmpty(),
            failures.joinToString(
                prefix = "Unsafe outer continuous-transform extremes (${failures.size}):\n",
                separator = "\n",
                limit = 100,
                truncated = "\n...",
            ),
        )
    }

    private suspend fun validateDynamicExtremes(seed: Seed, initialTag: String) {
        val parsed = requireNotNull(initialTag.parseTransformTag())
        val tweakOrder = parsed.id.transformTweakRanges().keys.toList()
        check(tweakOrder.isNotEmpty()) { "No continuous settings" }

        suspend fun validateRangesAt(tag: String) {
            val response = evaluateCore(CoreRequest(CoreState(seed.tag, listOf(tag), "c")))
            response.poly.validateRenderableImmersion()
            check(response.error?.code != CoreIssueCode.TransformFailed) {
                "Default construction crashed: ${response.error}"
            }

            val ranges = response.transformTweakRanges.singleOrNull().orEmpty()
                .associateBy(CoreTransformTweakRange::tweak)
            for (tweak in tweakOrder) {
                val safeRange = ranges[tweak] ?: continue
                val envelope = parsed.id.transformTweakRanges().getValue(tweak)
                check(safeRange.min >= envelope.min && safeRange.max <= envelope.max) {
                    "$tweak range $safeRange exceeds $envelope"
                }

                val minTick = ceil(safeRange.min * 100.0 - 1e-9).toInt()
                val maxTick = floor(safeRange.max * 100.0 + 1e-9).toInt()
                if (minTick > maxTick) continue
                for (tick in linkedSetOf(minTick, maxTick)) {
                    val current = requireNotNull(tag.parseTransformTag())
                    val value = tick / 100.0
                    val candidateTweaks = current.tweaks.toMutableMap().apply {
                        if (value == 1.0) remove(tweak) else put(tweak, value)
                    }
                    val candidateTag = encodeTransformTag(current.id, candidateTweaks)
                    val endpoint = evaluateCore(
                        CoreRequest(
                            CoreState(seed.tag, listOf(candidateTag), "c"),
                            calculateTweakRanges = false,
                        )
                    )
                    endpoint.poly.validateRenderableImmersion()
                    check(endpoint.error == null) {
                        "$tweak boundary $value was rejected: ${endpoint.error}"
                    }
                    check(endpoint.validTransformTags == listOf(candidateTag)) {
                        "$tweak boundary $value was not retained: ${endpoint.validTransformTags}"
                    }
                }
            }
        }

        validateRangesAt(initialTag)
    }
}

private val continuousTransformTags = listOf(
    "t", "N", "z",
    "k",
    "e", "O",
    "b", "m",
    // Flipped chirality is a mirror and therefore has the same geometry-safe interval.
    "s", "g",
    "c",
)

// The range search is deliberately representative: default geometry for every fixed seed and
// operation is covered by the catalog validation suites, while this test exercises distinct
// symmetry/topology classes and every regular-star form without multiplying the expensive
// intersection search by mirror-equivalent and construction-equivalent cases.
private val representativeCatalogSeeds = listOf(
    Seed.Tetrahedron,
    Seed.RhombitruncatedIcosidodecahedron,
    Seed.GreatDodecahedron,
    Seed.GreatIcosahedron,
)

// Family construction itself is validated at structural low order and n=100 elsewhere. The range
// algorithm needs one non-degenerate member of each family rather than repeating the same search
// for every n.
private val representativeFamilySeeds = FamilySeeds.filter { seed ->
    seed.tag.toFamilySeedIdOrNull()?.n == 10
}

private fun List<Seed>.withFamily(family: SeedFamily): List<Seed> =
    filter { seed -> seed.tag.toFamilySeedIdOrNull()?.family == family }

private val outerExtremeTransformTags = continuousTransformTags.flatMap(String::outerExtremeTags)

private fun String.outerExtremeTags(): List<String> {
    val parsed = requireNotNull(parseTransformTag())
    var variants: List<Map<TransformTweak, Double>> = listOf(emptyMap())
    for ((tweak, range) in parsed.id.transformTweakRanges()) {
        variants = variants.flatMap { values ->
            listOf(range.min, range.max).map { value -> values + (tweak to value) }
        }
    }
    return variants.map { tweaks -> encodeTransformTag(parsed.id, tweaks) }
}

private fun Seed.continuousOrbitTransformTags(): List<String> =
    poly.availableOrbitTransforms.mapNotNull { transform ->
        when (transform) {
            is KisFace, is TruncateVertex, is RectifyVertex -> transform.tag
            else -> null
        }
    }

private fun Seed.outerExtremeOrbitTransformTags(): List<String> =
    continuousOrbitTransformTags().flatMap(String::outerExtremeTags)
