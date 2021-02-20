package polyhedra.common

import polyhedra.common.util.*

enum class MidPoint { Tangent, Center, Closest }

fun tangentFraction(a: Vec3, b: Vec3): Double {
    val vec = b - a
    return  -(a * vec) / (vec * vec)
}

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
    isTangentInSegment(a.pt, b.pt)

fun Edge.midPointFraction(midPoint: MidPoint): Double =
    midPointFraction(a.pt, b.pt, midPoint)

fun Edge.midPoint(midPoint: MidPoint): Vec3 =
    midPointFraction(midPoint).atSegment(a.pt, b.pt)



