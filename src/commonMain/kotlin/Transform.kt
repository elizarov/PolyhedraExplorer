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
    Cantellated("c", Polyhedron::cantellated)
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
    for (v in vs) {
        face(vertexDirectedEdges[v]!!.map { ev[it.normalizedDirection()]!! }, FaceKind(faceKinds.size + v.kind.id))
    }
}

fun Polyhedron.regularTruncationRatio(faceKind: FaceKind = FaceKind(0)): Double {
        val f = faceKinds[faceKind]!!.first() // take representative face of this kind
        val n = f.size // face size
        return 1 / (1 + cos(PI / n))
    }

fun Polyhedron.truncated(ratio: Double = regularTruncationRatio()): Polyhedron = polyhedron {
    // vertices from the original directed edges
    val ev = directedEdges.associateWith { e ->
        val t = ratio * e.midPointFraction(edgesMidPointDefault)
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

fun Polyhedron.regularCantellationRatio(edgeKind: EdgeKind? = null): Double {
    val ek = edgeKind ?: edgeKinds.keys.first() // min edge kind by default
    val e = edgeKinds[ek]!!.first() // representative edge
    val f = e.r // primary face
    val g = e.l // secondary face
    val n = f.size // primary face size
    val da = PI - acos(f.plane.n * g.plane.n) // dihedral angle
    return 1 / (1 + sin(da / 2) / tan(PI / n))
}

fun Polyhedron.cantellated(ratio: Double = regularCantellationRatio()): Polyhedron = polyhedron {
    // vertices from the directed edges
    val ev = directedEdges.associateWith { e ->
        val a = e.a // vertex for cantellation
        val f = e.r // primary face for cantellation
        val c = f.plane.tangentPoint // face center
        vertex(ratio.atSegment(a.pt, c), VertexKind(directedEdgeKindsIndex[e.kind]!!))
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