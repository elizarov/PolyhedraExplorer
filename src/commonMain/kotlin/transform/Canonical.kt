package polyhedra.common.transform

import polyhedra.common.*
import polyhedra.common.util.*
import kotlin.math.*

private const val TARGET_TOLERANCE = 1e-13

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
    var interactions = 0
    do {
        interactions++
        var ok = true
        // check all edges
        for (f in fs) {
            for (i in 0 until f.size) {
                val a = vs[f[i].id]
                val b = vs[f[(i + 1) % f.size].id]
                val tf = tangentFraction(a, b)
                check(!tf.isNaN())
                val d = tf.distanceAtSegment(a, b)
                val err = 1.0 - d
                if (abs(err) < TARGET_TOLERANCE) continue // ok
                ok = false
                val adj = 0.5 * err * tf.atSegment(a, b)
                dv[a.id] += adj
                dv[b.id] += adj
            }
        }
        // apply average of edge adjustments
        for (i in vs.indices) {
            vs[i] += dv[i] / vertexFaces[vs[i]]!!.size.toDouble()
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
                normSum += (a - center) cross (b - center)
            }
            normSum /= normSum.norm // normalize to unit vector
            // project vertices onto the resulting plane (if needed)
            val pd = normSum * center // plane distance
            for (v in f) {
                val a = vs[v.id]
                val vd = pd - normSum * a // vertex distance from plane
                if (abs(vd) < TARGET_TOLERANCE) continue
                ok = false
                dv[v.id] += vd * normSum
            }
            // clear temp vars
            center.setToZero()
            normSum.setToZero()
        }
        // apply average of projecting adjustments
        for (i in vs.indices) {
            vs[i] += dv[i] / vertexFaces[vs[i]]!!.size.toDouble()
            dv[i].setToZero()
        }
    } while (!ok)
    println("Done in $interactions iterations")
    // copy faces with new vertices
    val fs = fs.map { f ->
        MutableFace(f.id, f.fvs.map { vs[it.id] }, f.kind)
    }
    // rebuild polyhedron with new vertices and faces
    return Polyhedron(vs, fs)
}