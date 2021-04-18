package polyhedra.common.transform

import kotlinx.serialization.*
import polyhedra.common.poly.*

const val DROP_TAG = "x"

@Serializable
class Drop(val kind: AnyKind) : Transform() {
    override val tag: String
        get() = "$DROP_TAG[$kind]"
    override fun transform(poly: Polyhedron): Polyhedron = poly.drop(kind)
    override fun toString(): String = "Drop $kind"
}

class DropExpansion(poly: Polyhedron, kind: AnyKind) {
    val dropVks: Set<VertexKind> // drop vertices
    val dropEks: Set<EdgeKind> // drop edges
    val isValid: Boolean

    init {
        val dropVks = HashSet<VertexKind>()
        val dropEks = HashSet<EdgeKind>()
        val mergeFks = HashSet<FaceKind>()
        var changes: Boolean

        fun drop(ek: EdgeKind) {
            if (!dropEks.add(ek)) return
            changes = true
            dropEks.add(ek.reversed())
            mergeFks.add(ek.l)
            mergeFks.add(ek.r)
        }

        fun drop(vk: VertexKind) {
            if (!dropVks.add(vk)) return
            changes = true
            poly.vertexKinds[vk]!!.directedEdges.forEach { drop(it.kind) }
        }

        fun drop(fk: FaceKind) {
            poly.faceKinds[fk]!!.directedEdges.forEach { drop(it.kind) }
        }

        fun check(vk: VertexKind) {
            val cnt = poly.vertexKinds[vk]!!.directedEdges.count { it.kind !in dropEks }
            if (cnt < 3) drop(vk)
        }

        when (kind) {
            is VertexKind -> drop(kind)
            is FaceKind -> drop(kind)
            is EdgeKind -> drop(kind)
            else -> error("Unexpected kind: $kind")
        }

        do {
            changes = false
            for (ek in dropEks.toList()) {
                check(ek.a) // reverse edge is there, will check ek.b
            }
        } while (changes)

        this.dropVks = dropVks
        this.dropEks = dropEks
        // valid when some vertices remain and merged faces form one well-formed face
        isValid = dropVks.size < poly.vertexKinds.size && poly.isWellFormedMergedFace(mergeFks)
    }
}

private fun Polyhedron.isWellFormedMergedFace(mergeFks: Set<FaceKind>): Boolean {
    val border = HashMap<Vertex, Edge>()
    val faces = HashSet<Face>()
    val queue = ArrayDeque<Face>()
    fun enqueue(f: Face) {
        if (faces.add(f)) queue.addLast(f)
    }
    // take representative starting face, walk new merged face & build its full border
    enqueue(faceKinds[mergeFks.first()]!!)
    while (queue.isNotEmpty()) {
        val f = queue.removeFirst()
        for (e in f.directedEdges) {
            val g = e.l
            if (g.kind in mergeFks) {
                enqueue(g)
            } else {
                val prev = border.put(e.a, e)
                if (prev != null) return false
            }
        }
    }
    // pick a vertex on edge on the border and follow util it cycles
    val start = border.values.firstOrNull() ?: return false
    var cur = start
    do {
        cur = border.remove(cur.b) ?: return false
    } while (cur != start)
    // all border edges must have been walked
    return border.isEmpty()
}

fun Polyhedron.drop(kind: AnyKind) = transformedPolyhedron(Drop, kind) {
    with(DropExpansion(this@drop, kind)) {
        check(isValid)
        val vv = vs
            .filter { v -> v.kind !in dropVks }
            .associateWith { v -> vertex(v) }
        val processed = HashSet<Face>()
        for (f in fs) {
            if (!processed.add(f)) continue
            val start = f.directedEdges.firstOrNull() { it.kind !in dropEks } ?: continue
            val fvs = ArrayList<Vertex>()
            var fk = f.kind
            var cur = start
            do {
                fvs.add(vv[cur.a]!!)
                val v = cur.b
                val n = v.directedEdges.size
                val i0 = v.directedEdges.indexOf(cur.reversed)
                var found = false
                for (k in n - 1 downTo 1) {
                    val e = v.directedEdges[(i0 + k) % n]
                    if (e.kind !in dropEks) {
                        cur = e
                        fk = minOf(fk, e.r.kind)
                        processed.add(e.r)
                        found = true
                        break
                    }
                }
                check(found)
            } while (cur != start)
            face(fvs, fk)
        }
    }
    // this also forces renumbering of kinds to fill in gaps
    mergeIndistinguishableKinds()
}