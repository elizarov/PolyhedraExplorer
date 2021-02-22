package polyhedra.test

import polyhedra.common.*

import org.junit.*

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
    if (t.drop(1).contains(Transform.Cantellated)) return false // cantellation must be first
    if (t.drop(1).contains(Transform.Bevelled)) return false // bevelling must be first
    if (t.drop(1).contains(Transform.Snub)) return false // Snub must be first
    return true
}