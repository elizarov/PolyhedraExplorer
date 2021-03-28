/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.poly

import kotlinx.serialization.*
import polyhedra.common.util.*

@Serializable(with = PolyhedronSerializer::class)
class Polyhedron(
    vs: List<MutableVertex>,
    fs: List<MutableFace>
) {
    val vs: List<Vertex> = vs
    val fs: List<Face> = fs
    val es: List<Edge>
    val directedEdges: List<Edge>

    // `build edges (unidirectional & directed) and link them with vertices and faces
    init {
        val es = ArrayList<Edge>()
        val directedEdges = ArrayList<Edge>()
        val vertexDirectedEdges = ArrayIdMap<MutableVertex, ArrayList<Edge>>(
            vs.size,
            keyFactory = { i -> vs[i] },
            valueFactory = { ArrayList() }
        )
        val faceDirectedEdges = ArrayIdMap<MutableFace, ArrayList<Edge>>(
            fs.size,
            keyFactory = { i -> fs[i] },
            valueFactory = { ArrayList() }
        )
        val lFaces = ArrayIdMap<Vertex, HashMap<Vertex, Face>>()
        for (f in fs) {
            for (i in 0 until f.size) {
                val a = f[i]
                val b = f[(i + 1) % f.size]
                require(a.id != b.id) { "Duplicate vertices at face $f" }
                if (a.id <= b.id) continue
                lFaces.getOrPut(b) { HashMap() }[a] = f
            }
        }
        for (rf in fs) {
            for (i in 0 until rf.size) {
                val a = rf[i]
                val b = rf[(i + 1) % rf.size]
                if (a.id >= b.id) continue
                val lf = lFaces[a]?.get(b)
                require(lf != null) {
                    "Edge $a to $b on face $rf does not have an adjacent face"
                }
                val ea = Edge(a, b, lf, rf)
                val eb = Edge(b, a, rf, lf) 
                ea.reversed = eb
                eb.reversed = ea
                es += ea.normalizedDirection()
                directedEdges += ea
                directedEdges += eb
                vertexDirectedEdges[a]!!.add(ea)
                vertexDirectedEdges[b]!!.add(eb)
                faceDirectedEdges[rf]!!.add(ea)
                faceDirectedEdges[lf]!!.add(eb)
            }
        }
        for ((v, ves) in vertexDirectedEdges) {
            ves.sortVertexAdjacentEdges(v)
            v.directedEdges = ves
        }
        for ((f, fes) in faceDirectedEdges) {
            fes.sortFaceAdjacentEdges(f)
            for (i in 0 until fes.size) {
                val e0 = fes[i]
                val e1 = fes[(i + 1) % fes.size]
                e0.rNext = e1
                e1.reversed.lNext = e0.reversed
            }
            f.directedEdges = fes
        }
        this.es = es
        this.directedEdges = directedEdges
    }

    val vertexKinds: IdMap<VertexKind, List<Vertex>> by lazy { vs.groupById { it.kind } }
    val faceKinds: IdMap<FaceKind, List<Face>> by lazy { fs.groupById { it.kind } }
    val edgeKinds: Map<EdgeKind, List<Edge>> by lazy { es.groupBy { it.kind } }

    val edgeKindsIndex: Map<EdgeKind, Int> by lazy {
        es.asSequence()
            .map { it.kind }
            .distinctIndexed { it }
    }

    val directedEdgeKindsIndex: Map<EdgeKind, Int> by lazy {
        directedEdges.asSequence()
            .map { it.kind }
            .distinctIndexed { it }
    }

    val inradius: Double by lazy { fs.minOf { f -> f.d } }
    val midradius: Double by lazy { es.avgOf { e -> e.midPoint(MidPoint.Closest).norm } }
    val circumradius: Double by lazy { vs.maxOf { v -> v.norm } }

    // Radius that is used for for polar reciprocation to compute dual,
    // the key requirement is that dual points of regular polygon's faces must be in the centers of those faces
    val dualReciprocationRadius: Double
        get() = inradius

    val edgesMidPointDefault: MidPoint by lazy {
        if (es.all { e -> e.isTangentInSegment() }) MidPoint.Tangent else MidPoint.Center
    }

    override fun toString(): String =
        "Polyhedron(vs=${vs.size}, es=${es.size}, fs=${fs.size})"
}

private fun ArrayList<Edge>.sortVertexAdjacentEdges(v: Vertex) {
    require(all { it.a == v })
    for (i in 1 until size) {
        val prev = this[i - 1].r
        val j = (i until size).first { this[it].l == prev }
        swap(i, j)
    }
}

private fun ArrayList<Edge>.sortFaceAdjacentEdges(f: Face) {
    require(all { it.r == f })
    for (i in 1 until size) {
        val prev = this[i - 1].b
        val j = (i until size).first { this[it].a == prev }
        swap(i, j)
    }
}

