package polyhedra.common.util

data class Plane(
    val n: Vec3,
    val d: Double
)

// Plane through a point with a given normal
fun planePN(p: Vec3, n: Vec3): Plane {
    val u = n.unit
    return Plane(u, p * u)
}

// Plane through 3 points
fun plane3(a: Vec3, b: Vec3, c: Vec3): Plane =
    planePN(a, ((c - a) cross (b - a)))

// Intersect 3 planes
fun planeIntersection(p: Plane, q: Plane, r: Plane): Vec3 {
    val a = p.n
    val b = q.n
    val c = r.n
    val d = det(
        a.x, a.y, a.z,
        b.x, b.y, b.z,
        c.x, c.y, c.z
    )
    val x = det(
        p.d, a.y, a.z,
        q.d, b.y, b.z,
        r.d, c.y, c.z
    )
    val y = det(
        a.x, p.d, a.z,
        b.x, q.d, b.z,
        c.x, r.d, c.z
    )
    val z = det(
        a.x, a.y, p.d,
        b.x, b.y, q.d,
        c.x, c.y, r.d
    )
    return Vec3(x / d, y / d, z / d)
}

operator fun Plane.contains(v: Vec3): Boolean = n * v approx d

fun Plane.dualPoint(r: Double): Vec3 =
    (r * r / d) * n

// Projection of origin onto the plane
val Plane.tangentPoint: Vec3
    get() = n * d

// Intersection of a plane with a given vector
// Resulting vector is in the plane
fun Plane.intersection(v: Vec3) =
    v * (d / (n * v))