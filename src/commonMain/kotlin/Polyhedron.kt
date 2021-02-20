package polyhedra.common

import polyhedra.common.util.*

class Polyhedron(
    val vs: List<Vertex>,
    val fs: List<Face>
) {
    val es: List<Edge> = buildList {
        val lFaces = ArrayIdMap<Vertex, HashMap<Vertex, Face>>()
        for (f in fs) {
            for (i in 0 until f.size) {
                val a = f[i]
                val b = f[(i + 1) % f.size]
                require(a.id != b.id)
                if (a.id > b.id) {
                    lFaces.getOrPut(b) { HashMap() }[a] = f
                }
            }
        }
        for (f in fs) {
            for (i in 0 until f.size) {
                val a = f[i]
                val b = f[(i + 1) % f.size]
                if (a.id < b.id) {
                    val lf = lFaces[a]?.get(b)
                    require(lf != null) {
                        "Edge $a to $b on face $f does not have an adjacent face"
                    }
                    add(Edge(a, b, lf, f).normalizedDirection())
                }
            }
        }
    }

    val vertexKinds: IdMap<VertexKind, List<Vertex>> by lazy { vs.groupById { it.kind } }
    val faceKinds: IdMap<FaceKind, List<Face>> by lazy { fs.groupById { it.kind } }
    val edgeKinds: Map<EdgeKind, List<Edge>> by lazy { es.groupBy { it.kind } }

    val directedEdges: List<Edge> by lazy {
        es.flatMap { listOf(it, it.reversed()) }
    }

    // adjacent edges are properly ordered
    val vertexDirectedEdges: IdMap<Vertex, List<Edge>> by lazy {
        directedEdges
            .groupById { it.a }
            .mapValues { entry ->
                entry.value.sortedVertexAdjacentEdges(entry.key)
            }
    }

    // adjacent vertex-edges are properly ordered
    val vertexVertexDirectedEdge: IdMap<Vertex, Map<Vertex, Edge>> by lazy {
        vertexDirectedEdges
            .mapValues { entry ->
                entry.value.associateBy { it.b }
            }
    }

    // adjacent faces are properly ordered
    val vertexFaces: IdMap<Vertex, List<Face>> by lazy {
        vertexDirectedEdges
            .mapValues { e -> e.value.map { it.r } }
    }

    val faceDirectedEdges: IdMap<Face, List<Edge>> by lazy {
        directedEdges
            .groupById { it.r }
            .mapValues { it.value.sortedFaceAdjacentEdges(it.key) }
    }

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

    val inradius: Double by lazy { fs.minOf { f -> f.plane.d } }
    val midradius: Double by lazy { es.avgOf { e -> e.midPoint(MidPoint.Closest).norm } }
    val circumradius: Double by lazy { vs.maxOf { v -> v.pt.norm } }

    val edgesMidPointDefault: MidPoint by lazy {
        if (es.all { e -> e.isTangentInSegment() }) MidPoint.Tangent else MidPoint.Center
    }

    override fun toString(): String =
        "Polyhedron(vs=${vs.size}, es=${es.size}, fs=${fs.size})"
}

fun List<Edge>.sortedVertexAdjacentEdges(a: Vertex): List<Edge> {
    require(all { it.a == a })
    val result = toMutableList()
    for (i in 1 until size) {
        val prev = result[i - 1].r
        val j = (i until size).first { result[it].l == prev }
        result.swap(i, j)
    }
    return result
}

fun List<Edge>.sortedFaceAdjacentEdges(f: Face): List<Edge> {
    require(all { it.r == f })
    val result = toMutableList()
    for (i in 1 until size) {
        val prev = result[i - 1].b
        val j = (i until size).first { result[it].a == prev }
        result.swap(i, j)
    }
    return result
}

private fun idString(id: Int, first: Char, last: Char): String {
    val n = last - first + 1
    val ch = first + (id % n)
    val rem = id / n
    if (rem == 0) return ch.toString()
    return idString(rem - 1, first, last) + ch
}

inline class VertexKind(override val id: Int) : Id, Comparable<VertexKind> {
    override fun compareTo(other: VertexKind): Int = id.compareTo(other.id)
    override fun toString(): String = idString(id, 'A', 'Z')
}

class Vertex(
    override val id: Int,
    val pt: Vec3,
    val kind: VertexKind,
) : Id {
    override fun equals(other: Any?): Boolean = other is Vertex && id == other.id
    override fun hashCode(): Int = id
    override fun toString(): String = "$kind vertex(id=$id, $pt)"
}

inline class FaceKind(override val id: Int) : Id, Comparable<FaceKind> {
    override fun compareTo(other: FaceKind): Int = id.compareTo(other.id)
    override fun toString(): String = idString(id, 'α', 'ω')
}

class Face(
    override val id: Int,
    val fvs: List<Vertex>,
    val kind: FaceKind,
) : Id {
    val plane = plane3(fvs[0].pt, fvs[1].pt, fvs[2].pt)

    val size: Int
        get() = fvs.size
    operator fun get(index: Int): Vertex = fvs[index]
    operator fun iterator(): Iterator<Vertex> = fvs.iterator()

    override fun equals(other: Any?): Boolean = other is Face && id == other.id
    override fun hashCode(): Int = id
    override fun toString(): String =
        "$kind face(id=$id, [${fvs.map { it.id }.joinToString(", ")}])"
}

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
    val l: Face,
    val r: Face,
) {
    val kind: EdgeKind = EdgeKind(a.kind, b.kind, l.kind, r.kind)
    override fun toString(): String = "$kind edge(${a.id}-${l.id}/${r.id}-${b.id})"
}

fun Edge.reversed(): Edge = Edge(b, a, r, l)

fun Edge.normalizedDirection(): Edge {
    val rk = kind.reversed().compareTo(kind)
    return when {
        rk < 0 -> reversed()
        rk > 0 -> this
        b.id < a.id -> reversed()
        else -> this
    }
}

val Edge.vec: Vec3
    get() = b.pt - a.pt

val Edge.len: Double
    get() = vec.norm

class PolyhedronBuilder {
    private val vs = ArrayList<Vertex>()
    private val fs = ArrayList<Face>()

    fun vertex(p: Vec3, kind: VertexKind = VertexKind(0)): Vertex =
        Vertex(vs.size, p, kind).also { vs.add(it) }

    fun vertex(x: Double, y: Double, z: Double, kind: VertexKind = VertexKind(0)): Vertex =
        vertex(Vec3(x, y, z), kind)

    fun face(vararg fvIds: Int, kind: FaceKind = FaceKind(0)) {
        val a = List(fvIds.size) { vs[fvIds[it]] }
        fs.add(Face(fs.size, a, kind))
    }

    fun face(fvs: List<Vertex>, kind: FaceKind) {
        fs.add(Face(fs.size, fvs.map { vs[it.id] }, kind))
    }

    fun face(f: Face) {
        face(f.fvs, f.kind)
    }

    fun build() = Polyhedron(vs, fs)
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