private fun idString(id: Int, first: Char, last: Char): String {
    val n = last - first + 1
    val ch = first + (id % n)
    val rem = id / n
    if (rem == 0) return ch.toString()
    return idString(rem - 1, first, last) + ch
}

interface MutableKind<K : Id> {
    var kind: K
}

@Serializable
inline class VertexKind(override val id: Int) : Id, Comparable<VertexKind> {
    override fun compareTo(other: VertexKind): Int = id.compareTo(other.id)
    override fun toString(): String = idString(id, 'A', 'Z')
}

interface Vertex : Id, Vec3 {
    val kind: VertexKind
    val directedEdges: List<Edge> // edges are properly ordered clockwise
}

class MutableVertex(
    override val id: Int,
    pt: Vec3,
    override var kind: VertexKind,
) : Vertex, MutableVec3(pt), MutableKind<VertexKind> {
    override lateinit var directedEdges: List<Edge> // edges are properly ordered clockwise

    override fun equals(other: Any?): Boolean = other is Vertex && id == other.id
    override fun hashCode(): Int = id
    override fun toString(): String = "$kind vertex(id=$id, ${super.toString()})"
}

fun Vertex.toMutableVertex(): MutableVertex =
    MutableVertex(id, this, kind).apply {
        directedEdges = this@toMutableVertex.directedEdges
    }

@Serializable
inline class FaceKind(override val id: Int) : Id, Comparable<FaceKind> {
    override fun compareTo(other: FaceKind): Int = id.compareTo(other.id)
    override fun toString(): String = idString(id, 'α', 'ω')
}

interface Face : Id, Plane {
    val fvs: List<Vertex>
    val kind: FaceKind
    val dualKind: FaceKind // used only for by cantellation
    val isPlanar: Boolean
    val directedEdges: List<Edge> // edges are properly ordered clockwise
}

class MutableFace(
    override val id: Int,
    override val fvs: List<Vertex>,
    override var kind: FaceKind,
    override val dualKind: FaceKind = kind // used only for by cantellation
) : Face, MutablePlane(fvs.averagePlane()), MutableKind<FaceKind> {
    override val isPlanar = fvs.all { it in this }
    override lateinit var directedEdges: List<Edge> // edges are properly ordered clockwise

    override fun equals(other: Any?): Boolean = other is Face && id == other.id
    override fun hashCode(): Int = id
    override fun toString(): String =
        "$kind face(id=$id, [${fvs.map { it.id }.joinToString(", ")}])"
}

val Face.size: Int get() = fvs.size
operator fun Face.get(index: Int): Vertex = fvs[index]
operator fun Face.iterator(): Iterator<Vertex> = fvs.iterator()

data class EdgeKind(val a: VertexKind, val b: VertexKind, val l: FaceKind, val r: FaceKind) : Comparable<EdgeKind> {
    override fun compareTo(other: EdgeKind): Int {
        if (a != other.a) return a.compareTo(other.a)
        if (b != other.b) return b.compareTo(other.b)
        if (l != other.l) return l.compareTo(other.l)
        return r.compareTo(other.r)
    }

    override fun toString(): String = "$a-$l/$r-$b"
}

fun EdgeKind.reversed(): EdgeKind = EdgeKind(b, a, r, l)

data class Edge(
    val a: Vertex,
    val b: Vertex,
    val l: Face, // face to the left of the edge
    val r: Face, // face to the right of the edge
) {
    val kind: EdgeKind = EdgeKind(a.kind, b.kind, l.kind, r.kind)
    lateinit var reversed: Edge
    lateinit var rNext: Edge // next clockwise edge on the right face
    lateinit var lNext: Edge // next clockwise edge on the left face
    override fun toString(): String = "$kind edge(${a.id}-${l.id}/${r.id}-${b.id})"
}

fun Edge.normalizedDirection(): Edge {
    val rk = kind.reversed().compareTo(kind)
    return when {
        rk < 0 -> reversed
        rk > 0 -> this
        b.id < a.id -> reversed
        else -> this
    }
}

val Edge.vec: Vec3
    get() = b - a

val Edge.len: Double
    get() = vec.norm

fun Edge.distanceTo(p: Vec3): Double =
    p.distanceToLine(a, b)

private const val DEBUG_MERGE_KINDS = false

