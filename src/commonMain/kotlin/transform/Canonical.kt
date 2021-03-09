package polyhedra.common.transform

import polyhedra.common.*
import polyhedra.common.util.*
import kotlin.coroutines.cancellation.*
import kotlin.math.*

private const val TARGET_TOLERANCE = 1e-12

var totalIterations = 0

// Algorithm from https://www.georgehart.com/virtual-polyhedra/canonical.html
fun Polyhedron.canonical(progress: OperationProgressContext? = null): Polyhedron {
    // copy vertices to mutate them
    val vs = vs.map { it.toMutableVertex() }
    // pre-scale to an average midRadius of 1
    val preScale = 1 / midradius
    for (v in vs) v *= preScale
    // canonicalize
    val vMul = DoubleArray(vs.size) { i -> 1.0 / vertexFaces[vs[i]]!!.size }
    val dv = vs.map { MutableVec3() }
    val center = MutableVec3()
    val normSum = MutableVec3()
    val h = MutableVec3()
    var iterations = 0
    var initialLogError = 0.0
    var prevDone = 0
    while(true) {
        if (progress?.isActive == false) throw CancellationException("Operation was cancelled")
        var maxError = 0.0
        // check all edges
        for (f in fs) {
            for (i in 0 until f.size) {
                val a = vs[f[i].id]
                val b = vs[f[(i + 1) % f.size].id]
                val tf = tangentFraction(a, b)
                check(!tf.isNaN())
                tf.atSegmentTo(h, a, b)
                val err = 1.0 - h.norm
                maxError = max(maxError, abs(err))
                val factor = 0.95 * err
                dv[a.id].plusAssignMul(h, factor)
                dv[b.id].plusAssignMul(h, factor)
            }
        }
        // apply average of edge adjustments
        for (i in vs.indices) {
            vs[i].plusAssignMul(dv[i], vMul[i])
            dv[i].setToZero()
        }
        // compute current center of gravity
        for (i in vs.indices) {
            center += vs[i]
        }
        center /= vs.size.toDouble()
        maxError = max(maxError, center.norm)
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
                val dist = pd - normSum * a // vertex distance from plane
                maxError = max(maxError, abs(dist))
                dv[v.id].plusAssignMul(normSum, dist)
            }
            // clear temp vars
            center.setToZero()
            normSum.setToZero()
        }
        // apply average of projecting adjustments
        for (i in vs.indices) {
            vs[i].plusAssignMul(dv[i], vMul[i])
            dv[i].setToZero()
        }
        iterations++
        if (maxError <= TARGET_TOLERANCE) break // success
        val logError = log10(maxError)
        if (initialLogError == 0.0) {
            initialLogError = logError
        } else {
            val done = (100 * (initialLogError - logError) / (initialLogError - log10(TARGET_TOLERANCE))).toInt()
            if (done > prevDone) {
                println("Canonical: at $iterations iterations, log error = ${logError.fmt}, done = $done%")
                prevDone = done
                progress?.reportProgress(done)
            }
        }
    }
    println("Canonical: done in $iterations iterations")
    totalIterations += iterations
    // copy faces with new vertices
    val fs = fs.map { f ->
        MutableFace(f.id, f.fvs.map { vs[it.id] }, f.kind)
    }
    // rebuild polyhedron with new vertices and faces
    return Polyhedron(vs, fs)
}