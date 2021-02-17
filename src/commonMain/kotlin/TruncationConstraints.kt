package polyhedra.common

import kotlin.math.*

data class TruncationConstraints(
    val cs: List<TruncationConstraint>
)

data class TruncationConstraint(
    val kind: EdgeKind,
    val a: Double,
    val x: VertexKind,
    val b: Double,
    val y: VertexKind,
    val c: Double
) {
    override fun toString(): String =
        "$kind: ${a.fmt} * $x + ${b.fmt} * $y <= ${c.fmt}"
}

fun Polyhedron.computeTruncationConstraints(): TruncationConstraints {
    val cs = edgeKinds.entries.map { (ek, es) ->
        val e = es[0]
        val vec = e.vec
        val len = vec.norm
        TruncationConstraint(
            ek,
            -len * (e.a.pt * e.a.pt) / (vec * e.a.pt), e.a.kind,
            len * (e.b.pt * e.b.pt) / (vec * e.b.pt), e.b.kind,
            len
        )
    }
    return TruncationConstraints(cs)
}

fun TruncationConstraints.solveRectification(): Map<VertexKind, Double>? {
    val sol = HashMap<VertexKind, Double>()
    fun update(a: VertexKind, x: Double): Boolean {
        val prev = sol[a]
        if (prev != null && !(prev approx x)) return false
        sol[a] = x
        return true
    }
    val rem = cs.toMutableList()
    while (rem.isNotEmpty()) {
        val cc = rem.removeLast()
        if (cc.x == cc.y) {
            if (!update(cc.x, cc.c / (cc.a + cc.b))) return null
        }
        val x = sol[cc.x]
        val y = sol[cc.y]
        if (x != null && y != null) {
            val diff = cc.a * x + cc.b * y - cc.c
            if (abs(diff) >= EPS) return null
            continue
        }
        if (x != null) {
            if (!update(cc.y, (cc.c - cc.a * x) / cc.b)) return null
            continue
        }
        if (y != null) {
            if (!update(cc.x, (cc.c - cc.b * y) / cc.a)) return null
            continue
        }

        for (i in rem.indices) {
            var t = rem[i]
            if (t.x == cc.x) {
                rem[i] = TruncationConstraint(
                    t.kind,
                    -cc.b / cc.a * t.a, cc.y,
                    t.b, t.y,
                    -cc.c / cc.a * t.a + t.c
                ).also { t = it }
            }
            if (t.y == cc.x) {
                rem[i] = TruncationConstraint(
                    t.kind,
                    t.a, t.x,
                    -cc.b / cc.a * t.b, cc.y,
                    -cc.c / cc.a * t.b + t.c
                )
            }
        }
    }
    for (t in cs) {
        val y = sol[t.y] ?: return null
        // x = c/a - b/a * y
        val x = t.c / t.a - t.b / t.a * y
        if (!update(t.x, x)) return null
    }
    for (f in sol.values) if (f < EPS) return null
    return sol
}
