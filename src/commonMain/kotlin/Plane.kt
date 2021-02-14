package polyhedra.common

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

fun Plane.normalFromOrigin(): Plane =
    if (d >= 0) this else Plane(-n, -d)