package polyhedra.common

import polyhedra.common.util.*
import kotlin.math.*

enum class Transform(
    override val tag: String,
    val transform: (Polyhedron) -> Polyhedron,
    val isApplicable: (Polyhedron) -> String? = { null } // todo: not defined usefully now
) : Tagged {
    None("n", { it }),
    Dual("d", Polyhedron::dual),
    Rectified("r", Polyhedron::rectified),
    Truncated("t", Polyhedron::truncated),
    Cantellated("c", Polyhedron::cantellated), // ~= Rectified, Rectified
    Bevelled("b", Polyhedron::bevelled) // ~= Rectified, Truncated
}

val Transforms: List<Transform> by lazy { Transform.values().toList() }

fun Polyhedron.transformed(transform: Transform) = memoTransform(transform.transform)

fun Polyhedron.transformed(vararg transforms: Transform) =
    transforms.fold(this) { poly, transform -> poly.transformed(transform) }

fun Polyhedron.dual(): Polyhedron = polyhedron {
    val r = midradius
    // vertices from the original faces
    val fv = fs.associateWith { f ->
        vertex(f.plane.dualPoint(r), VertexKind(f.kind.id))
    }
    // faces from the original vertices
    for ((v, fl) in vertexFaces) {
        face(fl.map { fv[it]!! }, FaceKind(v.kind.id))
    }
}

fun Polyhedron.rectified(): Polyhedron = polyhedron {
    // vertices from the original edges
    val ev = es.associateWith { e ->
        vertex(e.midPoint(edgesMidPointDefault), VertexKind(edgeKindsIndex[e.kind]!!))
    }
    // faces from the original faces
    for (f in fs) {
        face(faceDirectedEdges[f]!!.map { ev[it.normalizedDirection()]!! }, f.kind)
    }
    // faces from the original vertices
    val kindOfs = faceKinds.size
    for (v in vs) {
        face(vertexDirectedEdges[v]!!.map { ev[it.normalizedDirection()]!! }, FaceKind(kindOfs + v.kind.id))
    }
}

// ea == PI / face_size
fun regularTruncationRatio(ea: Double): Double = 1 / (1 + cos(ea))

fun Polyhedron.regularTruncationRatio(faceKind: FaceKind = FaceKind(0)): Double {
    val f = faceKinds[faceKind]!!.first() // take representative face of this kind
    return regularTruncationRatio(PI / f.size)
}

fun Polyhedron.truncated(tr: Double = regularTruncationRatio()): Polyhedron = polyhedron {
    // vertices from the original directed edges
    val ev = directedEdges.associateWith { e ->
        val t = tr * e.midPointFraction(edgesMidPointDefault)
        vertex(t.atSegment(e.a.pt, e.b.pt), VertexKind(directedEdgeKindsIndex[e.kind]!!))
    }
    // faces from the original faces
    for (f in fs) {
        val fvs = faceDirectedEdges[f]!!.flatMap {
            listOf(ev[it]!!, ev[it.reversed()]!!)
        }
        face(fvs, f.kind)
    }
    // faces from the original vertices
    val kindOfs = faceKinds.size
    for (v in vs) {
        face(vertexDirectedEdges[v]!!.map { ev[it]!! }, FaceKind(kindOfs + v.kind.id))
    }
}

data class RegularFaceGeometry(
    val ea: Double, // PI / face_size
    val da: Double  // dihedral angle
)

fun Polyhedron.regularFaceGeometry(edgeKind: EdgeKind? = null): RegularFaceGeometry {
    val ek = edgeKind ?: edgeKinds.keys.first() // min edge kind by default
    val e = edgeKinds[ek]!!.first() // representative edge
    val f = e.r // primary face
    val g = e.l // secondary face
    val n = f.size // primary face size
    val ea = PI / n
    val da = PI - acos(f.plane.n * g.plane.n) // dihedral angle
    return RegularFaceGeometry(ea, da)
}

fun Polyhedron.regularCantellationRatio(edgeKind: EdgeKind? = null): Double {
    val (ea, da) = regularFaceGeometry(edgeKind)
    return 1 / (1 + sin(da / 2) / tan(ea))
}

fun Polyhedron.cantellated(cr: Double = regularCantellationRatio()): Polyhedron = polyhedron {
    // vertices from the directed edges
    val ev = directedEdges.associateWith { e ->
        val a = e.a // vertex for cantellation
        val f = e.r // primary face for cantellation
        val c = f.plane.tangentPoint // face center
        vertex(cr.atSegment(a.pt, c), VertexKind(directedEdgeKindsIndex[e.kind]!!))
    }
    val fvv = ev.entries
        .groupBy{ it.key.r }
        .mapValues { e ->
            e.value.associateBy({ it.key.a }, { it.value })
        }
    // faces from the original faces
    for (f in fs) {
        val fvs = faceDirectedEdges[f]!!.map { ev[it]!! }
        face(fvs, f.kind)
    }
    // faces from the original vertices
    var kindOfs = faceKinds.size
    for (v in vs) {
        face(vertexDirectedEdges[v]!!.map { ev[it]!! }, FaceKind(kindOfs + v.kind.id))
    }
    // faces from the original edges
    kindOfs += vertexKinds.size
    for (e in es) {
        val fvs = listOf(
            fvv[e.r]!![e.a]!!,
            fvv[e.l]!![e.a]!!,
            fvv[e.l]!![e.b]!!,
            fvv[e.r]!![e.b]!!
        )
        face(fvs, FaceKind(kindOfs + edgeKindsIndex[e.kind]!!))
    }
}

