package polyhedra.common.util

import polyhedra.common.*
import kotlin.math.*

interface Quat {
    val w: Double
    val x: Double
    val y: Double
    val z: Double
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


fun MutableQuat.multiplyFront(a: Double, b: Double, c: Double, d: Double) {
    val rw = a * this.w - b * this.x - c * this.y - d * this.z
    val rx = a * this.x + b * this.w + c * this.z - d * this.y
    val ry = a * this.y - b * this.z + c * this.w + d * this.x
    val rz = a * this.z + b * this.y - c * this.x + d * this.w
    this.w = rw
    this.x = rx
    this.y = ry
    this.z = rz
}

fun MutableQuat.multiplyFront(q: Quat) = multiplyFront(q.w, q.x, q.y, q.z)

// multiplies by a pure quaternion of the given vector
fun MutableQuat.multiplyFront(v: Vec3) {
    val rw = -v.x * x - v.y * y - v.z * z
    val rx = +v.x * w + v.y * z - v.z * y
    val ry = -v.x * z + v.y * w + v.z * x
    val rz = +v.x * y - v.y * x + v.z * w
    w = rw
    x = rx
    y = ry
    z = rz
}

fun Vec3.rotated(q: Quat): Vec3 {
    val r = MutableQuat(q.w, -q.x, -q.y, -q.z)
    r.multiplyFront(this)
    r.multiplyFront(q)
    return Vec3(r.x, r.y, r.z)
}

fun Quat.toAngles(): Vec3 {
    val x = atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y))
    val siny = 2 * (w * y - z * x);
    val y = if (siny.absoluteValue >= 1) (PI / 2) * siny.sign else asin(siny)
    val z = atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z));
    return Vec3(x, y, z)
}

fun Vec3.anglesToQuat(): MutableQuat {
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
    w approx q.w && x approx q.x && y approx q.y && z approx q.z

