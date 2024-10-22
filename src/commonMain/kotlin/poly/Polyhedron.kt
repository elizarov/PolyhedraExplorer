/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.poly

import kotlinx.serialization.*
import polyhedra.common.transform.*
import polyhedra.common.util.*
import kotlin.jvm.*

@Serializable(with = PolyhedronSerializer::class)
class Polyhedron(
    vs: List<MutableVertex>,
    fs: List<MutableFace>,
    val faceKindSources: List<FaceKindSource>? // non-null when polyhedron was transformed
)  {
    val vs: List<Vertex> = vs
    val fs: List<Face> = fs
    val es: List<Edge>
    val directedEdges: List<Edge>

    // `build edges (unidirectional & directed) and link them with vertices and faces
    init {
        val es = ArrayList<Edge>()
        val directedEdges = ArrayList<Edge>()
        val lFaces = ArrayIdMap<Vertex, HashMap<Vertex, MutableFace>>()
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
                val a = rf.fvs[i]
                val b = rf.fvs[(i + 1) % rf.size]
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
                a.directedEdges.add(ea)
                b.directedEdges.add(eb)
                rf.directedEdges.add(ea)
                lf.directedEdges.add(eb)
            }
        }
        for (v in vs) v.directedEdges.sortVertexAdjacentEdges(v)
        for (f in fs) f.directedEdges.sortFaceAdjacentEdges(f)
        this.es = es
        this.directedEdges = directedEdges
    }

    val vertexKinds: IdMap<VertexKind, Vertex> by lazy { vs.associateById({ it.kind }, { it }) }
    val faceKinds: IdMap<FaceKind, Face> by lazy { fs.associateById({ it.kind }, { it }) }
    val edgeKinds: Map<EdgeKind, Edge> by lazy { es.associateBy({ it.kind }, { it }) }
    val directedEdgeKinds: Map<EdgeKind, Edge> by lazy { directedEdges.associateBy({ it.kind }, { it }) }

    val vertexKindCount: IdMap<VertexKind, Int> by lazy { vs.groupingBy { it.kind }.eachCountTo(ArrayIdMap()) }
    val faceKindCount: IdMap<FaceKind, Int> by lazy { fs.groupingBy { it.kind }.eachCountTo(ArrayIdMap()) }
    val edgeKindCount: Map<EdgeKind, Int> by lazy { es.groupingBy { it.kind }.eachCount() }

    val nonPlanarFaceKinds by lazy {
        faceKinds.entries
            .filter { (_, f0) -> !f0.isPlanar }
            .map { (fk, _) -> fk }
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

    val isoEdges: List<IsoEdge> by lazy {
        val ke = directedEdges.associateBy({ it.kind }, { it })
        val ki = ke.entries.associateBy(
            keySelector = { (ek, _) -> ek },
            valueTransform = { (ek, e) ->
                IsoEdge(ek, EdgeEquivalenceClass(e))
            }
        )
        for (ie in ki.values) {
            ie.lNext = ki[ke[ie.kind]!!.next(IsoDir.L).kind]!!
            ie.rNext = ki[ke[ie.kind]!!.next(IsoDir.R).kind]!!
        }
        ki.values.toList()
    }

    val inradius: Double by lazy { fs.minOf { f -> f.d } }
    val midradius: Double by lazy { es.avgOf { e -> e.midPoint(MidPoint.Closest).norm } }
    val circumradius: Double by lazy { vs.maxOf { v -> v.norm } }

    fun scaleDenominator(scale: Scale): Double = when(scale) {
        Scale.Inradius -> inradius
        Scale.Midradius -> midradius
        Scale.Circumradius -> circumradius
    }
    
    // Radius that is used for for polar reciprocation to compute dual,
    // the key requirement is that dual points of regular polygon's faces must be in the centers of those faces
    val dualReciprocationRadius: Double
        get() = inradius

    val edgesMidPointDefault: MidPoint by lazy {
        if (es.all { e -> e.isTangentInSegment() }) MidPoint.Tangent else MidPoint.Center
    }

    private val faceRims by lazy { ArrayIdMap<Face, FaceRim>() }

    fun faceRim(f: Face) =
        faceRims.getOrPut(f) { FaceRim(f) }

    val canDrop: Set<AnyKind> by lazy {
        val kinds = mutableSetOf<AnyKind>()
        kinds += vertexKinds.keys
        kinds += faceKinds.keys
        kinds += edgeKinds.keys
        kinds.filter {
            DropExpansion(this, it).isValid
        }.toSet()
    }

    override fun toString(): String =
        "Polyhedron(vs=${vs.size}, es=${es.size}, fs=${fs.size})"
}

private fun MutableList<Edge>.sortVertexAdjacentEdges(v: Vertex) {
    require(all { it.a == v })
    for (i in 1 until size) {
        val prev = this[i - 1].r
        val j = (i until size).first { this[it].l == prev }
        swap(i, j)
    }
}

private fun MutableList<Edge>.sortFaceAdjacentEdges(f: Face) {
    require(all { it.r == f })
    for (i in 1 until size) {
        val prev = this[i - 1].b
        val j = (i until size).first { this[it].a == prev }
        swap(i, j)
    }
}