data class BevellingRatio(val cr: Double, val tr: Double)

fun Polyhedron.regularBevellingRatio(edgeKind: EdgeKind? = null): BevellingRatio {
    val (ea, da) = regularFaceGeometry(edgeKind)
    val tr = regularTruncationRatio(ea)
    val cr = (1 - tr) / (1 + sin(da / 2) / tan(ea) - tr)
    return BevellingRatio(cr, tr)
}

fun Polyhedron.bevelled(br: BevellingRatio = regularBevellingRatio()): Polyhedron = polyhedron {
    val (cr, tr) = br
    // vertices from the face-directed edges
    val fev = fs.associateWith { f ->
        val c = f.plane.tangentPoint // face center
        faceDirectedEdges[f]!!.flatMap { e ->
            val kind = directedEdgeKindsIndex[e.kind]!!
            // take edge both ways on a face, the other point will have different kind
            listOf(
                e to VertexKind(2 * kind),
                e.reversed() to VertexKind(2 * kind + 1)
            )
        }.associateBy({ it.first }, { (e, ek) ->
            val a = e.a // vertex for cantellation
            val b = e.b // next vertex for cantellation
            val ac = cr.atSegment(a.pt, c)
            val bc = cr.atSegment(b.pt, c)
            val t = tr * e.midPointFraction(edgesMidPointDefault)
            vertex(t.atSegment(ac, bc), ek)
        })
    }
    // faces from the original faces
    for (f in fs) {
        face(fev[f]!!.values, f.kind)
    }
    // faces from the original vertices
    var kindOfs = faceKinds.size
    for (v in vs) {
        val fvs = vertexDirectedEdges[v]!!.flatMap { e ->
            listOf(fev[e.l]!![e]!!, fev[e.r]!![e]!!)
        }
        face(fvs, FaceKind(kindOfs + v.kind.id))
    }
    // faces from the original edges
    kindOfs += vertexKinds.size
    for (e in es) {
        val er = e.reversed()
        val fvs = listOf(
            fev[e.r]!![e]!!,
            fev[e.l]!![e]!!,
            fev[e.l]!![er]!!,
            fev[e.r]!![er]!!
        )
        face(fvs, FaceKind(kindOfs + edgeKindsIndex[e.kind]!!))
    }
}

// a * x^2 + b * x + c = 0
private fun solve3(a: Double, b: Double, c: Double) =
    (-b + sqrt(sqr(b) - 4 * a * c)) / (2 * a)

private fun snubComputeSA(ea: Double, da: Double, cr: Double): Double {
    val rf = 1 - cr
    val cm = (1 - cos(da)) / 2
    val cp = (1 + cos(da)) / 2
    val cosGA = solve3(cp * sqr(rf), 2 * cm * rf * cos(ea), -sqr(cos(ea)) * (cm + sqr(rf)))
    return ea - acos(cosGA)
}

private fun snubComputeA(ea: Double, da: Double, cr: Double, sa: Double): Double {
    val h = 1 / (2 * tan(ea))
    val ga = ea - sa
    val rf = 1 - cr
    val t = rf / (2 * sin(ea))
    return Vec3(
        2 * t * sin(ga),
        (h - t * cos(ga)) * (cos(da) - 1),
        (h - t * cos(ga)) * sin(da)
    ).norm
}

private fun snubComputeB(ea: Double, da: Double, cr: Double, sa: Double): Double {
    val h = 1 / (2 * tan(ea))
    val ga = ea - sa
    val ha = ea + sa
    val rf = 1 - cr
    val t = rf / (2 * sin(ea))
    return Vec3(
        t * (sin(ga) - sin(ha)),
        (h - t * cos(ga)) * cos(da) - (h - t * cos(ha)),
        (h - t * cos(ga)) * sin(da)
    ).norm
}

private fun snubComputeCR(ea: Double, da: Double): Double {
    var crL = 0.0
    var crR = 1.0
    while (true) {
        val cr = (crL + crR) / 2
        if (cr <= crL || cr >= crR) return cr // result precision is an ULP
        val sa = snubComputeSA(ea, da, cr)
        // error goes from positive to negative to NaN as cr goes from 0 to 1
        val rf = 1 - cr
        val err = snubComputeB(ea, da, cr, sa) - rf
        if (err <= 0)
            crL = cr else
            crR = cr
    }
}

data class SnubbingRatio(val cr: Double, val sa: Double)

fun Polyhedron.regularSnubbingRatio(edgeKind: EdgeKind? = null): SnubbingRatio {
    val (ea, da) = regularFaceGeometry(edgeKind)
    val cr = snubComputeCR(ea, da)
    val sa = snubComputeSA(ea, da, cr)
    return SnubbingRatio(cr, sa)
}

fun Polyhedron.snub(sr: SnubbingRatio = regularSnubbingRatio()) = polyhedron {
    val (cr, sa) = sr
    // TODO:
}