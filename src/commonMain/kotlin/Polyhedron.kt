package polyhedra.common

class Polyhedron(
    val vs: List<Vertex>,
    val fs: List<Face>
) {
    val es: List<Edge> = buildList {
        var id = 0
        for (f in fs) {
            for (i in 0 until f.size) {
                val a = f[i]
                val b = f[(i + 1) % f.size]
                if (a.id < b.id)
                    add(Edge(id++, a, b))
            }
        }
    }

    val kindVertices: IdMap<Kind, List<Vertex>> by lazy { vs.groupById { it.kind } }
    val kindFaces: IdMap<Kind, List<Face>> by lazy { fs.groupById { it.kind } }
    val kindEdges: Map<EdgeKind, List<Edge>> by lazy { es.groupBy { it.kind } }

    val vertexFaces: IdMap<Vertex, List<Face>> by lazy {
        fs
            .flatMap { f -> f.fvs.map { v -> v to f } }
            .groupById({ it.first }, { it.second })
    }

    val vertexEdges: IdMap<Vertex, Map<Vertex, Edge>> by lazy {
        es
            .flatMap { e -> listOf(Triple(e.a, e.b, e), Triple(e.b, e.a, e)) }
            .groupById { it.first }
            .mapValues { entry -> entry.value.associateBy({ it.second }, { it.third }) }
    }

    val edgeKinds: Map<EdgeKind, Kind> by lazy {
        es.asSequence()
            .map { it.kind }
            .distinctIndexed { Kind(it) }
    }

    val directedEdgeKinds: Map<EdgeKind, Kind> by lazy {
        es.asSequence()
            .flatMap { listOf(it.kind, EdgeKind(it.b.kind, it.a.kind)) }
            .distinctIndexed { Kind(it) }
    }

    val inradius: Double by lazy { fs.minOf { f -> f.plane.d } }
    val midradius: Double by lazy { es.avgOf { e -> e.tangentPoint.norm } }
    val circumradius: Double by lazy { vs.maxOf { v -> v.pt.norm } }
    
    override fun toString(): String =
        "Polyhedron(vs=${vs.size}, es=${es.size}, fs=${fs.size})"
}

inline class Kind(override val id: Int) : Id, Comparable<Kind> {
    override fun compareTo(other: Kind): Int = id.compareTo(other.id)
    override fun toString(): String = "$id"
}

class Vertex(
    override val id: Int,
    val pt: Vec3,
    val kind: Kind,
) : Id {
    override fun equals(other: Any?): Boolean = other is Vertex && id == other.id
    override fun hashCode(): Int = id
    override fun toString(): String = "Vertex(id=$id, $pt)"
}

data class EdgeKind(val a: Kind, val b: Kind) : Comparable<EdgeKind> {
    override fun compareTo(other: EdgeKind): Int {
        if (a != other.a) return a.compareTo(other.a)
        return b.compareTo(other.b)
    }

    override fun toString(): String = "$a-$b"
}

class Edge(
    override val id: Int,
    val a: Vertex,
    val b: Vertex,
) : Id {
    val kind = if (a.kind <= b.kind)
        EdgeKind(a.kind, b.kind) else
        EdgeKind(b.kind, a.kind)

    val vec = b.pt - a.pt
}

fun tangentFraction(a: Vec3, vec: Vec3): Double =
    -(a * vec) / (vec * vec)

val Edge.tangentFraction: Double
    get() = tangentFraction(a.pt, vec)

val Edge.tangentPoint: Vec3
    get() = a.pt + tangentFraction * vec

class Face(
    override val id: Int,
    val fvs: List<Vertex>,
    val kind: Kind,
) : Id {
    val plane = plane3(fvs[0].pt, fvs[1].pt, fvs[2].pt)

    val size: Int
        get() = fvs.size
    operator fun get(index: Int): Vertex = fvs[index]
    operator fun iterator(): Iterator<Vertex> = fvs.iterator()

    override fun equals(other: Any?): Boolean = other is Vertex && id == other.id
    override fun hashCode(): Int = id
    override fun toString(): String =
        "Face(id=$id, [${fvs.map { it.id }.joinToString(", ")}])"
}

fun Polyhedron.validate() {
    // Validate edges
    for (e in es) {
        require(e.vec.norm > EPS) {
            "$e non-degenerate"
        }
    }
    // Validate faces
    for (f in fs) {
        require(f.plane.d > 0) {
            "$f ${f.plane} normal points outwards"
        }
        for (v in f.fvs)
            require(v.pt in f.plane) {
                "$v in $f ${f.plane}"
            }
        for (i in 0 until f.size) {
            val a = f[i].pt
            val b = f[(i + 1) % f.size].pt
            val c = f[(i + 2) % f.size].pt
            val rot = (c - a) cross (b - a)
            require(rot * f.plane.n > -EPS) {
                "$f vertices $a $b $c in clock-wise order"
            }
        }
    }
}

class PolyhedronBuilder {
    private val vs = ArrayList<Vertex>()
    private val fs = ArrayList<Face>()

    fun vertex(p: Vec3, kind: Kind = Kind(0)): Vertex =
        Vertex(vs.size, p, kind).also { vs.add(it) }

    fun vertex(x: Double, y: Double, z: Double, kind: Kind = Kind(0)): Vertex =
        vertex(Vec3(x, y, z), kind)

    fun face(vararg fvIds: Int, kind: Kind = Kind(0)) {
        val a = List(fvIds.size) { vs[fvIds[it]] }
        fs.add(Face(fs.size, a, kind))
    }

    fun face(fvIds: List<Int>, kind: Kind, sort: Boolean = false) {
        val a = MutableList(fvIds.size) { vs[fvIds[it]] }
        if (sort) a.sortFace()
        fs.add(Face(fs.size, a, kind))
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

private fun MutableList<Vertex>.sortFace() {
    check(size >= 3)
    // start with vertex #0
    val v0 = this[0]
    val n = plane3(v0.pt, this[1].pt, this[2].pt).normalFromOrigin().n
    // find proper vertex #1
    val i1 = (1 until size).first { i1 ->
        // the rest of vertices must to be the right
        val v1 = this[i1]
        all { v2 -> ((v2.pt - v0.pt) cross (v1.pt - v0.pt)) * n > -EPS }
    }
    swap(1, i1)
    // select #2 and others
    for (i in 2 until size) {
        val prev = this[i - 1].pt
        val u = (prev - this[i - 2].pt).unit
        val j = (i until size).maxByOrNull { j ->
            (this[j].pt - prev).unit * u
        }!!
        swap(i, j)
    }
}

private fun <T> MutableList<T>.swap(i: Int, j: Int) {
    val t = this[i]
    this[i] = this[j]
    this[j] = t
}

