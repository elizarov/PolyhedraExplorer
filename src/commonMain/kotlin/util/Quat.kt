package polyhedra.common.util

import polyhedra.common.*
import kotlin.js.*
import kotlin.math.*

interface Quat {
    val w: Double
    val x: Double
    val y: Double
    val z: Double

    companion object {
        val ID: Quat = MutableQuat()
    }
}

data class MutableQuat(
    override var w: Double,
    override var x: Double,
    override var y: Double,
    override var z: Double,
) : Quat {
    override fun toString(): String =
        "Quat[${w.fmt}, ${x.fmt}, ${y.fmt}, ${z.fmt}]"
}

@JsName("MutableQuatId")
fun MutableQuat(): MutableQuat = MutableQuat(1.0, 0.0, 0.0, 0.0)

infix fun MutableQuat.by(q: Quat) = by(q.w, q.x, q.y, q.z)

fun MutableQuat.by(w: Double, x: Double, y: Double, z: Double) {
    this.w = w
    this.x = x
    this.y = y
    this.z = z
}

fun norm(w: Double, x: Double, y: Double, z: Double): Double =
    sqrt(sqr(w) + sqr(x) + sqr(y) + sqr(z))

val Quat.norm: Double
    get() = norm(w, x, y, z)

val Quat.unit: MutableQuat get() {
   val n = norm
   if (n < EPS) return MutableQuat()
   return MutableQuat(w / n, x / n, y / n, z / n)
}

operator fun Quat.times(a: Double): MutableQuat =
    MutableQuat(w * a, x * a, y * a, z * a)

operator fun Quat.plus(q: Quat): MutableQuat =
    MutableQuat(w  + q.w, x + q.x, y + q.y, z + q.z)

fun Vec3.toRotationAroundQuat(angle: Double): MutableQuat =
    rotationAroundQuat(x, y, z, angle)

fun rotationAroundQuat(x: Double, y: Double, z: Double, angle: Double): MutableQuat {
    val s = sin(angle * 0.5) / norm(x, y, z)
    val w = cos(angle * 0.5)
    return MutableQuat(w, s * x, s * y, s * z)
}

fun MutableQuat.rotateAroundFront(x: Double, y: Double, z: Double, angle: Double) {
    val s = sin(angle * 0.5) / norm(x, y, z)
    val w = cos(angle * 0.5)
    multiplyFront(w, s * x, s * y, s * z)
}

fun MutableQuat.multiplyFront(a: Double, b: Double, c: Double, d: Double) = by(
    a * w - b * x - c * y - d * z,
    a * x + b * w + c * z - d * y,
    a * y - b * z + c * w + d * x,
    a * z + b * y - c * x + d * w
)

fun MutableQuat.multiplyFront(q: Quat) = multiplyFront(q.w, q.x, q.y, q.z)

// multiplies by a pure quaternion of the given vector
fun MutableQuat.multiplyFront(v: Vec3) = by(
    -v.x * x - v.y * y - v.z * z,
    +v.x * w + v.y * z - v.z * y,
    -v.x * z + v.y * w + v.z * x,
    +v.x * y - v.y * x + v.z * w
)

fun Vec3.rotated(q: Quat): Vec3 {
    val r = MutableQuat(q.w, -q.x, -q.y, -q.z)
    r.multiplyFront(this)
    r.multiplyFront(q)
    return Vec3(r.x, r.y, r.z)
}

fun Quat.toAngles(): Vec3 {
    val sinx = 1 - 2 * (x * x + y * y)
    val cosx = 2 * (w * x + y * z)
    val siny = 2 * (w * y - z * x)
    val sinz = 1 - 2 * (y * y + z * z)
    val cosz = 2 * (w * z + x * y)
    if (siny.absoluteValue >= 1 - EPS)
        return Vec3(0.0, PI * 0.5 * siny.sign, 0.0)
    return Vec3(atan2(cosx, sinx), asin(siny), atan2(cosz, sinz))
}

fun Vec3.anglesToQuat(): MutableQuat =
    anglesToQuat(x, y, z)

fun anglesToQuat(x: Double, y: Double, z: Double): MutableQuat {
    val cy = cos(z * 0.5);
    val sy = sin(z * 0.5);
    val cp = cos(y * 0.5);
    val sp = sin(y * 0.5);
    val cr = cos(x * 0.5);
    val sr = sin(x * 0.5);
    return MutableQuat(
        cr * cp * cy + sr * sp * sy,
        sr * cp * cy - cr * sp * sy,
        cr * sp * cy + sr * cp * sy,
        cr * cp * sy - sr * sp * cy
    )
}

infix fun Quat.approx(q: Quat): Boolean =
    w approx q.w && x approx q.x && y approx q.y && z approx q.z ||
    w approx -q.w && x approx -q.x && y approx -q.y && z approx -q.z

