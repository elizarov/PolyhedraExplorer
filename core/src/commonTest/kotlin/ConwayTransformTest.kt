package polyhedra.core

import polyhedra.core.poly.Seeds
import polyhedra.core.poly.geometryFingerprint
import polyhedra.core.poly.reflected
import polyhedra.core.poly.validate
import polyhedra.core.transform.Transform
import polyhedra.core.transform.Propeller as PropellerTransform
import polyhedra.core.transform.Whirl as WhirlTransform
import polyhedra.core.transform.isCanonical
import polyhedra.core.transform.transformed
import polyhedra.model.poly.Chirality
import polyhedra.model.poly.FEV
import polyhedra.model.poly.fev
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConwayTransformTest {
    @Test
    fun transformsProduceValidDeclaredTopology() {
        val cube = Seeds.single { it.tag == "C" }.poly
        val cases = listOf(
            Transform.Propeller to FEV(30, 60, 32),
            Transform.PropellerFlipped to FEV(30, 60, 32),
            Transform.Whirl to FEV(30, 84, 56),
            Transform.WhirlFlipped to FEV(30, 84, 56),
            Transform.Quinto to FEV(30, 72, 44),
        )
        for ((transform, expectedFev) in cases) {
            val result = cube.transformed(transform)
            result.validate()
            assertEquals(expectedFev, result.fev(), transform.toString())
            val sourcedFaceKinds = requireNotNull(result.faceKindSources).map { it.kind }.toSet()
            assertTrue(result.faceKinds.keys.all { it in sourcedFaceKinds }, transform.toString())
        }
    }

    @Test
    fun chiralVariantsAreReflections() {
        val cube = Seeds.single { it.tag == "C" }.poly
        for ((default, flipped) in listOf(
            Transform.Propeller to Transform.PropellerFlipped,
            Transform.Whirl to Transform.WhirlFlipped,
        )) {
            val defaultResult = cube.transformed(default)
            val flippedResult = cube.transformed(flipped)
            assertFalse(defaultResult.geometryFingerprint().matches(flippedResult.geometryFingerprint()))
            assertTrue(defaultResult.reflected().geometryFingerprint().matches(flippedResult.geometryFingerprint()))
        }
    }

    @Test
    fun chiralityMetadataMatchesOperatorDefinitions() {
        assertEquals(Chirality.Default, (Transform.Propeller as PropellerTransform).chirality)
        assertEquals(Chirality.Flipped, (Transform.PropellerFlipped as PropellerTransform).chirality)
        assertEquals(Chirality.Default, (Transform.Whirl as WhirlTransform).chirality)
        assertEquals(Chirality.Flipped, (Transform.WhirlFlipped as WhirlTransform).chirality)
    }

    @Test
    fun transformsReturnCanonicalRealizations() {
        val cube = Seeds.single { it.tag == "C" }.poly
        for (transform in listOf(Transform.Propeller, Transform.Whirl, Transform.Quinto)) {
            assertTrue(cube.transformed(transform).isCanonical(), transform.toString())
        }
    }

    @Test
    fun repeatedWhirlRemainsValid() {
        var poly = Seeds.single { it.tag == "T" }.poly
        repeat(3) { poly = poly.transformed(Transform.Whirl) }

        poly.validate()
        assertEquals(FEV(688, 2058, 1372), poly.fev())
    }
}
