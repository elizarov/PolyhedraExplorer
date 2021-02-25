import polyhedra.common.util.*
import kotlin.math.*
import kotlin.test.*

class QuatTest {
    @Test
    fun testRotation() {
        val ux = Vec3(1.0, 0.0, 0.0)
        val uy = Vec3(0.0, 1.0, 0.0)
        val uz = Vec3(0.0, 0.0, 1.0)
        val ra = PI / 2
        assertApprox(ux, ux.rotated(rotationQuat(ra, ux)))
        assertApprox(-uz, ux.rotated(rotationQuat(ra, uy)))
        assertApprox(uy, ux.rotated(rotationQuat(ra, uz)))
        assertApprox(uz, uy.rotated(rotationQuat(ra, ux)))
        assertApprox(uy, uy.rotated(rotationQuat(ra, uy)))
        assertApprox(-ux, uy.rotated(rotationQuat(ra, uz)))
        assertApprox(-uy, uz.rotated(rotationQuat(ra, ux)))
        assertApprox(ux, uz.rotated(rotationQuat(ra, uy)))
        assertApprox(uz, uz.rotated(rotationQuat(ra, uz)))
    }

    private fun assertApprox(expect: Vec3, actual: Vec3) {
        if (expect approx actual) return
        fail("Expected: $expect, actual: $actual")
    }
}