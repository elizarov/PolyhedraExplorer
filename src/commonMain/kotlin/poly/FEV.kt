/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.poly

// Faces, Edge, Vertices (counts)
data class FEV(
    val f: Int,
    val e: Int,
    val v: Int
) {
    override fun toString(): String = "F=$f, E=$e, V=$v"
}

fun Polyhedron.fev(): FEV = FEV(fs.size, es.size, vs.size)

class TransformFEV(
    val ff: Int, val fe: Int, val fv: Int,
    val ef: Int, val ee: Int, val ev: Int,
    val vf: Int, val ve: Int, val vv: Int
) {
    companion object {
        val ID = TransformFEV(
            1, 0, 0,
            0, 1, 0,
            0, 0, 1
        )
    }
}

operator fun TransformFEV.times(p: FEV): FEV = with(p) {
    FEV(
        ff * f + fe * e + fv * v,
        ef * f + ee * e + ev * v,
        vf * f + ve * e + vv * v
    )
}
