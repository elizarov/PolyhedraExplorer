/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

import polyhedra.common.poly.*
import polyhedra.common.transform.*

import kotlin.test.*

class ValidatePolyhedra {
    private val expandingTransforms = Transforms.filter { it.fev != TransformFEV.ID }

    @Test
    fun validateSeeds() {
        testParameter("seed", Seeds) { seed ->
            val poly = seed.poly
            poly.validate()
            when (seed.type) {
                SeedType.Platonic -> {
                    check(poly.faceKinds.size == 1)
                    check(poly.edgeKinds.size == 1)
                    check(poly.vertexKinds.size == 1)
                }
                SeedType.Arhimedean -> {
//                    check(poly.vertexKinds.size == 1)
                }
            }
            // check FEV counts
            assertEquals(seed.fev, poly.fev())
        }
    }

    @Test
    fun validatePlatonicTransform() {
        testParameter("seed", Seeds.filter { it.type == SeedType.Platonic} ) { seed ->
            testParameter("transform", expandingTransforms) { transform ->
                seed.poly.transformed(transform).validate()
            }
        }
    }

    // Should be able to cantellate any Platonic seed as long as needed
    @Test
    fun validatePlatonicCantellationSequence() {
        testParameter("seed", Seeds.filter { it.type == SeedType.Platonic}) { seed ->
            testParameter("n", 1..5) { n ->
                seed.poly.transformed(List(n) { Transform.Cantellated }).validate()
            }
        }
    }

    @Test
    fun validatePlatonic2Transforms() {
        testParameter("seed", Seeds.filter { it.type == SeedType.Platonic}) { seed ->
            testParameter("transform1", expandingTransforms) { transform1 ->
                testParameter("transform2", expandingTransforms) { transform2 ->
                    if (isOkSequence(transform1, transform2)) {
                        seed.poly.transformed(transform1, transform2).validate()
                    }
                }
            }
        }
    }

    @Test
    fun validatePlatonic3Transforms() {
        testParameter("seed", Seeds.filter { it.type == SeedType.Platonic}) { seed ->
            testParameter("transform1", expandingTransforms) { transform1 ->
                testParameter("transform2", expandingTransforms) { transform2 ->
                    testParameter("transform3", expandingTransforms) { transform3 ->
                        if (isOkSequence(transform1, transform2, transform3)) {
                            seed.poly.transformed(transform1, transform2, transform3).validate()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun validateTransformedCanonical() {
        testParameter("seed", Seeds) { seed ->
            seed.poly.canonical().validate()
            testParameter("transform", expandingTransforms) { transform ->
                val transformed = seed.poly.transformed(transform)
                val isValid = try {
                    transformed.validate()
                    true
                } catch (e: Exception) { false }
                if (isValid) {
                    println("Checking Canonical $transform $seed, ${transformed.fev()}")
                    transformed.canonical().validate()
                }
            }
        }
        println("Total iterations $totalIterations")
    }
}

private fun isOkSequence(vararg transforms: Transform): Boolean {
    val t = transforms.toList()
    if (t.getOrNull(0) == Transform.Chamfered && t.getOrNull(1) in listOf(Transform.Chamfered, Transform.Bevelled)) return false
    if (t.lastIndexOf(Transform.Chamfered) >= 0 && t.size > 1) return false // chamfering can be used only with one other
    if (t.lastIndexOf(Transform.Bevelled) > 1) return false // bevelling must be first or second
    if (t.lastIndexOf(Transform.Snub) > 0) return false // Snub must be first
    return true
}