/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.transform

import kotlinx.coroutines.*
import polyhedra.common.poly.*
import polyhedra.common.util.*
import kotlin.math.*
import kotlin.time.*

private const val TARGET_TOLERANCE = 1e-12

var totalIterations = 0

// https://youtrack.jetbrains.com/issue/KT-40689 workaround
private fun max(a: Double, b: Double) = if (a > b) a else b

fun Polyhedron.canonical(): Polyhedron =
    runSynchronously { canonical(null) }

// Tuning parameters for algorithm's convergence
private const val speedFactorEdges = 1.0 // diverges when larger
private const val speedFactorFaces = 2.0 // diverges when larger

// Algorithm from https://www.georgehart.com/virtual-polyhedra/canonical.html
@OptIn(ExperimentalTime::class)
suspend fun Polyhedron.canonical(progress: OperationProgressContext?): Polyhedron {
    val poly = this
    // https://youtrack.jetbrains.com/issue/KT-42625 workaround
    val monotonic = kotlin.time.TimeSource.Monotonic
    val startTime = monotonic.markNow()
    // copy vertices coordinates to mutate them
    val vs = vs.mapTo(ArrayList()) { it.toMutableVec3() }
    // pre-scale to an average midRadius of 1
    val preScale = 1 / midradius
    for (v in vs) v *= preScale
    // canonicalize
    val vAvgFactor = DoubleArray(vs.size) { i -> 1.0 / poly.vs[i].directedEdges.size }
    val dv = vs.map { MutableVec3() }
    val center = MutableVec3()
    val normSum = MutableVec3()
    val h = MutableVec3()
    var iterations = 0
    var initialError = 0.0
    var prevDone = 0
    var lastTime = 0L
    while(true) {
        var maxError = 0.0
        // check all edges
        for (f in fs) {
            for (i in 0 until f.size) {
                val aid = f[i].id
                val bid = f[(i + 1) % f.size].id
                val a = vs[aid]
                val b = vs[bid]
                val tf = tangentFraction(a, b)
                check(!tf.isNaN())
                tf.atSegmentTo(h, a, b)
                val err = 1.0 - h.norm
                maxError = max(maxError, abs(err))
                dv[aid].plusAssignMul(h, err)
                dv[bid].plusAssignMul(h, err)
            }
        }
        // apply average of edge adjustments
        for (i in vs.indices) {
            vs[i].plusAssignMul(dv[i], speedFactorEdges * vAvgFactor[i])
            dv[i].setToZero()
        }
        // compute current center of gravity
        for (i in vs.indices) {
            center += vs[i]
        }
        center /= vs.size
        maxError = max(maxError, center.norm)
        // recenter all vertices
        for (i in vs.indices) {
            vs[i].minusAssign(center)
        }
        center.setToZero()
        // check all faces & project vertices
        for (f in fs) {
            // find centroid of face vertices
            for (i in 0 until f.size) center += vs[f[i].id]
            center /= f.size
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
        // apply average of face-projecting adjustments
        for (i in vs.indices) {
            vs[i].plusAssignMul(dv[i], speedFactorFaces * vAvgFactor[i])
            dv[i].setToZero()
        }
        iterations++
        if (maxError <= TARGET_TOLERANCE) break // success
        // Record initial error
        if (initialError == 0.0) {
            initialError = maxError
            continue
        }
        // cancellation/log point (checked every 100 ms)
        val curTime = startTime.elapsedNow().inWholeMilliseconds / 100
        if (curTime <= lastTime) continue
        lastTime = curTime
        val done = (100 * log10(initialError / maxError) / log10(initialError / TARGET_TOLERANCE)).toInt()
        // log every second
        if (curTime % 10 == 0L) {
            println("Canonical: at $iterations iterations, log error = ${log10(maxError).fmt}, done = $done%")
        }
        // report progress when it changes
        if (done > prevDone) {
            prevDone = done
            progress?.reportProgress(done)
        }
        yield()
    }
    println("Canonical: done $iterations iterations in ${startTime.elapsedNow().toDouble(DurationUnit.SECONDS).fmtFix(3)} sec")
    totalIterations += iterations
    // rebuild polyhedron with new vertices and old faces
    return polyhedron(mergeIndistinguishableKinds = true) {
        for (i in vs.indices) vertex(vs[i], poly.vs[i].kind)
        faces(fs)
    }
}

fun Polyhedron.isCanonical(): Boolean {
    // 1. Polyhedron must be centered at origin
    val center = MutableVec3()
    for (i in vs.indices) {
        center += vs[i]
    }
    center /= vs.size
    if (!(center approx Vec3.ZERO)) return false
    // 2. All faces must be planar
    if (fs.any { !it.isPlanar }) return false
    // 3. All edges must be tangent to the sphere of the same radius
    var minD = Double.POSITIVE_INFINITY
    var maxD = 0.0
    for (e in es) {
        val d = e.tangentDistance()
        if (d < minD) minD = d
        if (d > maxD) maxD = d
    }
    return maxD approx minD
}