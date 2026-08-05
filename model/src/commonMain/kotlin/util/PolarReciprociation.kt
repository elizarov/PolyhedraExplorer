/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.util

fun Plane.dualPoint(r: Double): Vec3 =
    (r * r / d) * this

fun Vec3.dualPlane(r: Double): Plane {
    val n = norm
    return Plane(this / n, r * r / n)
}
    