import polyhedra.common.poly.Seeds
import polyhedra.common.poly.TransformFEV
import polyhedra.common.poly.fev
import polyhedra.common.poly.times
import polyhedra.common.transform.Transforms
import polyhedra.common.transform.transformed
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
