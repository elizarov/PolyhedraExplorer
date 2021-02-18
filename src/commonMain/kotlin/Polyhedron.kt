package polyhedra.common

class Polyhedron(
    val vs: List<Vertex>,
    val fs: List<Face>
) {
    val es: List<Edge> = buildList {
        var id = 0
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
                    add(Edge(id++, a, b, lf, f).normalizedDirection())
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
                entry.value.sortedAdjacentEdges(entry.key)
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

fun List<Edge>.sortedAdjacentEdges(a: Vertex): List<Edge> {
    require(all { it.a == a })
    val result = toMutableList()
    for (i in 1 until size) {
        val prev = result[i - 1].r
        val j = (i until size).first { result[it].l == prev }
        result.swap(i, j)
    }
    return result
}

inline class VertexKind(override val id: Int) : Id, Comparable<VertexKind> {
    override fun compareTo(other: VertexKind): Int = id.compareTo(other.id)
    override fun toString(): String = "${'A' + id}"
}

class Vertex(
    override val id: Int,
    val pt: Vec3,
    val kind: VertexKind,
) : Id {
    override fun equals(other: Any?): Boolean = other is Vertex && id == other.id
    override fun hashCode(): Int = id
    override fun toString(): String = "$kind-vertex(id=$id, $pt)"
}

inline class FaceKind(override val id: Int) : Id, Comparable<FaceKind> {
    override fun compareTo(other: FaceKind): Int = id.compareTo(other.id)
    override fun toString(): String = "${'α' + id}"
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
        "$kind-face(id=$id, [${fvs.map { it.id }.joinToString(", ")}])"
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

class Edge(
    override val id: Int,
    val a: Vertex,
    val b: Vertex,
    val l: Face,
    val r: Face,
) : Id {
    val kind: EdgeKind = EdgeKind(a.kind, b.kind, l.kind, r.kind)
    override fun equals(other: Any?): Boolean = other is Edge && id == other.id
    override fun hashCode(): Int = id
    override fun toString(): String = "$kind edge(${a.id}-${l.id}/${r.id}-${b.id})"
}

fun Edge.reversed(): Edge = Edge(id, b, a, r, l)

fun Edge.normalizedDirection(): Edge =
    if (kind.reversed() < kind) reversed() else this

val Edge.vec: Vec3
    get() = b.pt - a.pt

val Edge.len: Double
    get() = vec.norm

fun Polyhedron.validate() {
    validateGeometry()
    validateKinds()
}

fun Polyhedron.validateGeometry() {
    // Validate edges
    for (e in es) {
        require((e.a.pt - e.b.pt).norm > EPS) {
            "$e non-degenerate"
        }
    }
    // Validate faces
    for (f in fs) {
        require(f.plane.d > 0) {
            "Face normal does not point outwards: $f ${f.plane} "
        }
        for (v in f.fvs)
            require(v.pt in f.plane) {
                "Face is not planar: $f, $v !in ${f.plane}"
            }
        for (i in 0 until f.size) {
            val a = f[i].pt
            val b = f[(i + 1) % f.size].pt
            val c = f[(i + 2) % f.size].pt
            val rot = (c - a) cross (b - a)
            require(rot * f.plane.n > -EPS) {
                "Face is not clockwise: $f, vertices $a $b $c"
            }
        }
    }
}

fun Polyhedron.validateKinds() {
    // Validate face kinds
    for ((fk, fs) in faceKinds) {
        fs.validateUnique("$fk-face distances") { it.plane.d }
    }
    // Validate vertex kinds
    for ((vk, vs) in vertexKinds) {
        vs.validateUnique("$vk-vertex distances") { it.pt.norm }
    }
    // Validate edge kinds
    for ((ek, es) in edgeKinds) {
        es.validateUnique("$ek edge distances") { it.midPoint(MidPoint.Closest).norm }
        es.validateUnique("$ek edge lengths") { it.len }
    }
}

private fun <T> Collection<T>.validateUnique(msg: String, selector: (T) -> Double) {
    val d = associateWith(selector)
    require(d.values.span() < EPS) {
        "$msg are different: ${d.entries.minByOrNull { it.value }} -- ${d.entries.maxByOrNull { it.value }}"
    }
}

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

    fun face(fvIds: List<Int>, kind: FaceKind) {
        fs.add(Face(fs.size, fvIds.map { vs[it] }, kind))
    }

    fun face(f: Face) {
        val a = List(f.size) { vs[f[it].id] }
        fs.add(Face(fs.size, a, f.kind))
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

