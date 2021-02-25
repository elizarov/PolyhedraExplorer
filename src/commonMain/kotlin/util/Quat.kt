package polyhedra.common.util

import kotlin.math.*

interface Quat {
    val a: Double
    val b: Double
    val c: Double
    val d: Double
}

class MutableQuat(
    override var a: Double,
    override var b: Double,
    override var c: Double,
    override var d: Double,
) : Quat

fun rotationQuat(angle: Double, x: Double, y: Double, z: Double): MutableQuat {
    val s = sin(angle / 2)
    val a = cos(angle / 2)
    return MutableQuat(a, s * x, s * y, s * z)
}

fun rotationQuat(angle: Double, v: Vec3): MutableQuat = rotationQuat(angle, v.x, v.y, v.z)

fun MutableQuat.multiplyFront(quat: Quat) {
    val ra = quat.a * a - quat.b * b - quat.c * c - quat.d * d
    val rb = quat.a * b + quat.b * a + quat.c * d - quat.d * c
    val rc = quat.a * c - quat.b * d + quat.c * a + quat.d * b
    val rd = quat.a * d + quat.b * c - quat.c * b + quat.d * a
    a = ra
    b = rb
    c = rc
    d = rd
}

fun MutableQuat.rotateFront(angle: Double, x: Double, y: Double, z: Double) {
    val s = sin(angle / 2)
    val a1 = cos(angle / 2)
    val b1 = s * x
    val c1 = s * y
    val d1 = s * z
    val ra = a1 * a - b1 * b - c1 * c - d1 * d
    val rb = a1 * b + b1 * a + c1 * d - d1 * c
    val rc = a1 * c - b1 * d + c1 * a + d1 * b
    val rd = a1 * d + b1 * c - c1 * b + d1 * a
    a = ra
    b = rb
    c = rc
    d = rd
}

fun Vec3.rotated(q: Quat): Vec3 {
    val r = MutableQuat(q.a, -q.b, -q.c, -q.d)
    val t = MutableQuat(0.0, x, y, z)
    r.multiplyFront(t)
    r.multiplyFront(q)
    return Vec3(r.b, r.c, r.d)
}
