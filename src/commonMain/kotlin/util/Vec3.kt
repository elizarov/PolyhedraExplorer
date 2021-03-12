/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.util

import kotlinx.serialization.*
import polyhedra.common.poly.*
import kotlin.math.*

interface Vec3 {
    val x: Double
    val y: Double
    val z: Double
}

@Serializable
open class MutableVec3(
    override var x: Double = 0.0,
    override var y: Double = 0.0,
    override var z: Double = 0.0
) : Vec3 {
    constructor(v: Vec3) : this(v.x, v.y, v.z)
    override fun toString(): String = "[${x.fmt}, ${y.fmt}, ${z.fmt}]"
}

fun MutableVec3.setToZero() {
    x = 0.0
    y = 0.0
    z = 0.0
}

fun MutableVec3.set(x: Double, y: Double, z: Double) {
    this.x = x
    this.y = y
    this.z = z
}

fun Vec3.toPreciseString(): String =
    "[$x, $y, $z]"

fun Vec3(x: Double, y: Double, z: Double): Vec3 = MutableVec3(x, y, z)

fun Vec3.toMutableVec3(): MutableVec3 = MutableVec3(x, y, z)

fun norm(x: Double, y: Double, z: Double): Double =
    sqrt(sqr(x) + sqr(y) + sqr(z))

val Vec3.norm: Double
    get() = norm(x, y, z)

val Vec3.unit: Vec3
    get() {
        val norm = norm
        return if (abs(norm) < EPS) this else this / norm
    }

operator fun MutableVec3.timesAssign(a: Double) {
    x *= a
    y *= a
    z *= a
}

operator fun MutableVec3.divAssign(a: Double) {
    x /= a
    y /= a
    z /= a
}

operator fun MutableVec3.plusAssign(u: Vec3) {
    x += u.x
    y += u.y
    z += u.z
}

fun MutableVec3.plusAssignMul(u: Vec3, a: Double) {
    x += u.x * a
    y += u.y * a
    z += u.z * a
}

operator fun MutableVec3.minusAssign(u: Vec3) {
    x -= u.x
    y -= u.y
    z -= u.z
}

operator fun Vec3.plus(u: Vec3): Vec3 = Vec3(x + u.x, y + u.y, z + u.z)
operator fun Vec3.minus(u: Vec3): Vec3 = Vec3(x - u.x, y - u.y, z - u.z)

operator fun Vec3.times(u: Vec3): Double = x * u.x + y * u.y + z * u.z
operator fun Vec3.times(a: Double): Vec3 = Vec3(x * a, y * a, z * a)
operator fun Double.times(u: Vec3): Vec3 = u * this

operator fun Vec3.div(d: Double): Vec3 = Vec3(x / d, y / d, z / d)
operator fun Vec3.unaryMinus(): Vec3 = Vec3(-x, -y, -z)

infix fun Vec3.cross(u: Vec3) = Vec3(
    y * u.z - z * u.y,
    -x * u.z + z * u.x,
     x * u.y - y * u.x
)

// dest += (a - c) cross (b - c)
fun crossCenteredAddTo(dest: MutableVec3, a: Vec3, b: Vec3, c: Vec3): Vec3 {
    val ax = a.x - c.x
    val ay = a.y - c.y
    val az = a.z - c.z
    val bx = b.x - c.x
    val by = b.y - c.y
    val bz = b.z - c.z
    dest.x += ay * bz - az * by
    dest.y += -ax * bz + az * bx
    dest.z += ax * by - ay * bx
    return dest
}

// when this == 0.0 -> result is a
// when this == 1.0 -> result is b
fun Double.atSegmentTo(dest: MutableVec3, a: Vec3, b: Vec3): Vec3 { // a + this * (b - a)
    val f = this
    dest.set(a.x + f * (b.x - a.x), a.y + f * (b.y - a.y), a.z + f * (b.z - a.z))
    return dest
}

// when this == 0.0 -> result is a
// when this == 1.0 -> result is b
fun Double.atSegment(a: Vec3, b: Vec3): Vec3 = // a + this * (b - a)
    atSegmentTo(MutableVec3(), a, b)

// when this == 0.0 -> result is a.norm
// when this == 1.0 -> result is b.norm
fun Double.distanceAtSegment(a: Vec3, b: Vec3): Double { // norm(a + this * (b - a))
    val f = this
    return norm(a.x + f * (b.x - a.x), a.y + f * (b.y - a.y), a.z + f * (b.z - a.z))
}

infix fun Vec3.approx(u: Vec3): Boolean =
    x approx u.x && y approx u.y && z approx u.z

// distance from this point to a line A-B
fun Vec3.distanceToLine(a: Vec3, b: Vec3): Double {
    val a0 = a - this
    val b0 = b - this
    return tangentDistance(a0, b0)
}