class PolyhedronBuilder(
    private val vs: ArrayList<MutableVertex> = ArrayList(),
    private val fs: ArrayList<MutableFace> = ArrayList()
) {
    private var mergeIndistinguishableKinds = false

    fun vertex(p: Vec3, kind: VertexKind = VertexKind(0)): Vertex =
        MutableVertex(vs.size, p, kind).also { vs.add(it) }

    fun vertex(x: Double, y: Double, z: Double, kind: VertexKind = VertexKind(0)): Vertex =
        vertex(Vec3(x, y, z), kind)

    fun face(vararg fvIds: Int, kind: FaceKind = FaceKind(0)) {
        val a = List(fvIds.size) { vs[fvIds[it]] }
        fs.add(MutableFace(fs.size, a, kind))
    }

    fun face(fvIds: List<Int>, kind: FaceKind = FaceKind(0)) {
        val a = List(fvIds.size) { vs[fvIds[it]] }
        fs.add(MutableFace(fs.size, a, kind))
    }

    fun face(fvs: Collection<Vertex>, kind: FaceKind, dualKind: FaceKind = kind) {
        fs.add(MutableFace(fs.size, fvs.map { vs[it.id] }, kind, dualKind))
    }

    fun face(f: Face) {
        face(f.fvs, f.kind, f.dualKind)
    }

    fun build(): Polyhedron {
        val poly = Polyhedron(vs, fs)
        if (!mergeIndistinguishableKinds) return poly
        val ge = poly.directedEdges.groupIndistinguishable()
        val gk = ge.mapTo(HashSet()) { list ->
            val vks = list.mapTo(HashSet()) { it.a.kind }
            val fks = list.mapTo(HashSet()) { it.r.kind }
            vks to fks
        }
        val (vkg, fkg) = gk.filterIndistinguishableKinds()
        if (vkg.isEmpty() && fkg.isEmpty()) return poly
        if (DEBUG_MERGE_KINDS) {
            println("--- mergeIndistinguishableKinds")
            ge.forEachIndexed { i, g -> println("Edge group #$i: $g") }
            gk.forEachIndexed { i, g -> println("Kind group #$i: $g") }
            println("Resulting vkg = $vkg")
            println("Resulting fkg = $fkg")
        }
        vs.renumberKinds(vkg) { VertexKind(it) }
        fs.renumberKinds(fkg) { FaceKind(it) }
        return Polyhedron(vs, fs)
    }

    fun debugDump() {
        for (v in vs) println(v)
        for (f in fs) println(f)
    }

    fun mergeIndistinguishableKinds() {
        mergeIndistinguishableKinds = true
    }
}

fun polyhedron(block: PolyhedronBuilder.() -> Unit): Polyhedron =
    PolyhedronBuilder().run {
        block()
        build()
    }

private fun <T> MutableList<T>.swap(i: Int, j: Int) {
    val t = this[i]
    this[i] = this[j]
    this[j] = t
}

private fun <K : Id> Set<Set<K>>.countGroupOccurrences(): IdMap<K, Int> {
    val c = ArrayIdMap<K, Int>()
    for (g in this) for (k in g) {
        c[k] = c.getOrElse(k) { 0 } + 1
    }
    return c
}

private fun Set<Pair<Set<VertexKind>, Set<FaceKind>>>.filterIndistinguishableKinds(): Pair<List<Set<VertexKind>>, List<Set<FaceKind>>> {
    // count mentions
    val vgs = mapTo(HashSet()) { it.first }
    val fgs = mapTo(HashSet()) { it.second }
    val vc = vgs.countGroupOccurrences()
    val fc = fgs.countGroupOccurrences()
    // drop all ambiguous groups that come in different combos
    fun <K : Id> isGood(g: Set<K>, c: IdMap<K, Int>): Boolean = g.all { c[it] == 1 }
    forEach { (vg, fg) ->
        // it should be non-singleton group with a unique renumber groups
        if (!isGood(vg, vc) ||  !isGood(fg, fc)) {
            vgs -= vg
            fgs -= fg
        }
    }
    // recursively drop groups with bad pair
    do {
        var changes = false
        forEach { (vg, fg) ->
            // if one is bad, drop the other too
            if (vg in vgs) {
                if (fg !in fgs) {
                    vgs -= vg
                    changes = true
                }
            } else {
                // vg !in vgs
                if (fg in fgs) {
                    fgs -= fg
                    changes = true
                }
            }
        }
    } while (changes)
    return vgs.filter { it.size > 1 } to fgs.filter { it.size > 1 }
}

private fun <K, T : MutableKind<K>> List<T>.renumberKinds(
    gs: Collection<Collection<K>>,
    factory: (Int) -> K
) where K : Id, K : Comparable<K> {
    if (gs.isEmpty()) return
    val map = ArrayIdMap<K, K>()
    for (g in gs) {
        val k0 = g.minOrNull()!!
        for (k in g) map[k] = k0
    }
    for (item in this) {
        val k = item.kind
        if (map[k] == null) map[k] = k
    }
    val reindex = map.values.distinctIndexed { factory(it) }
    for ((src, dst) in map) {
        map[src] = reindex[dst]!!
    }
    for (item in this) {
        item.kind = map[item.kind]!!
    }
}

