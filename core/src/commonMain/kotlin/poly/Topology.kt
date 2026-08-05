/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.poly

fun Polyhedron.hasSameTopology(other: Polyhedron): Boolean {
    val nv = vs.size
    if (nv != other.vs.size) return false
    val ne = es.size
    if (ne != other.es.size) return false
    val nf = fs.size
    if (nf != other.fs.size) return false
    for (i in 0 until nv) {
        if (vs[i].kind != other.vs[i].kind) return false
    }
    for (i in 0 until ne) {
        val e1 = es[i]
        val e2 = other.es[i]
        if (e1.a.id != e2.a.id) return false
        if (e1.b.id != e2.b.id) return false
        if (e1.l.id != e2.l.id) return false
        if (e1.r.id != e2.r.id) return false
    }
    for (i in 0 until nf) {
        val f1 = fs[i]
        val f2 = other.fs[i]
        if (f1.kind != f2.kind) return false
        val m = f1.size
        if (m != f2.size) return false
        for (j in 0 until m) {
            if (f1[j].id != f2[j].id) return false
        }
    }
    return true
}
