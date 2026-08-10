package polyhedra.core

import kotlinx.coroutines.test.runTest
import polyhedra.core.api.evaluateCore
import polyhedra.core.transform.Transform
import polyhedra.model.api.CoreAnimationStep
import polyhedra.model.api.CoreRequest
import polyhedra.model.api.CoreResponse
import polyhedra.model.api.CoreState
import polyhedra.model.api.TransformMacros
import polyhedra.model.poly.size
import polyhedra.model.util.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransformAnimationTest {
    @Test
    fun replacingIdentityCanonicalWithKisSkipsTheNoopAndAnimatesKisIn() = runTest {
        val previous = state(listOf("o"), seedTag = "I")
        val current = state(listOf("k"), seedTag = "I")

        val animation = assertAnimated("Canonical to Kis", current, previous).animation

        assertEquals(1, animation.size)
        assertEquals(ANIMATION_DURATION, animation.single().duration, 1e-9)
    }

    @Test
    fun identityCanonicalAndNoTransformProduceTheSameMacroAnimation() = runTest {
        val none = state(emptyList(), seedTag = "I")
        val canonical = state(listOf("o"), seedTag = "I")

        for (macro in TransformMacros) {
            val transformed = state(listOf(macro.tag), seedTag = "I")
            assertEquivalentAnimation(
                "Canonical to ${macro.displayName}",
                evaluate(transformed, none).animation,
                evaluate(transformed, canonical).animation,
            )
            assertEquivalentAnimation(
                "${macro.displayName} to Canonical",
                evaluate(none, transformed).animation,
                evaluate(canonical, transformed).animation,
            )
        }
    }

    @Test
    fun defaultReplacementUsesFullDurationForOutAndInButCompatibleCutsMorphDirectly() = runTest {
        val outAndIn = evaluate(state(listOf("k")), state(listOf("p")))
        assertValidAnimation("Propeller to Kis", outAndIn)
        assertEquals(ANIMATION_DURATION * 2.0, outAndIn.animation.sumOf(CoreAnimationStep::duration), 1e-9)

        val direct = evaluate(state(listOf("a")), state(listOf("t")))
        assertValidAnimation("Truncated to Rectified", direct)
        assertEquals(ANIMATION_DURATION, direct.animation.sumOf(CoreAnimationStep::duration), 1e-9)
        assertEquals(1, direct.animation.size)
    }

    @Test
    fun everyStandardOperationPairAnimatesOutAndInUnlessItIsAChiralityFlip() = runTest {
        val operationTags = buildSet {
            Transform.Transforms.filterNot { it == Transform.None }.mapTo(this, Transform::tag)
            TransformMacros.mapTo(this) { it.tag }
            addAll(listOf("s'", "p'", "w'", "g'"))
        }
        val chiralityFlips = setOf("s", "p", "w", "g")

        for (previousTag in operationTags) {
            for (currentTag in operationTags) {
                if (previousTag == currentTag) continue
                val previous = state(listOf(previousTag))
                val current = state(listOf(currentTag))
                val sameChiralOperation = previousTag.removeSuffix("'") == currentTag.removeSuffix("'") &&
                    previousTag.removeSuffix("'") in chiralityFlips
                if (sameChiralOperation) {
                    assertTrue(evaluate(current, previous).animation.isEmpty(), "$previousTag to $currentTag")
                } else {
                    val response = evaluate(current, previous)
                    assertValidAnimation("$previousTag to $currentTag", response)
                    val totalDuration = response.animation.sumOf(CoreAnimationStep::duration)
                    assertTrue(
                        totalDuration in ANIMATION_DURATION..(ANIMATION_DURATION * 2.0),
                        "$previousTag to $currentTag duration $totalDuration",
                    )
                }
            }
        }
    }

    @Test
    fun orbitTargetedCutsUseDirectMorphsOnOneOrbitAndOutInAcrossOrbits() = runTest {
        val prefix = state(listOf("t", "t"))
        val available = evaluate(prefix).availableOrbitTransforms.last()
        val truncateTags = available.filter { tag -> tag.startsWith("t[") }
        val rectifyTags = available.filter { tag -> tag.startsWith("a[") }
        assertTrue(truncateTags.size > 1)
        assertTrue(rectifyTags.size > 1)

        fun String.target() = substringAfter('[').substringBefore(']')

        val truncate = truncateTags.first()
        val matchingRectify = rectifyTags.first { tag -> tag.target() == truncate.target() }
        val otherTruncate = truncateTags.first { tag -> tag.target() != truncate.target() }
        val sameOrbit = evaluate(
            state(prefix.transformTags + matchingRectify),
            state(prefix.transformTags + truncate),
        )
        assertValidAnimation("selective Truncated to Rectified on one orbit", sameOrbit)
        assertEquals(ANIMATION_DURATION, sameOrbit.animation.sumOf(CoreAnimationStep::duration), 1e-9)
        assertEquals(1, sameOrbit.animation.size)

        val differentOrbit = evaluate(
            state(prefix.transformTags + otherTruncate),
            state(prefix.transformTags + truncate),
        )
        assertValidAnimation("selective Truncated target change", differentOrbit)
        assertEquals(ANIMATION_DURATION * 2.0, differentOrbit.animation.sumOf(CoreAnimationStep::duration), 1e-9)

        assertAnimated(
            "apply selective Truncated",
            state(prefix.transformTags + truncate),
            prefix,
        )
        assertAnimated(
            "remove selective Truncated",
            prefix,
            state(prefix.transformTags + truncate),
        )
    }

    @Test
    fun everyAnimatablePrimitiveAppliesAndRemovesInBothChiralities() = runTest {
        val cases = listOf(
            PrimitiveCase("Truncated", "t"),
            PrimitiveCase("Rectified", "a"),
            PrimitiveCase("Cantellated", "e"),
            PrimitiveCase("Dual", "d"),
            PrimitiveCase("Bevelled", "b"),
            PrimitiveCase("Snub", "s"),
            PrimitiveCase("Snub'", "s'"),
            PrimitiveCase("Propeller", "p"),
            PrimitiveCase("Propeller'", "p'"),
            PrimitiveCase("Whirl", "w"),
            PrimitiveCase("Whirl'", "w'"),
            PrimitiveCase("Quinto", "q"),
            PrimitiveCase("Chamfered", "c"),
            PrimitiveCase("Canonical", "o", baseTags = listOf("t~d=0.7")),
        )
        assertEquals(
            Transform.Transforms.filterNot { it == Transform.None }.mapTo(linkedSetOf(), Transform::tag),
            cases.mapTo(linkedSetOf(), PrimitiveCase::tag),
        )

        for (case in cases) {
            val base = state(case.baseTags)
            val transformed = state(case.baseTags + case.tag)
            assertAnimated("apply ${case.name}", transformed, base)
            assertAnimated("remove ${case.name}", base, transformed)
        }
    }

    @Test
    fun conwayTransformAnimationStartsAsAnExactSubdivisionOfTheInputSurface() = runTest {
        for (scaleTag in listOf("i", "m", "c")) {
            val baseState = state(emptyList()).copy(scaleTag = scaleTag)
            val base = evaluate(baseState)
            for (tag in listOf("p", "p'", "w", "w'", "q")) {
                val response = evaluate(
                    state = state(listOf(tag)).copy(scaleTag = scaleTag),
                    previousState = baseState,
                )
                val start = response.animation.single().previousPoly

                assertTrue(
                    start.vs.all { vertex -> base.poly.fs.any { face -> vertex in face } },
                    "$tag at $scaleTag scale",
                )
            }
        }
    }

    @Test
    fun propellerOnIcosahedronOpensFromTheOriginalFaceShape() = runTest {
        val baseState = state(emptyList(), seedTag = "I")
        val base = evaluate(baseState).poly
        val maxEdgeLength = base.es.maxOf { edge -> (edge.a - edge.b).norm }

        for (tag in listOf("p", "p'")) {
            val start = evaluate(state(listOf(tag), seedTag = "I"), baseState)
                .animation.single().previousPoly

            assertTrue(
                start.vs.all { vertex ->
                    base.vs.any { sourceVertex ->
                        (vertex - sourceVertex).norm <= maxEdgeLength * 0.02
                    }
                },
                "$tag must begin with its inserted vertices collapsed near source vertices",
            )
        }
    }

    @Test
    fun whirlAndQuintoOnIcosahedronOpenFromCollapsedCentralFaces() = runTest {
        val baseState = state(emptyList(), seedTag = "I")
        val base = evaluate(baseState).poly
        val faceCenters = base.fs.map { face ->
            face.fvs.fold(Vec3(0.0, 0.0, 0.0)) { sum, vertex -> sum + vertex } /
                face.fvs.size.toDouble()
        }
        val maxEdgeLength = base.es.maxOf { edge -> (edge.a - edge.b).norm }
        val cases = listOf(
            "w" to base.vs.size + base.directedEdges.size,
            "w'" to base.vs.size + base.directedEdges.size,
            "q" to base.vs.size + base.es.size,
        )

        for ((tag, innerVertexOffset) in cases) {
            val start = evaluate(state(listOf(tag), seedTag = "I"), baseState)
                .animation.single().previousPoly
            val innerVertices = start.vs.drop(innerVertexOffset)

            assertTrue(innerVertices.isNotEmpty(), "$tag must contain inner vertices")
            assertTrue(
                innerVertices.all { vertex ->
                    faceCenters.any { center ->
                        (vertex - center).norm <= maxEdgeLength * 0.02
                    }
                },
                "$tag must begin with its central faces collapsed near source-face centers",
            )
        }
    }

    @Test
    fun macrosAnimateAllExpandedComponentsOnOneSharedClock() = runTest {
        val macroTags = TransformMacros.mapTo(linkedSetOf()) { it.tag } + "g'"
        for (scaleTag in listOf("i", "m", "c")) {
            val base = state(emptyList()).copy(scaleTag = scaleTag)
            val basePoly = evaluate(base).poly
            for (tag in macroTags) {
                val transformed = state(listOf(tag)).copy(scaleTag = scaleTag)
                val applied = assertAnimated("apply macro $tag at $scaleTag scale", transformed, base)
                val removed = assertAnimated("remove macro $tag at $scaleTag scale", base, transformed)

                assertEquals(1, applied.animation.size, "apply macro $tag")
                assertEquals(1, removed.animation.size, "remove macro $tag")
                if (scaleTag == "c" && tag !in setOf("e", "b")) {
                    val step = applied.animation.first()
                    assertTrue(
                        step.previousPoly.vs.indices.all { vertexIndex ->
                            val vertex = step.positionAt(vertexIndex, 0.0)
                            basePoly.fs.minOf { face -> abs(face * vertex - face.d) } < 0.01
                        },
                        "fused macro $tag must extrapolate to the input surface at $scaleTag scale",
                    )
                }
            }
        }
    }

    @Test
    fun nonDefaultKisHeightIsIncludedInTheSingleFusedAnimation() = runTest {
        val base = state(emptyList())
        val transformed = state(listOf("k~h=0.75"))

        val applied = assertAnimated("apply tweaked Kis", transformed, base).animation
        val removed = assertAnimated("remove tweaked Kis", base, transformed).animation
        assertEquals(1, applied.size)
        assertEquals(1, removed.size)
    }

    @Test
    fun dropAndSelectiveKisInsertionRemainImmediate() = runTest {
        val base = state(listOf("t", "t"))
        val available = evaluate(base).availableOrbitTransforms.last()
        val tags = listOf(
            available.first { it.startsWith("x[") },
            available.first { it.startsWith("k[") },
        )
        for (tag in tags) {
            val transformed = state(base.transformTags + tag)
            assertTrue(evaluate(transformed, base).animation.isEmpty(), "apply $tag")
            assertTrue(evaluate(base, transformed).animation.isEmpty(), "remove $tag")
        }
    }

    private suspend fun assertAnimated(
        label: String,
        state: CoreState,
        previousState: CoreState,
    ): CoreResponse {
        val response = evaluate(state, previousState)
        assertValidAnimation(label, response)
        assertEquals(ANIMATION_DURATION, response.animation.sumOf(CoreAnimationStep::duration), 1e-9, label)
        return response
    }

    private fun assertEquivalentAnimation(
        label: String,
        expected: List<CoreAnimationStep>,
        actual: List<CoreAnimationStep>,
    ) {
        assertEquals(expected.size, actual.size, label)
        expected.zip(actual).forEachIndexed { index, (expectedStep, actualStep) ->
            assertEquals(expectedStep.duration, actualStep.duration, 1e-9, "$label step $index duration")
            assertEquals(expectedStep.previousFraction, actualStep.previousFraction, 1e-9, "$label step $index start")
            assertEquals(expectedStep.targetFraction, actualStep.targetFraction, 1e-9, "$label step $index end")
            assertTrue(
                expectedStep.previousPoly.hasSameAnimationGeometry(actualStep.previousPoly) &&
                    expectedStep.targetPoly.hasSameAnimationGeometry(actualStep.targetPoly),
                "$label step $index geometry",
            )
        }
    }

    private fun assertValidAnimation(label: String, response: CoreResponse) {
        assertTrue(response.animation.isNotEmpty(), label)
        response.animation.forEachIndexed { index, step ->
            assertTrue(
                step.previousPoly.hasCompatibleAnimationBuffers(step.targetPoly),
                "$label, step $index has incompatible topology",
            )
            assertTrue(step.previousFraction in 0.0..1.0, "$label, step $index previous fraction")
            assertTrue(step.targetFraction in 0.0..1.0, "$label, step $index target fraction")
        }
    }

    private suspend fun evaluate(
        state: CoreState,
        previousState: CoreState? = null,
    ): CoreResponse = evaluateCore(
        CoreRequest(
            state = state,
            previousState = previousState,
            animationDuration = previousState?.let { ANIMATION_DURATION },
            calculateTweakRanges = false,
        )
    )

    private fun state(tags: List<String>, seedTag: String = "T") =
        CoreState(seedTag, tags, "c")

    private fun polyhedra.model.poly.Polyhedron.hasCompatibleAnimationBuffers(
        other: polyhedra.model.poly.Polyhedron,
    ): Boolean =
        vs.size == other.vs.size &&
            es.size == other.es.size &&
            fs.size == other.fs.size &&
            fs.indices.all { index -> fs[index].size == other.fs[index].size }

    private fun polyhedra.model.poly.Polyhedron.hasSameAnimationGeometry(
        other: polyhedra.model.poly.Polyhedron,
    ): Boolean =
        hasCompatibleAnimationBuffers(other) &&
            vs.indices.all { index -> vs[index] approx other.vs[index] }

    private fun CoreAnimationStep.positionAt(vertexIndex: Int, fraction: Double): Vec3 {
        val previousWeight = (fraction - targetFraction) / (previousFraction - targetFraction)
        val targetWeight = (fraction - previousFraction) / (targetFraction - previousFraction)
        return previousPoly.vs[vertexIndex] * previousWeight + targetPoly.vs[vertexIndex] * targetWeight
    }

    private data class PrimitiveCase(
        val name: String,
        val tag: String,
        val baseTags: List<String> = emptyList(),
    )

    private companion object {
        const val ANIMATION_DURATION = 0.6
    }
}
