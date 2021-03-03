package polyhedra.common

// Faces, Edge, Vertices (counts)
data class FEV(
    val f: Int,
    val e: Int,
    val v: Int
)

fun Polyhedron.fev(): FEV = FEV(fs.size, es.size, vs.size)

class TransformFEV(
    val ff: Int, val fe: Int, val fv: Int,
    val ef: Int, val ee: Int, val ev: Int,
    val vf: Int, val ve: Int, val vv: Int
)

operator fun TransformFEV.times(p: FEV): FEV = with(p) {
    FEV(
        ff * f + fe * e + fv * v,
        ef * f + ee * e + ev * v,
        vf * f + ve * e + vv * v
    )
}
