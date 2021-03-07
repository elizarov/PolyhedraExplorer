package polyhedra.common.util

import polyhedra.common.*
import kotlin.math.*

interface Vec3 {
    val x: Double
    val y: Double
    val z: Double
}

open class MutableVec3(
    override var x: Double = 0.0,
    override var y: Double = 0.0,
    override var z: Double = 0.0
) : Vec3 {
    constructor(v: Vec3) : this(v.x, v.y, v.z)
    override fun toString(): String = "[${x.fmt}, ${y.fmt}, ${z.fmt}]"
    fun setToZero() { x = 0.0; y = 0.0; z = 0.0 }
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

// when this == 0.0 -> result is a
// when this == 1.0 -> result is b
fun Double.atSegment(a: Vec3, b: Vec3): Vec3 = // a + this * (b - a)
    Vec3(a.x + this * (b.x - a.x), a.y + this * (b.y - a.y), a.z + this * (b.z - a.z))

// when this == 0.0 -> result is a.norm
// when this == 1.0 -> result is b.norm
fun Double.distanceAtSegment(a: Vec3, b: Vec3): Double = // norm(a + this * (b - a))
    norm(a.x + this * (b.x - a.x), a.y + this * (b.y - a.y), a.z + this * (b.z - a.z))

infix fun Vec3.approx(u: Vec3): Boolean =
    x approx u.x && y approx u.y && z approx u.z

// distance from this point to a line A-B
fun Vec3.distanceToLine(a: Vec3, b: Vec3): Double {
    val a0 = a - this
    val b0 = b - this
    return tangentDistance(a0, b0)
}