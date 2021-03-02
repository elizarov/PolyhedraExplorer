package polyhedra.common.util

import polyhedra.common.*
import kotlin.math.*

data class Vec3(
    val x: Double,
    val y: Double,
    val z: Double
) {
    fun toPreciseString(): String = "[$x, $y, $z]"
    override fun toString(): String = "[${x.fmt}, ${y.fmt}, ${z.fmt}]"
}

fun norm(x: Double, y: Double, z: Double): Double =
    sqrt(sqr(x) + sqr(y) + sqr(z))

val Vec3.norm: Double
    get() = norm(x, y, z)

val Vec3.unit: Vec3
    get() {
        val norm = norm
        return if (abs(norm) < EPS) this else this / norm
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

infix fun Vec3.approx(u: Vec3): Boolean =
    x approx u.x && y approx u.y && z approx u.z