private fun idString(id: Int, from: Char, to: Char): String {
    val n = to - from + 1
    val ch = from + (id % n)
    val rem = id / n
    if (rem == 0) return ch.toString()
    return idString(rem - 1, from, to) + ch
}

private fun String.toIdOrNull(from: Char, to: Char): Int? {
    return when(length) {
        0 -> null
        1 -> if (first() in from..to) first() - from else null
        else -> {
            val ch = last()
            if (ch !in from..to) return null
            val prefix = dropLast(1).toIdOrNull(from, to) ?: return null
            (prefix + 1) * (to - from + 1) + (ch - from)
        }
    }
}

interface MutableKind<K : Id> {
    var kind: K
}

interface AnyKind

fun String.toAnyKindOrNull(): AnyKind? {
    toEdgeKindOrNull()?.let { return it }
    toVertexKindOrNull()?.let { return it }
    toFaceKindOrNull()?.let { return it }
    return null
}

interface FaceKindSource {
    val kind: FaceKind
    val source: AnyKind
}

data class MutableFaceKindSource(
    override var kind: FaceKind,
    override val source: AnyKind
) : MutableKind<FaceKind>, FaceKindSource {
    override fun toString(): String = "$kind<-$source"
}

@Serializable
@JvmInline
value class VertexKind(override val id: Int) : Id, AnyKind, Comparable<VertexKind> {
    override fun compareTo(other: VertexKind): Int = id.compareTo(other.id)
    override fun toString(): String = idString(id, 'A', 'Z')
}

fun String.toVertexKindOrNull() =
    toIdOrNull('A', 'Z')?.let { VertexKind(it) }

interface Vertex : Id, Vec3 {
    val kind: VertexKind
    val directedEdges: List<Edge> // edges are properly ordered clockwise
}

class MutableVertex(
    override val id: Int,
    pt: Vec3,
    override var kind: VertexKind,
    override val directedEdges: MutableList<Edge> = ArrayList() // edges are properly ordered clockwise
) : Vertex, MutableVec3(pt), MutableKind<VertexKind> {
    override fun equals(other: Any?): Boolean = other is Vertex && id == other.id
    override fun hashCode(): Int = id
    override fun toString(): String = "$kind vertex(id=$id, ${super.toString()})"
}

fun Vertex.toMutableVertex(): MutableVertex =
    MutableVertex(id, this, kind, directedEdges.toMutableList())

@Serializable
@JvmInline
value class FaceKind(override val id: Int) : Id, AnyKind, Tagged, Comparable<FaceKind> {
    override fun compareTo(other: FaceKind): Int = id.compareTo(other.id)
    override fun toString(): String = idString(id, 'α', 'ω')
    override val tag: String get() = toString()
}

fun String.toFaceKindOrNull() =
    toIdOrNull('α', 'ω')?.let { FaceKind(it) }

interface Face : Id, Plane {
    val fvs: List<Vertex>
    val kind: FaceKind
    val isPlanar: Boolean
    val directedEdges: List<Edge> // edges are properly ordered clockwise
}

class MutableFace(
    override val id: Int,
    override val fvs: List<MutableVertex>,
    override var kind: FaceKind,
    override val directedEdges: MutableList<Edge> = ArrayList() // edges are properly ordered clockwise
) : Face, MutablePlane(fvs.averagePlane()), MutableKind<FaceKind> {
    override val isPlanar = fvs.all { it in this }

    override fun equals(other: Any?): Boolean = other is Face && id == other.id
    override fun hashCode(): Int = id
    override fun toString(): String =
        "$kind face(id=$id, [${fvs.map { it.id }.joinToString(", ")}])"
}

val Face.size: Int get() = fvs.size
operator fun Face.get(index: Int): Vertex = fvs[index]
operator fun Face.iterator(): Iterator<Vertex> = fvs.iterator()

@Serializable
data class EdgeKind(val a: VertexKind, val b: VertexKind, val l: FaceKind, val r: FaceKind) : AnyKind, Comparable<EdgeKind> {
    override fun compareTo(other: EdgeKind): Int {
        if (a != other.a) return a.compareTo(other.a)
        if (b != other.b) return b.compareTo(other.b)
        if (l != other.l) return l.compareTo(other.l)
        return r.compareTo(other.r)
    }

    override fun toString(): String = "$a-$l/$r-$b"
}

fun String.toEdgeKindOrNull(): EdgeKind? {
    val s = split("-").takeIf { it.size == 3 } ?: return null
    val a = s[0].toVertexKindOrNull() ?: return null
    val b = s[2].toVertexKindOrNull() ?: return null
    val t = s[1].split("/").takeIf { it.size == 2 } ?: return null
    val l = t[0].toFaceKindOrNull() ?: return null
    val r = t[1].toFaceKindOrNull() ?: return null
    return EdgeKind(a, b, l, r)
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
    override fun toString(): String = "$kind edge(${a.id}-${l.id}/${r.id}-${b.id})"

    // next clockwise edge on right/left face
    fun next(dir: IsoDir): Edge = when (dir) {
        IsoDir.R -> r.directedEdges.find { it.a == b }!!
        IsoDir.L -> l.directedEdges.find { it.b == b }!!.reversed
    }
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

private fun <T> MutableList<T>.swap(i: Int, j: Int) {
    val t = this[i]
    this[i] = this[j]
    this[j] = t
}


