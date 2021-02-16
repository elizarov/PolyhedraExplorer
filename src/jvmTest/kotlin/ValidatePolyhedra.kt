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
    fun validateMultipleTransforms() {
        testParameter("seed", Seeds) { seed ->
            testParameter("transform", Transforms.filter { it != Transform.None }) {
                var poly = seed.poly
                testParameter("n", 1..4) { n ->
                    poly = poly.rectified()
                    poly.validate()
                }
            }
        }
    }
}