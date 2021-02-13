package polyhedra.test

import polyhedra.common.*

import org.junit.*

class ValidatePolyhedra {
    @Test
    fun validateSeeds() {
        for (seed in Seed.values()) {
            seed.poly.validate()
        }
    }
}