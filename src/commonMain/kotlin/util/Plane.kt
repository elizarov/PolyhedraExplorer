package polyhedra.common.util

data class Plane(
    val n: Vec3,
    val d: Double
)

fun plane3(a: Vec3, b: Vec3, c: Vec3): Plane {
    val n = ((c - a) cross (b - a)).unit
    return Plane(n, a * n)
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