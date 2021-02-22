package polyhedra.common

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

fun regularTruncationRatio(n: Int): Double {
    return 1 / (1 + cos(PI / n))
}

fun Polyhedron.regularTruncationRatio(faceKind: FaceKind = FaceKind(0)): Double {
    val f = faceKinds[faceKind]!!.first() // take representative face of this kind
    return regularTruncationRatio(
        f.size // face size
    )
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

fun Polyhedron.regularCantellationRatio(edgeKind: EdgeKind? = null): Double {
    val ek = edgeKind ?: edgeKinds.keys.first() // min edge kind by default
    val e = edgeKinds[ek]!!.first() // representative edge
    val f = e.r // primary face
    val g = e.l // secondary face
    val n = f.size // primary face size
    val da = PI - acos(f.plane.n * g.plane.n) // dihedral angle
    return 1 / (1 + sin(da / 2) / tan(PI / n))
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

data class BevellingRatio(val tr: Double, val cr: Double)

fun Polyhedron.regularBevellingRatio(edgeKind: EdgeKind? = null): BevellingRatio {
    val ek = edgeKind ?: edgeKinds.keys.first() // min edge kind by default
    val e = edgeKinds[ek]!!.first() // representative edge
    val f = e.r // primary face
    val g = e.l // secondary face
    val n = f.size // primary face size
    val da = PI - acos(f.plane.n * g.plane.n) // dihedral angle
    val tr = regularTruncationRatio(n)
    val cr = (1 - tr) / (1 + sin(da / 2) / tan(PI / n) - tr)
    return BevellingRatio(tr, cr)
}

fun Polyhedron.bevelled(br: BevellingRatio = regularBevellingRatio()): Polyhedron = polyhedron {
    val (tr, cr) = br
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