package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.poly.FamilySeeds
import polyhedra.core.poly.Seed
import polyhedra.core.poly.Seeds
import polyhedra.core.poly.validateMeshGeometry
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
    fun geometryDerivedExtremesAreValidForEveryCatalogSeed() = runTest {
        validateDynamicRanges(Seeds)
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
    fun outerExplorationExtremesNeverExposeInvalidGeometryForCatalogSeeds() = runTest {
        validateOuterExtremes(Seeds)
    }

    @Test
    fun outerExplorationExtremesNeverExposeInvalidGeometryForPrisms() = runTest {
        validateOuterExtremes(FamilySeeds.withFamily(SeedFamily.Prism))
    }

    @Test
    fun outerExplorationExtremesNeverExposeInvalidGeometryForAntiprisms() = runTest {
        validateOuterExtremes(FamilySeeds.withFamily(SeedFamily.Antiprism))
    }

    @Test
    fun outerExplorationExtremesNeverExposeInvalidGeometryForPyramids() = runTest {
        validateOuterExtremes(FamilySeeds.withFamily(SeedFamily.Pyramid))
    }

    @Test
    fun outerExplorationExtremesNeverExposeInvalidGeometryForBipyramids() = runTest {
        validateOuterExtremes(FamilySeeds.withFamily(SeedFamily.Bipyramid))
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
                    response.poly.validateMeshGeometry()
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
            response.poly.validateMeshGeometry()
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
                    endpoint.poly.validateMeshGeometry()
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
    "s", "s'", "g", "g'",
    "c",
)

// Family geometry changes continuously with n. Exercise every catalog seed and the structural,
// low-order, mid-range, and maximum members of each parameterized family in the costlier range test.
private val representativeFamilySeeds = FamilySeeds.filter { seed ->
    seed.tag.toFamilySeedIdOrNull()?.n in setOf(3, 4, 5, 10, 100)
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
