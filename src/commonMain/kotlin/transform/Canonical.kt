package polyhedra.common.transform

import polyhedra.common.*
import polyhedra.common.util.*
import kotlin.math.*

private const val TARGET_TOLERANCE = 1e-12

var totalIterations = 0

// Algorithm from https://www.georgehart.com/virtual-polyhedra/canonical.html
fun Polyhedron.canonical(): Polyhedron {
    // copy vertices to mutate them
    val vs = vs.map { it.toMutableVertex() }
    // pre-scale to an average midRadius of 1
    val preScale = 1 / midradius
    for (v in vs) v *= preScale
    // canonicalize
    val dv = vs.map { MutableVec3() }
    val center = MutableVec3()
    val normSum = MutableVec3()
    val h = MutableVec3()
    var iterations = 0
    do {
        iterations++
        var ok = true
        // check all edges
        for (f in fs) {
            for (i in 0 until f.size) {
                val a = vs[f[i].id]
                val b = vs[f[(i + 1) % f.size].id]
                val tf = tangentFraction(a, b)
                check(!tf.isNaN())
                tf.atSegmentTo(h, a, b)
                val err = 1.0 - h.norm
                if (abs(err) < TARGET_TOLERANCE) continue // ok
                ok = false
                val factor = 0.95 * err
                dv[a.id].plusAssignMul(h, factor)
                dv[b.id].plusAssignMul(h, factor)
            }
        }
        // apply average of edge adjustments
        for (i in vs.indices) {
            vs[i].plusAssignMul(dv[i], 1.0 / vertexFaces[vs[i]]!!.size)
            dv[i].setToZero()
        }
        // compute current center of gravity
        for (i in vs.indices) {
            center += vs[i]
        }
        center /= vs.size.toDouble()
        // recenter all vertices
        for (i in vs.indices) {
            vs[i] -= center
        }
        center.setToZero()
        // check all faces & project vertices
        for (f in fs) {
            // find centroid of face vertices
            for (i in 0 until f.size) center += vs[f[i].id]
            center /= f.size.toDouble()
            // find sum cross-product of all face angles -> normal of the "average" plane
            for (i in 0 until f.size) {
                val a = vs[f[i].id]
                val b = vs[f[(i + 1) % f.size].id]
                crossCenteredAddTo(normSum, a, b, center)
            }
            normSum /= normSum.norm // normalize to unit vector
            // project vertices onto the resulting plane (if needed)
            val pd = normSum * center // plane distance
            for (v in f) {
                val a = vs[v.id]
                val vd = pd - normSum * a // vertex distance from plane
                if (abs(vd) < TARGET_TOLERANCE) continue
                ok = false
                dv[v.id].plusAssignMul(normSum, vd)
            }
            // clear temp vars
            center.setToZero()
            normSum.setToZero()
        }
        // apply average of projecting adjustments
        for (i in vs.indices) {
            vs[i].plusAssignMul(dv[i], 1.0 / vertexFaces[vs[i]]!!.size)
            dv[i].setToZero()
        }
    } while (!ok)
    println("Done in $iterations iterations")
    totalIterations += iterations
    // copy faces with new vertices
    val fs = fs.map { f ->
        MutableFace(f.id, f.fvs.map { vs[it.id] }, f.kind)
    }
    // rebuild polyhedron with new vertices and faces
    return Polyhedron(vs, fs)
}