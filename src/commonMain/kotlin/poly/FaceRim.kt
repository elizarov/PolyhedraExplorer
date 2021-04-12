package polyhedra.common.poly

import polyhedra.common.util.*

class FaceRim(f: Face)  {
    val maxRim: Double
    val rimDir: List<Vec3>

    init {
        val n = f.size
        // edge vectors
        val ev = List(n) { i ->
            val j = (i + 1) % n
            f[j] - f[i]
        }
        // edge unit vectors
        val evu = List(n) { i ->
            ev[i].unit
        }
        // angle bisectors
        val bis = List(n) { i ->
            val k = (i + n - 1) % n
            evu[i] - evu[k]
        }
        // pseudo-incenter
        val ic = run {
            val sum = MutableVec3()
            val tmp = MutableVec3()
            for (i in 0 until n) {
                val j = (i + 1) % n
                val a1 = bis[i] * bis[i]
                val b1 = -bis[i] * bis[j]
                val c1 = bis[i] * ev[i]
                val a2 = -b1
                val b2 = bis[j] * bis[j]
                val c2 = bis[j] * ev[i]
                val d = det(a1, b1, a2, b2)
                val t1 = det(c1, b1, c2, b2) / d
                val t2 = det(a1, c1, a2, c2) / d
                tmp.setToZero()
                tmp += f[i]
                tmp += bis[i] * t1
                tmp += f[j]
                tmp += bis[j] * t2
                tmp /= 2.0
                sum += tmp
            }
            sum /= n.toDouble()
            sum
        }
        // vector from vertex to ic
        val icd = List(n) { i ->
            ic - f[i]
        }
        // shortest distance from ic to edge
        maxRim = (0 until n).minOf { i ->
            val j = (i + 1) % n
            tangentDistance(icd[i], icd[j])
        }
        // rescale so that rimDir reaches ic when multiplied by maxRim
        rimDir = List(n) { i ->
            icd[i] / maxRim
        }
    }

    val borderNorm = List(f.size) { i ->
        val j = (i + 1) % f.size
        (f[j] cross f[i]).unit
    }
}