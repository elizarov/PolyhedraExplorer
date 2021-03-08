package polyhedra.common

import polyhedra.common.util.*

enum class MidPoint { Tangent, Center, Closest }

// returns 0.0 when tangent point is a, 1.0 -- when it is b, or a fraction in between
fun tangentFraction(a: Vec3, b: Vec3): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val dz = b.z - a.z
    return  -(a.x * dx + a.y * dy + a.z * dz) / (sqr(dx) + sqr(dy) + sqr(dz))
}

// distance from origin to line A-B
fun tangentDistance(a: Vec3, b: Vec3): Double =
    tangentFraction(a, b).distanceAtSegment(a, b)

fun isTangentInSegment(a: Vec3, b: Vec3): Boolean =
    tangentFraction(a, b) in EPS..1 - EPS

fun midPointFraction(a: Vec3, b: Vec3, midPoint: MidPoint): Double {
    if (midPoint == MidPoint.Center) return 0.5
    val f = tangentFraction(a, b)
    if (f !in EPS..1 - EPS) { // !isTangentInSegment
        if (midPoint == MidPoint.Tangent) return 0.5
        return if (a.norm < b.norm + EPS) 0.0 else 1.0 // closest
    }
    return f
}

fun Edge.isTangentInSegment(): Boolean =
    isTangentInSegment(a, b)

fun Edge.midPointFraction(midPoint: MidPoint): Double =
    midPointFraction(a, b, midPoint)

fun Edge.midPoint(midPoint: MidPoint): Vec3 =
    midPointFraction(midPoint).atSegment(a, b)

fun Edge.tangentPoint(): Vec3 =
    tangentFraction(a, b).atSegment(a, b)



