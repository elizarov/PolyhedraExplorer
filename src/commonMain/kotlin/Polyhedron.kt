package polyhedra.common

import kotlinx.serialization.Serializable
import polyhedra.common.util.*

@Serializable(with = PolyhedronSerializer::class)
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
                require(a.id != b.id) { "Duplicate vertices at face $f" }
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

    // edges are properly ordered
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

    private inner class TransformMemo(
        val transform: (Polyhedron) -> Polyhedron,
        private val result: Result<Polyhedron>
    ) {
        fun getOrThrow(): Polyhedron = result.getOrThrow()
    }

    private var memo: TransformMemo? = null

    fun memoTransform(transform: (Polyhedron) -> Polyhedron): Polyhedron {
        memo?.takeIf { it.transform === transform }?.let { return it.getOrThrow() }
        val result = runCatching { transform(this) }
        // log the failure once and memoize
        result.exceptionOrNull()?.printStackTrace()
        memo = TransformMemo(transform, result)
        return result.getOrThrow()
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

@Serializable
inline class VertexKind(override val id: Int) : Id, Comparable<VertexKind> {
    override fun compareTo(other: VertexKind): Int = id.compareTo(other.id)
    override fun toString(): String = idString(id, 'A', 'Z')
}

interface Vertex : Id, Vec3 {
    val kind: VertexKind
}

class MutableVertex(
    override val id: Int,
    pt: Vec3,
    override val kind: VertexKind,
) : Vertex, MutableVec3(pt) {
    override fun equals(other: Any?): Boolean = other is Vertex && id == other.id
    override fun hashCode(): Int = id
    override fun toString(): String = "$kind vertex(id=$id, ${super.toString()})"
}

fun Vertex.toMutableVertex() = MutableVertex(id, this, kind)

@Serializable
inline class FaceKind(override val id: Int) : Id, Comparable<FaceKind> {
    override fun compareTo(other: FaceKind): Int = id.compareTo(other.id)
    override fun toString(): String = idString(id, 'α', 'ω')
}

interface Face : Id, Plane {
    val fvs: List<Vertex>
    val kind: FaceKind
    val dualKind: FaceKind
}

class MutableFace(
    override val id: Int,
    override val fvs: List<Vertex>,
    override val kind: FaceKind,
    override val dualKind: FaceKind = kind // used only for by cantellation
) : Face, MutablePlane(plane3(fvs[0], fvs[1], fvs[2])) {
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
    get() = b - a

val Edge.len: Double
    get() = vec.norm

fun Edge.distanceTo(p: Vec3): Double =
    p.distanceToLine(a, b)

class PolyhedronBuilder {
    private val vs = ArrayList<Vertex>()
    private val fs = ArrayList<Face>()

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

    fun build() = Polyhedron(vs, fs)

    fun debugDump() {
        for (v in vs) println(v)
        for (f in fs) println(f)
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

