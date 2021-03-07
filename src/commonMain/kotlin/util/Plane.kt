package polyhedra.common.util

import polyhedra.common.*

interface Plane : Vec3 {
    val d: Double
}

open class MutablePlane(
    x: Double,
    y: Double,
    z: Double,
    override var d: Double
) : MutableVec3(x, y, z), Plane {
    override fun toString(): String =
        "Plane(n=${super.toString()}, d=${d.fmt})"
}

fun Plane(n: Vec3, d: Double): Plane =
    MutablePlane(n.x, n.y, n.z, d)

// Plane through a point with a given normal
fun planeByNormalAndPoint(n: Vec3, p: Vec3): Plane {
    val u = n.unit
    return Plane(u, p * u)
}

// Plane through 3 points
fun plane3(a: Vec3, b: Vec3, c: Vec3): Plane =
    planeByNormalAndPoint(((c - a) cross (b - a)), a)

// Intersect 3 planes
fun planeIntersection(p: Plane, q: Plane, r: Plane): Vec3 {
    val d = det(
        p.x, p.y, p.z,
        q.x, q.y, q.z,
        r.x, r.y, r.z
    )
    val x = det(
        p.d, p.y, p.z,
        q.d, q.y, q.z,
        r.d, r.y, r.z
    )
    val y = det(
        p.x, p.d, p.z,
        q.x, q.d, q.z,
        r.x, r.d, r.z
    )
    val z = det(
        p.x, p.y, p.d,
        q.x, q.y, q.d,
        r.x, r.y, r.d
    )
    return Vec3(x / d, y / d, z / d)
}

operator fun Plane.contains(v: Vec3): Boolean =
    this * v approx d

// Projection of origin onto the plane
val Plane.tangentPoint: Vec3
    get() = this * d

// Intersection of a plane with a given vector
// Resulting vector is in the plane
fun Plane.intersection(v: Vec3) =
    v * (d / (this * v))