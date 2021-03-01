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
    if (t.lastIndexOf(Transform.Bevelled) > 1) return false // bevelling must be first or second
    if (t.lastIndexOf(Transform.Snub) > 0) return false // Snub must be first
    return true
}