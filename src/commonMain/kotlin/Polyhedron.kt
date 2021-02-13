package polyhedra.common

class Polyhedron(
    val vs: List<Vertex>,
    val fs: List<Face>
) {
    val es = buildList {
        var id = 0
        for (f in fs) {
            for (i in 0 until f.size) {
                val u = f[i]
                val v = f[(i + 1) % f.size]
                if (u.id < v.id)
                    add(Edge(id++, u, v))
            }
        }
    }

    val vsByKind by lazy { vs.groupByToList { it.kind.id } }
    val fsByKind by lazy { fs.groupByToList { it.kind.id } }
    val esByKind by lazy { es.groupBy { it.kind } }

    override fun toString(): String =
        "Polyhedron(vs=${vs.size}, es=${es.size}, fs=${fs.size})"
}

inline class Kind(val id: Int) : Comparable<Kind> {
    override fun compareTo(other: Kind): Int = id.compareTo(other.id)
    override fun toString(): String = "$id"
}

class Vertex(
    val id: Int,
    val pt: Vec3,
    val kind: Kind,
) {
    
}

class Edge(
    val id: Int,
    val u: Vertex,
    val v: Vertex,
) {
    val kind = if (u.kind <= v.kind)
        EdgeKind(u.kind, v.kind) else
        EdgeKind(v.kind, u.kind)
}

data class EdgeKind(val u: Kind, val v: Kind) {
    override fun toString(): String = "$u-$v"
}

class Face(
    val id: Int,
    val vs: List<Vertex>,
    val kind: Kind,
) {
    val plane = plane3(vs[0].pt, vs[1].pt, vs[2].pt)

    val size: Int
        get() = vs.size
    operator fun get(index: Int): Vertex = vs[index]
    operator fun iterator(): Iterator<Vertex> = vs.iterator()
}

fun Polyhedron.validate() {
    // Validate faces
    for (f in fs) {
        for (v in f.vs)
            check(v.pt in f.plane)
        for (i in 0 until f.size) {
            val u = f[i].pt
            val v = f[(i + 1) % f.size].pt
            val w = f[(i + 2) % f.size].pt
            val rot = (w - u) cross (v - u)
            check(rot * f.plane.n > -EPS)
        }
    }
}

class PolyhedronBuilder {
    private val vs = ArrayList<Vertex>()
    private val fs = ArrayList<Face>()

    fun vertex(p: Vec3, kind: Kind = Kind(0)) {
        vs.add(Vertex(vs.size, p, kind))
    }

    fun vertex(x: Double, y: Double, z: Double, kind: Kind = Kind(0)) {
        vertex(Vec3(x, y, z), kind)
    }

    fun face(v: List<Int>, kind: Kind = Kind(0)) {
        val a = List(v.size) { vs[v[it]] }
        fs.add(Face(fs.size, a, kind))
    }

    fun face(vararg v: Int, kind: Kind = Kind(0)) {
        val a = List(v.size) { vs[v[it]] }
        fs.add(Face(fs.size, a, kind))
    }

    fun build() = Polyhedron(vs, fs)
}

fun polyhedron(block: PolyhedronBuilder.() -> Unit): Polyhedron =
    PolyhedronBuilder().run {
        block()
        build()
    }

fun List<Vertex>.sortedFace(): List<Vertex> {
    TODO()
}

