import polyhedra.common.util.*
import kotlin.math.*
import kotlin.test.*

class QuatTest {
    private val ux = Vec3(1.0, 0.0, 0.0)
    private val uy = Vec3(0.0, 1.0, 0.0)
    private val uz = Vec3(0.0, 0.0, 1.0)

    private val ra = PI / 2

    @Test
    fun testRotation() {
        assertApprox(ux, ux.rotated(ux.toRotationAroundQuat(ra)))
        assertApprox(-uz, ux.rotated(uy.toRotationAroundQuat(ra)))
        assertApprox(uy, ux.rotated(uz.toRotationAroundQuat(ra)))
        assertApprox(uz, uy.rotated(ux.toRotationAroundQuat(ra)))
        assertApprox(uy, uy.rotated(uy.toRotationAroundQuat(ra)))
        assertApprox(-ux, uy.rotated(uz.toRotationAroundQuat(ra)))
        assertApprox(-uy, uz.rotated(ux.toRotationAroundQuat(ra)))
        assertApprox(ux, uz.rotated(uy.toRotationAroundQuat(ra)))
        assertApprox(uz, uz.rotated(uz.toRotationAroundQuat(ra)))
    }

    @Test
    fun testToAngles() {
        assertAnglesApprox(ra * ux, ux.toRotationAroundQuat(ra).toAngles())
        assertAnglesApprox(ra * uy + PI * uz, uy.toRotationAroundQuat(ra).toAngles())
        assertAnglesApprox(ra * uz, uz.toRotationAroundQuat(ra).toAngles())
    }

    @Test
    fun testAnglesToQuat() {
        assertApprox(ux.toRotationAroundQuat(ra), (ux * ra).anglesToQuat())
        assertApprox(uy.toRotationAroundQuat(ra), (uy * ra).anglesToQuat())
        assertApprox(uz.toRotationAroundQuat(ra), (uz * ra).anglesToQuat())
    }

    private fun assertApprox(expect: Quat, actual: Quat) {
        if (expect approx actual) return
        fail("Expected: $expect, actual: $actual")
    }

    private fun assertApprox(expect: Vec3, actual: Vec3) {
        if (expect approx actual) return
        fail("Expected: $expect, actual: $actual")
    }

    private fun assertAnglesApprox(expect: Vec3, actual: Vec3) {
        if (expect anglesApprox actual) return
        fail("Expected: $expect, actual: $actual")
    }
}