import polyhedra.common.util.*
import kotlin.math.*
import kotlin.random.*
import kotlin.test.*

class QuatTest {
    private val ux = Vec3(1.0, 0.0, 0.0)
    private val uy = Vec3(0.0, 1.0, 0.0)
    private val uz = Vec3(0.0, 0.0, 1.0)

    private val ra = PI / 2 // 90 deg
    private val rb = PI / 3 // 60 deg
    private val rc = PI / 4 // 45 deg
    private val rd = PI / 6 // 30 deg

    private val testAngles = listOf(0.0, ra, rb, rc, rd, -ra, -rb, -rc, -rd)

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
        for (r in testAngles) {
            assertApprox(r * ux, ux.toRotationAroundQuat(r).toAngles())
            assertApprox(r * uy, uy.toRotationAroundQuat(r).toAngles())
            assertApprox(r * uz, uz.toRotationAroundQuat(r).toAngles())
        }
    }

    @Test
    fun testAnglesToQuat() {
        for (r in testAngles) {
            assertApprox(ux.toRotationAroundQuat(r), (ux * r).anglesToQuat())
            assertApprox(uy.toRotationAroundQuat(r), (uy * r).anglesToQuat())
            assertApprox(uz.toRotationAroundQuat(r), (uz * r).anglesToQuat())
        }
    }

    @Test
    fun testToAnglesAndBack() {
        val rnd = Random(1)
        repeat(10) {
            val original = rotationAroundQuat(
                rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble() * 2 * PI
            )
            val angles = original.toAngles()
            val quat = angles.anglesToQuat()
            assertApprox(original, quat)
        }
    }

    private fun assertApprox(expect: Quat, actual: Quat) {
        if (expect approx actual) return
        fail("Expected: $expect, actual: $actual")
    }

    private fun assertApprox(expect: Vec3, actual: Vec3) {
        if (expect approx actual) return
        fail("Expected: $expect, actual: $actual")
    }
}