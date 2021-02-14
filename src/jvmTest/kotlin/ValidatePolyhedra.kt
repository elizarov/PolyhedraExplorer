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
    fun validateMultipleRectifiedSeeds() {
        testParameter("seed", Seeds) { seed ->
            var poly = seed.poly
            testParameter("n", 1..4) { n ->
                poly = poly.rectified()
                poly.validate()
            }
        }
    }
}