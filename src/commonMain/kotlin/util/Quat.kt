/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.util

import polyhedra.common.*
import kotlin.js.*
import kotlin.math.*

interface Quat {
    val x: Double
    val y: Double
    val z: Double
    val w: Double

    companion object {
        val ID: Quat = MutableQuat()
    }
}

data class MutableQuat(
    override var x: Double,
    override var y: Double,
    override var z: Double,
    override var w: Double,
) : Quat {
    override fun toString(): String =
        "Quat[${x.fmt}, ${y.fmt}, ${z.fmt}, ${w.fmt}]"
}

@JsName("MutableQuatId")
fun MutableQuat(): MutableQuat = MutableQuat(0.0, 0.0, 0.0, 1.0)

fun Quat.toMutableQuat(): MutableQuat =
    MutableQuat(x, y, z, w)

fun Quat(x: Double, y: Double, z: Double, w: Double): Quat =
    MutableQuat(x, y, z, w)

infix fun MutableQuat.by(q: Quat) = by(q.x, q.y, q.z, q.w)

fun MutableQuat.by(x: Double, y: Double, z: Double, w: Double) {
    this.x = x
    this.y = y
    this.z = z
    this.w = w
}

fun norm(x: Double, y: Double, z: Double, w: Double): Double =
    sqrt(sqr(x) + sqr(y) + sqr(z) + sqr(w))

val Quat.norm: Double
    get() = norm(x, y, z, w)

val Quat.unit: Quat get() {
   val n = norm
   if (n < EPS) return MutableQuat()
   return Quat(x / n, y / n, z / n, w / n)
}

operator fun Quat.plus(q: Quat): Quat =
    Quat(x + q.x, y + q.y, z + q.z, w  + q.w)

operator fun Quat.times(q: Quat): Quat =
    q.toMutableQuat().also { it.multiplyFront(this) }

operator fun Quat.times(a: Double): Quat =
    Quat(x * a, y * a, z * a, w * a)

operator fun Double.times(q: Quat): Quat = q * this

fun Vec3.toRotationAroundQuat(angle: Double): Quat =
    rotationAroundQuat(x, y, z, angle)

fun rotationAroundQuat(x: Double, y: Double, z: Double, angle: Double): Quat {
    val s = sin(angle * 0.5) / norm(x, y, z)
    val w = cos(angle * 0.5)
    return Quat(s * x, s * y, s * z, w)
}

fun MutableQuat.multiplyFront(x: Double, y: Double, z: Double, w: Double): Unit = by(
    w * this.x + x * this.w + y * this.z - z * this.y,
    w * this.y - x * this.z + y * this.w + z * this.x,
    w * this.z + x * this.y - y * this.x + z * this.w,
    w * this.w - x * this.x - y * this.y - z * this.z
)

fun MutableQuat.multiplyFront(q: Quat): Unit =
    multiplyFront(q.x, q.y, q.z, q.w)

// multiplies by a pure quaternion of the given vector
fun MutableQuat.multiplyFront(v: Vec3): Unit = by(
    +v.x * w + v.y * z - v.z * y,
    -v.x * z + v.y * w + v.z * x,
    +v.x * y - v.y * x + v.z * w,
    -v.x * x - v.y * y - v.z * z
)

fun Vec3.rotated(q: Quat): Vec3 {
    val r = MutableQuat(-q.x, -q.y, -q.z, q.w)
    r.multiplyFront(this)
    r.multiplyFront(q)
    return Vec3(r.x, r.y, r.z)
}

fun Quat.toAngles(): Vec3 {
    val sy = 2 * (w * y - z * x)
    if (sy.absoluteValue >= 1 - EPS)
        return Vec3(0.0, PI * 0.5 * sy.sign, 0.0)
    val sx = 1 - 2 * (x * x + y * y)
    val cx = 2 * (w * x + y * z)
    val sz = 1 - 2 * (y * y + z * z)
    val cz = 2 * (w * z + x * y)
    return Vec3(atan2(cx, sx), asin(sy), atan2(cz, sz))
}

fun Vec3.anglesToQuat(): Quat =
    anglesToQuat(x, y, z)

fun anglesToQuat(x: Double, y: Double, z: Double): Quat {
    val cy = cos(z * 0.5);
    val sy = sin(z * 0.5);
    val cp = cos(y * 0.5);
    val sp = sin(y * 0.5);
    val cr = cos(x * 0.5);
    val sr = sin(x * 0.5);
    return Quat(
        sr * cp * cy - cr * sp * sy,
        cr * sp * cy + sr * cp * sy,
        cr * cp * sy - sr * sp * cy,
        cr * cp * cy + sr * sp * sy
    )
}

infix fun Quat.approx(q: Quat): Boolean =
    w approx q.w && x approx q.x && y approx q.y && z approx q.z ||
    w approx -q.w && x approx -q.x && y approx -q.y && z approx -q.z

