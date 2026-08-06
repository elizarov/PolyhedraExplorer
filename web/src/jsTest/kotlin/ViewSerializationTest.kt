package polyhedra.web

import org.khronos.webgl.*
import polyhedra.model.util.*
import polyhedra.web.main.RootParams
import polyhedra.web.params.Param
import polyhedra.web.params.loadFromString
import polyhedra.web.poly.ViewContext
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewSerializationTest {
    @Test
    fun serializedViewIsAppliedWhenRenderingContextIsCreatedAfterReload() {
        val source = RootParams()
        source.render.view.rotate.rotate(0.4, -0.7, 0.2, Param.TargetValue)
        source.render.view.scale.updateValue(0.35, Param.TargetValue)
        val serialized = source.toString()
        assertTrue(serialized.contains("v(r("), serialized)

        val restored = RootParams()
        restored.loadFromString(serialized)
        val view = ViewContext(restored.render.view)
        val expectedAxis = Vec3(1.0, 0.0, 0.0)
            .rotated(restored.render.view.rotate.value) * 2.0.pow(restored.render.view.scale.value)

        assertEquals(expectedAxis.x, view.modelMatrix[0].toDouble(), tolerance)
        assertEquals(expectedAxis.y, view.modelMatrix[1].toDouble(), tolerance)
        assertEquals(expectedAxis.z, view.modelMatrix[2].toDouble(), tolerance)
        view.destroy()
    }

    private companion object {
        const val tolerance = 1e-5
    }
}
