import polyhedra.common.*

import kotlin.test.*

class ValidatePolyhedra {
    @Test
    fun validateSeeds() {
        testParameter("seed", Seeds) { seed ->
            seed.poly.validate()
        }
    }

    @Test
    fun validateTransform() {
        testParameter("seed", Seeds) { seed ->
            testParameter("transform", Transforms.filter { it != Transform.None }) { transform ->
                seed.poly.transformed(transform).validate()
            }
        }
    }

    // Should be able to cantellate any Platonic seed as long as needed
    @Test
    fun validateCantellationSequence() {
        testParameter("seed", Seeds) { seed ->
            testParameter("n", 1..5) { n ->
                seed.poly.transformed(List(n) { Transform.Cantellated }).validate()
            }
        }
    }

    @Test
    fun validate2Transforms() {
        testParameter("seed", Seeds) { seed ->
            testParameter("transform1", Transforms.filter { it != Transform.None }) { transform1 ->
                testParameter("transform2", Transforms.filter { it != Transform.None }) { transform2 ->
                    if (isOkSequence(transform1, transform2)) {
                        seed.poly.transformed(transform1, transform2).validate()
                    }
                }
            }
        }
    }

    @Test
    fun validate3Transforms() {
        testParameter("seed", Seeds) { seed ->
            testParameter("transform1", Transforms.filter { it != Transform.None }) { transform1 ->
                testParameter("transform2", Transforms.filter { it != Transform.None }) { transform2 ->
                    testParameter("transform3", Transforms.filter { it != Transform.None }) { transform3 ->
                        if (isOkSequence(transform1, transform2, transform3)) {
                            seed.poly.transformed(transform1, transform2, transform3).validate()
                        }
                    }
                }
            }
        }
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