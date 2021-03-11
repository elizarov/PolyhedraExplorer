/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.util

import kotlin.math.*

data class Mat3(
    val x: Vec3,
    val y: Vec3,
    val z: Vec3
) {
    override fun toString(): String = "[$x, $y, $z]"

    companion object {
        val ID = Mat3(
            Vec3(1.0, 0.0, 0.0),
            Vec3(0.0, 1.0, 0.0),
            Vec3(0.0, 0.0, 1.0)
        )
    }
}

operator fun Mat3.plus(m: Mat3): Mat3 = Mat3(x + m.x, y + m.y, z + m.z)

operator fun Mat3.times(a: Double): Mat3 = Mat3(x * a, y * a, z * a)
operator fun Double.times(m: Mat3): Mat3 = m * this

operator fun Vec3.times(m: Mat3): Vec3 = Vec3(
    x * m.x.x + y * m.y.x + z * m.z.x,
    x * m.x.y + y * m.y.y + z * m.z.y,
    x * m.x.z + y * m.y.z + z * m.z.z
)

fun Vec3.crossMat(): Mat3 = Mat3(
    Vec3(0.0, -z, y),
    Vec3(z, 0.0, -x),
    Vec3(-y, x, 0.0)
)

infix fun Vec3.outerProd(u: Vec3) = Mat3(x * u, y * u, z * u)

fun rotationMat(u: Vec3, a: Double) =
    cos(a) * Mat3.ID + sin(a) * u.crossMat() + (1 - cos(a)) * (u outerProd u)

