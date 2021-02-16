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
                    seed.poly.transformed(listOf(transform1, transform2)).validate()
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
                        seed.poly.transformed(listOf(transform1, transform2, transform3)).validate()
                    }
                }
            }
        }
    }

    @Test
    fun validate4Transforms() {
        testParameter("seed", Seeds) { seed ->
            testParameter("transform1", Transforms.filter { it != Transform.None }) { transform1 ->
                testParameter("transform2", Transforms.filter { it != Transform.None }) { transform2 ->
                    testParameter("transform3", Transforms.filter { it != Transform.None }) { transform3 ->
                        testParameter("transform4", Transforms.filter { it != Transform.None }) { transform4 ->
                            seed.poly.transformed(listOf(transform1, transform2, transform3, transform4)).validate()
                        }
                    }
                }
            }
        }
    }
}