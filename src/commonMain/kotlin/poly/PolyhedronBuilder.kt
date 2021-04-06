package polyhedra.common.poly

import polyhedra.common.util.*

private const val DEBUG_MERGE_KINDS = false

class PolyhedronBuilder(
    private var mergeIndistinguishableKinds: Boolean = false
) {
    private val vs: ArrayList<MutableVertex> = ArrayList()
    private val fs: ArrayList<MutableFace> = ArrayList()
    private val faceKindSources: ArrayList<MutableFaceKindSource> = ArrayList()
    private var forceFaceKinds: List<FaceKindSource>? = null

    fun vertex(p: Vec3, kind: VertexKind = VertexKind(0)): Vertex =
        MutableVertex(vs.size, p, kind).also { vs.add(it) }

    fun vertex(x: Double, y: Double, z: Double, kind: VertexKind = VertexKind(0)): Vertex =
        vertex(Vec3(x, y, z), kind)

    fun vertex(v: Vertex): Vertex =
        vertex(v, v.kind)

    fun vertices(vs: List<Vertex>) {
        for (v in vs) vertex(v)
    }

    fun face(vararg fvIds: Int, kind: FaceKind = FaceKind(0)) {
        val a = List(fvIds.size) { vs[fvIds[it]] }
        fs.add(MutableFace(fs.size, a, kind))
    }

    fun face(fvIds: List<Int>, kind: FaceKind = FaceKind(0)) {
        val a = List(fvIds.size) { vs[fvIds[it]] }
        fs.add(MutableFace(fs.size, a, kind))
    }

    fun face(fvs: Collection<Vertex>, kind: FaceKind) {
        fs.add(MutableFace(fs.size, fvs.map { vs[it.id] }, kind))
    }

    fun face(f: Face) {
        face(f.fvs, f.kind)
    }

    fun faces(fs: List<Face>) {
        for (f in fs) face(f)
    }

    fun faceKindSource(kind: FaceKind, source: AnyKind) {
        faceKindSources += MutableFaceKindSource(kind, source)
    }

    fun faceKindSources(faceKindSources: List<FaceKindSource>?) {
        if (faceKindSources == null) return
        for (fks in faceKindSources) faceKindSource(fks.kind, fks.source)
    }

    // Optimization: in sync with the corresponding methods of Polyhedron
    private val inradius: Double get() = fs.minOf { f -> f.d }
    private val circumradius: Double get() = vs.maxOf { v -> v.norm }
    private val midradius: Double
        get() {
            var sum = 0.0
            var cnt = 0
            for (f in fs) {
                for (i in 0 until f.size) {
                    val a = f[i]
                    val b = f[(i + 1) % f.size]
                    if (a.id < b.id) {
                        sum += midPointFraction(a, b, MidPoint.Closest).distanceAtSegment(a, b)
                        cnt++
                    }
                }
            }
            return sum / cnt
        }

    private fun scaleDenominator(scale: Scale): Double = when (scale) {
        Scale.Inradius -> inradius
        Scale.Midradius -> midradius
        Scale.Circumradius -> circumradius
    }

    fun scale(scale: Scale?) {
        if (scale == null) return
        val current = scaleDenominator(scale)
        if (current approx 1.0) return
        for (v in vs) v /= current
    }

    fun forceFaceKinds(forceFaceKinds: List<FaceKindSource>?) {
        this.forceFaceKinds = forceFaceKinds
    }

    fun build(): Polyhedron {
        val forceFaceKinds = forceFaceKinds
        if (forceFaceKinds != null) mergeIndistinguishableKinds = false // don't merge when kinds are forced
        // Fill missing face kind sources as identity if needed
        if (faceKindSources.isNotEmpty() || forceFaceKinds != null || mergeIndistinguishableKinds) {
            val added = faceKindSources.mapTo(HashSet()) { it.kind }
            for (f in fs) {
                if (added.add(f.kind)) faceKindSource(f.kind, f.kind)
            }
        }
        // Force face kinds if requested
        if (forceFaceKinds != null) {
            val fkSrc = faceKindSources.associateBy({ it.kind }, { it.source })
            val fkForce = forceFaceKinds.associateBy({ it.source }, { it.kind })
            for (f in fs) {
                val forcedKind = fkForce[fkSrc[f.kind]!!]
                if (forcedKind != null) f.kind = forcedKind
            }
        }
        // Build poly
        // NOTE: New poly needs faceKindSources only when they were not forced
        val poly = Polyhedron(
            vs, fs, faceKindSources.takeIf { faceKindSources.isNotEmpty() && forceFaceKinds == null }
        )
        // return the result if face kind merging is not requested
        if (!mergeIndistinguishableKinds) return poly
        // Group indistinguishable face kinds
        val ge = poly.isoEdges.groupIndistinguishable()
        val gk = ge.mapTo(HashSet()) { list ->
            val vks = list.mapTo(HashSet()) { it.kind.a }
            val fks = list.mapTo(HashSet()) { it.kind.r }
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
        // Renumber kinds
        vs.renumberKinds(vkg) { VertexKind(it) }
        fs.renumberKinds(fkg) { FaceKind(it) }
        faceKindSources.renumberKinds(fkg) { FaceKind(it) }
        // Rebuild polyhedron with renumbered kinds
        return polyhedronCopy(vs, fs, faceKindSources)
    }

    fun debugDump() {
        for (v in vs) println(v)
        for (f in fs) println(f)
    }

    fun mergeIndistinguishableKinds() {
        mergeIndistinguishableKinds = true
    }
}

fun polyhedron(mergeIndistinguishableKinds: Boolean = false, block: PolyhedronBuilder.() -> Unit): Polyhedron =
    PolyhedronBuilder(mergeIndistinguishableKinds).run {
        block()
        build()
    }

fun polyhedronCopy(
    vs: List<Vertex>,
    fs: List<Face>,
    faceKindSources: List<FaceKindSource>? = null,
    mergeIndistinguishableKinds: Boolean = false
) =
    polyhedron(mergeIndistinguishableKinds) {
        vertices(vs)
        faces(fs)
        faceKindSources(faceKindSources)
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
