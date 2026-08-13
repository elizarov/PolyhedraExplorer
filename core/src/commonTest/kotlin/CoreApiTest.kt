package polyhedra.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import polyhedra.core.api.*
import polyhedra.core.poly.Cube
import polyhedra.core.poly.Seed
import polyhedra.core.poly.hasSameTopology
import polyhedra.core.poly.validateGeometry
import polyhedra.core.transform.*
import polyhedra.model.api.*
import polyhedra.model.poly.FEV
import polyhedra.model.poly.fev
import polyhedra.model.util.norm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreApiTest {
    @Test
    fun largerContinuousValuesMoveEveryNamedConstructionFurther() {
        val poly = Seed.Cube.poly

        fun transform(tag: String) = requireNotNull(tag.toTransformOrNull())
        fun assertIncreases(label: String, low: Double?, high: Double?) {
            assertTrue(requireNotNull(high) > requireNotNull(low), "$label must increase: $low -> $high")
        }

        assertIncreases(
            "Truncation depth",
            transform("t~d=0.5").truncationRatio(poly),
            transform("t~d=1.5").truncationRatio(poly),
        )
        assertIncreases(
            "Cantellation distance",
            transform("e~c=0.5").cantellationRatio(poly),
            transform("e~c=1.5").cantellationRatio(poly),
        )
        assertIncreases(
            "Bevel distance",
            transform("b~c=0.5").bevellingRatio(poly)?.cr,
            transform("b~c=1.5").bevellingRatio(poly)?.cr,
        )
        assertIncreases(
            "Bevel depth",
            transform("b~d=0.5").bevellingRatio(poly)?.tr,
            transform("b~d=1.5").bevellingRatio(poly)?.tr,
        )
        assertIncreases(
            "Snub inset",
            transform("s~i=0.5").snubbingRatio(poly)?.cr,
            transform("s~i=1.5").snubbingRatio(poly)?.cr,
        )
        assertIncreases(
            "Snub twist",
            transform("s~r=0.5").snubbingRatio(poly)?.sa,
            transform("s~r=1.5").snubbingRatio(poly)?.sa,
        )
        assertIncreases(
            "Chamfer width",
            transform("c~w=0.5").chamferingRatio(poly),
            transform("c").chamferingRatio(poly),
        )
        val vertexKind = poly.vertexKinds.keys.first()
        assertIncreases(
            "Targeted truncation depth",
            TruncateVertex(vertexKind, 0.5).targetRatio(poly),
            TruncateVertex(vertexKind, 1.0).targetRatio(poly),
        )
        assertIncreases(
            "Targeted rectification depth",
            RectifyVertex(vertexKind, 0.5).targetRatio(poly),
            RectifyVertex(vertexKind, 1.0).targetRatio(poly),
        )
    }

    @Test
    fun largerKisHeightProducesLargerSpikes() = runTest {
        val input = evaluateCore(CoreRequest(CoreState("I", emptyList(), "c"))).poly
        val low = evaluateCore(CoreRequest(CoreState("I", listOf("k~h=0.5"), "c")))
            .transformedPolys.single()
        val high = evaluateCore(CoreRequest(CoreState("I", listOf("k~h=1.5"), "c")))
            .transformedPolys.single()

        fun averageApexRadius(poly: polyhedra.model.poly.Polyhedron): Double =
            poly.vs.drop(input.vs.size).map { it.norm }.average()

        assertTrue(
            averageApexRadius(high) > averageApexRadius(low),
            "Increasing Kis height must move the apexes farther out",
        )
    }

    @Test
    fun lowGyroInsetIsRejectedWithoutExposingInvalidGeometry() = runTest {
        val response = evaluateCore(CoreRequest(CoreState("I", listOf("g~i=0.23"), "c")))

        assertEquals(CoreIssueCode.InvalidGeometry, response.error?.code)
        assertEquals(0, response.errorIndex)
        assertTrue(response.validTransformTags.isEmpty())
        response.poly.validateGeometry()
        val insetRange = response.transformTweakRanges.single()
            .single { it.tweak == TransformTweak.Inset }
        assertTrue(insetRange.min > 0.23, "Unsafe Gyro inset must be outside $insetRange")
    }

    @Test
    fun evaluatesAndAnimatesNonDefaultContinuousTransformParameters() = runTest {
        val defaultResponse = evaluateCore(CoreRequest(CoreState("C", listOf("t"), "c")))
        val tweakedResponse = evaluateCore(
            CoreRequest(
                state = CoreState("C", listOf("t~d=0.7"), "c"),
                previousState = CoreState("C", listOf("t"), "c"),
                animationDuration = 0.5,
            )
        )

        assertEquals(defaultResponse.poly.fev(), tweakedResponse.poly.fev())
        assertNotEquals(defaultResponse.poly.vs.first().x, tweakedResponse.poly.vs.first().x)
        assertEquals(listOf("t~d=0.7"), tweakedResponse.validTransformTags)
        assertEquals(1, tweakedResponse.animation.size)
        assertTrue(tweakedResponse.animation.single().previousPoly.hasSameTopology(
            tweakedResponse.animation.single().targetPoly
        ))
    }

    @Test
    fun evaluatesParameterizedMacrosAndPreservesTheirLogicalTags() = runTest {
        for (tag in listOf("k~h=0.8", "e~c=0.8", "b~d=0.8~c=0.9", "g'~i=0.9~r=0.8")) {
            val response = evaluateCore(CoreRequest(CoreState("C", listOf(tag), "c")))

            assertNull(response.error, tag)
            assertEquals(listOf(tag), response.validTransformTags)
        }
    }

    @Test
    fun animatesHeightChangesForOneKisFaceOrbit() = runTest {
        val initial = evaluateCore(CoreRequest(CoreState("tC", emptyList(), "c")))
        val kisTag = initial.availableOrbitTransforms.first().first { it.startsWith("k[") }
        val defaultKis = evaluateCore(CoreRequest(CoreState("tC", listOf(kisTag), "c")))
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("tC", listOf("$kisTag~h=0.7"), "c"),
                previousState = CoreState("tC", listOf(kisTag), "c"),
                animationDuration = 0.5,
            )
        )

        assertNull(response.error)
        assertEquals(listOf("$kisTag~h=0.7"), response.validTransformTags)
        assertTrue(defaultKis.poly.vs.indices.any { index ->
            val defaultVertex = defaultKis.poly.vs[index]
            val tweakedVertex = response.poly.vs[index]
            defaultVertex.x != tweakedVertex.x ||
                defaultVertex.y != tweakedVertex.y ||
                defaultVertex.z != tweakedVertex.z
        })
        assertEquals(1, response.animation.size)
        assertTrue(response.animation.single().previousPoly.hasSameTopology(
            response.animation.single().targetPoly
        ))
    }

    @Test
    fun evaluatesCompleteTransformPipeline() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("C", listOf("t"), "c"),
            )
        )

        assertEquals(14, response.poly.fs.size)
        assertEquals(36, response.poly.es.size)
        assertEquals(24, response.poly.vs.size)
        assertEquals(listOf("t"), response.validTransformTags)
        assertEquals(2, response.availableOrbitTransforms.size)
        assertEquals(null, response.error)
    }

    @Test
    fun recognizesTransformedPolyhedronAsCatalogSeed() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("I", listOf("t"), "c"),
                detectSeed = true,
            )
        )

        assertEquals("tI", response.recognizedSeedTag)
        assertEquals("Truncated Icosahedron", response.polyName)
    }

    @Test
    fun usesResolvedInGeneratedPolyhedronName() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("T", listOf("R"), "c"),
            )
        )

        assertNull(response.error)
        assertEquals("Resolved Tetrahedron", response.polyName)
    }

    @Test
    fun recognizesCatalogSeedReachedThroughEquivalentConstruction() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("O", listOf("a"), "c"),
                detectSeed = true,
            )
        )

        assertEquals("aC", response.recognizedSeedTag)
    }

    @Test
    fun suggestsSnubDodecahedronForDualPentagonalHexecontahedron() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("dsD", listOf("d"), "c"),
                detectSeed = true,
            )
        )

        assertEquals("sD", response.recognizedSeedTag)
    }

    @Test
    fun recognizesProperChiralityOfPentagonalHexecontahedronFromDualSnubIcosahedron() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("I", listOf("s", "d"), "c"),
                detectSeed = true,
            )
        )

        assertEquals("dsD'", response.recognizedSeedTag)

        val flippedResponse = evaluateCore(
            CoreRequest(
                state = CoreState("I", listOf("s'", "d"), "c"),
                detectSeed = true,
            )
        )
        assertEquals("dsD", flippedResponse.recognizedSeedTag)
    }

    @Test
    fun recognizesFlippedSnubTransformsAsFlippedCatalogSeeds() = runTest {
        for ((seedTag, recognizedSeedTag, polyName) in listOf(
            Triple("C", "sC'", "Snub' Cube"),
            Triple("D", "sD'", "Snub' Dodecahedron"),
        )) {
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState(seedTag, listOf("s'"), "c"),
                    detectSeed = true,
                )
            )

            assertEquals(recognizedSeedTag, response.recognizedSeedTag)
            assertEquals(polyName, response.polyName)
            assertEquals(listOf("s'"), response.validTransformTags)
            assertNull(response.error)
        }
    }

    @Test
    fun skipsCatalogDetectionUnlessExplicitlyRequested() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("I", listOf("t"), "c"),
                detectSeed = false,
            )
        )

        assertEquals(null, response.recognizedSeedTag)
    }

    @Test
    fun producesTopologyAnimationInsideCore() = runTest {
        val response = evaluateCore(
            CoreRequest(
                state = CoreState("T", listOf("t"), "c"),
                previousState = CoreState("T", emptyList(), "c"),
                animationDuration = 0.5,
            )
        )

        val animation = response.animation.single()
        assertEquals(0.5, animation.duration)
        assertTrue(animation.previousFraction <= 0.001)
        assertTrue(animation.targetFraction >= 0.999)
    }

    @Test
    fun responseRoundTripsAcrossJsonBoundary() = runTest {
        val response = evaluateCore(CoreRequest(CoreState("O", listOf("d"), "m")))
        val encoded = CoreJson.encodeToString(response)
        val decoded = CoreJson.decodeFromString<CoreResponse>(encoded)

        assertEquals(response.polyName, decoded.polyName)
        assertEquals(response.poly.fev(), decoded.poly.fev())
        assertNotNull(decoded.poly)
        assertEquals(
            response.poly.resolvedFaces.map { face -> face.cells to face.edges },
            decoded.poly.resolvedFaces.map { face -> face.cells to face.edges },
        )
        assertEquals(
            response.poly.resolvedFaces.flatMap { face ->
                face.vertices.map { vertex ->
                    listOf(vertex.position.x, vertex.position.y, vertex.position.z) to vertex.provenance
                }
            },
            decoded.poly.resolvedFaces.flatMap { face ->
                face.vertices.map { vertex ->
                    listOf(vertex.position.x, vertex.position.y, vertex.position.z) to vertex.provenance
                }
            },
        )
        assertEquals(response.geometryAnalysis, decoded.geometryAnalysis)
    }

    @Test
    fun geometryContractsAndProvenanceRoundTripAcrossJsonBoundary() {
        val analysis = CoreGeometryAnalysis(
            strongestContract = PolyhedronContract.RenderableImmersion,
            intersectionCounts = mapOf(
                SurfaceIntersectionClass.SelfCrossingFace to 12,
                SurfaceIntersectionClass.IntersectingFaces to 30,
            ),
        )
        val provenance = ResolvedElementProvenance(
            sourceVertexIds = listOf(2),
            sourceEdgeIds = listOf(7, 9),
            sourceFaceIds = listOf(3, 4),
            sourceCellIds = listOf(1),
            sourceSegmentPoints = listOf(SourceSegmentPoint(3, 2, 0.25)),
        )

        assertEquals(
            analysis,
            CoreJson.decodeFromString<CoreGeometryAnalysis>(CoreJson.encodeToString(analysis)),
        )
        assertEquals(
            provenance,
            CoreJson.decodeFromString<ResolvedElementProvenance>(CoreJson.encodeToString(provenance)),
        )
        assertTrue(analysis.hasIntersections)
    }

    @Test
    fun evaluatesNewConwayTransformsWithChiralityAndMonotonicProgress() = runTest {
        val progress = mutableListOf<CoreProgress>()
        val response = evaluateCore(
            CoreRequest(CoreState("T", listOf("p'", "w'", "q"), "c")),
            progress::add,
        )

        assertNull(response.error)
        assertEquals(listOf("p'", "w'", "q"), response.validTransformTags)
        assertEquals("Quinto Whirl' Propeller' Tetrahedron", response.polyName)
        assertEquals(FEV(496, 1260, 766), response.poly.fev())
        assertTrue(response.poly.isCanonical())
        assertStageProgress(progress, lastTransformIndex = 2)
    }

    @Test
    fun chiralityFlipDoesNotInterpolateThroughCollapsedGeometry() = runTest {
        for ((defaultTag, flippedTag) in listOf(
            "p" to "p'",
            "w" to "w'",
            "s~r=0.8" to "s'~r=0.8",
            "g~r=0.8" to "g'~r=0.8",
        )) {
            val response = evaluateCore(
                CoreRequest(
                    state = CoreState("C", listOf(flippedTag), "c"),
                    previousState = CoreState("C", listOf(defaultTag), "c"),
                    animationDuration = 0.5,
                )
            )

            assertNull(response.error)
            assertTrue(response.animation.isEmpty(), "$defaultTag -> $flippedTag")
        }
    }

    @Test
    fun evaluatesCantellateChamferSnubCanonicalChain() = runTest {
        val progress = mutableListOf<CoreProgress>()
        val response = evaluateCore(
            CoreRequest(
                CoreState(
                    seedTag = "tC",
                    transformTags = listOf("e", "c", "s", "o"),
                    scaleTag = "c",
                )
            ),
            progress::add,
        )

        assertEquals(null, response.error, "The complete transform chain must succeed")
        assertEquals(listOf("e", "c", "s", "o"), response.validTransformTags)
        assertEquals(1730, response.poly.fs.size)
        assertEquals(2880, response.poly.es.size)
        assertEquals(1152, response.poly.vs.size)
        assertTrue(response.poly.isCanonical(), "The output must satisfy the canonical representation invariants")
        assertStageProgress(progress, lastTransformIndex = 3)
        assertTrue(
            progress.any { it.transformIndex == 3 && it.done in 1..99 },
            "Canonicalization must report intermediate progress on the Canonical stage",
        )
    }

    private fun assertStageProgress(progress: List<CoreProgress>, lastTransformIndex: Int) {
        assertEquals((0..lastTransformIndex).toList(), progress.map(CoreProgress::transformIndex).distinct())
        for (transformIndex in 0..lastTransformIndex) {
            val stageProgress = progress.filter { it.transformIndex == transformIndex }.map(CoreProgress::done)
            assertEquals(0, stageProgress.first(), "Stage $transformIndex must announce itself before starting")
            assertEquals(100, stageProgress.last(), "Stage $transformIndex must report completion")
            assertTrue(
                stageProgress.zipWithNext().all { (previous, next) -> next >= previous },
                "Stage $transformIndex progress must be monotonic: $stageProgress",
            )
        }
    }
}
