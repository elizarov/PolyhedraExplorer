package polyhedra.core

import polyhedra.core.poly.Seeds
import polyhedra.model.poly.TransformFEV
import polyhedra.model.poly.fev
import polyhedra.model.poly.times
import polyhedra.core.transform.Transforms
import polyhedra.core.transform.transformed
import kotlin.test.Test
import kotlin.test.assertEquals

class TransformFevTest {
    @Test
    fun declaredCountMatricesMatchGeneratedTopology() {
        val cube = Seeds.single { it.tag == "C" }
        val topologyChangingTransforms = Transforms.filter { it.fev != null && it.fev !== TransformFEV.ID }
        for (transform in topologyChangingTransforms) {
            assertEquals(
                transform.fev!! * cube.fev,
                cube.poly.transformed(transform).fev(),
                "$transform $cube",
            )
        }
    }
}
