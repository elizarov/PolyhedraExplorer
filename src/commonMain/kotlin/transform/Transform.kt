/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.transform

import polyhedra.common.poly.*
import polyhedra.common.util.*
import kotlin.math.*

enum class Transform(
    override val tag: String,
    val transform: (Polyhedron) -> Polyhedron,
    val isApplicable: (Polyhedron) -> Boolean = { true }, // todo: not defined usefully now
    val truncationRatio: (Polyhedron) -> Double? = { null },
    val cantellationRatio: (Polyhedron) -> Double? = { null },
    val bevellingRatio: (Polyhedron) -> BevellingRatio? = {
        val cr = cantellationRatio(it)
        val tr = truncationRatio(it)
        if (cr == null && tr == null) null else BevellingRatio(cr ?: 0.0, tr ?: 0.0)
    },
    val snubbingRatio: (Polyhedron) -> SnubbingRatio? = {
        cantellationRatio(it)?.let { cr -> SnubbingRatio(cr, 0.0) }
    },
    val chamferingRatio: (Polyhedron) -> Double? = { null },
    val asyncTransform: (suspend (Polyhedron, OperationProgressContext) -> Polyhedron)? = null,
    val isIdentityTransform: (Polyhedron) -> Boolean = { false },
    val fev: TransformFEV
) : Tagged {
    None(
        "n",
        { it },
        truncationRatio = { 0.0 },
        cantellationRatio = { 0.0 },
        chamferingRatio = { 0.0 },
        isIdentityTransform = { true },
        fev = TransformFEV.ID
    ),
    Truncated(
        "t",
        Polyhedron::truncated,
        truncationRatio = { it.regularTruncationRatio() },
        fev = TransformFEV(
            1, 0, 1,
            0, 3, 0,
            0, 2, 0
        )
    ),
    Rectified(
        "a",
        Polyhedron::rectified,
        truncationRatio = { 1.0 },
        fev = TransformFEV(
            1, 0, 1,
            0, 2, 0,
            0, 1, 0
        )
    ),
    Cantellated( // ~= Rectified, Rectified
        "e",
        Polyhedron::cantellated,
        cantellationRatio = { it.regularCantellationRatio() },
        fev = TransformFEV(
            1, 1, 1,
            0, 4, 0,
            0, 2, 0
        )
    ),
    Dual(
        "d",
        Polyhedron::dual,
        cantellationRatio = { 1.0 },
        fev = TransformFEV(
            0, 0, 1,
            0, 1, 0,
            1, 0, 0
        )
    ),
    Bevelled( // ~= Rectified, Truncated
        "b",
        Polyhedron::bevelled,
        bevellingRatio = { it.regularBevellingRatio() },
        fev = TransformFEV(
            1, 1, 1,
            0, 6, 0,
            0, 4, 0
        )
    ),
    Snub(
        "s",
        Polyhedron::snub,
        snubbingRatio = { it.regularSnubbingRatio() },
        fev = TransformFEV(
            1, 2, 1,
            0, 5, 0,
            0, 2, 0
        )
    ),
    Chamfered(
        "c",
        Polyhedron::chamfered,
        chamferingRatio = { it.chamferingRatio() },
        fev = TransformFEV(
            1, 2, 0,
            0, 4, 0,
            0, 1, 1
        )
    ),
    Canonical(
        "o",
        Polyhedron::canonical,
        asyncTransform = Polyhedron::canonical,
        isIdentityTransform = { it.isCanonical() },
        fev = TransformFEV.ID
    )
}

val Transforms: List<Transform> by lazy { Transform.values().toList() }

fun Polyhedron.transformed(transform: Transform) = transform.transform(this)

fun Polyhedron.transformed(transforms: List<Transform>) =
    transforms.fold(this) { poly, transform -> poly.transformed(transform) }

fun Polyhedron.transformed(vararg transforms: Transform) =
    transformed(transforms.toList())

fun Polyhedron.rectified(): Polyhedron = transformedPolyhedron(Transform.Rectified) {
    // vertices from the original edges
    val ev = es.associateWith { e ->
        vertex(e.midPoint(edgesMidPointDefault), VertexKind(edgeKindsIndex[e.kind]!!))
    }
    // faces from the original faces
    for (f in fs) {
        face(f.directedEdges.map { ev[it.normalizedDirection()]!! }, f.kind)
    }
    // faces from the original vertices
    val kindOfs = faceKinds.size
    for (v in vs) {
        face(v.directedEdges.map { ev[it.normalizedDirection()]!! }, FaceKind(kindOfs + v.kind.id))
    }
    for (vk in vertexKinds.keys) faceKindSource(FaceKind(kindOfs + vk.id), vk)
    mergeIndistinguishableKinds()
}

// ea == PI / face_size
fun regularTruncationRatio(ea: Double): Double = 1 / (1 + cos(ea))

fun Polyhedron.regularTruncationRatio(faceKind: FaceKind = FaceKind(0)): Double {
    val f = faceKinds[faceKind]!!.first() // take representative face of this kind
    return regularTruncationRatio(PI / f.size)
}

fun Polyhedron.truncated(
    tr: Double = regularTruncationRatio(),
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null
): Polyhedron = transformedPolyhedron(Transform.Truncated, tr, scale, forceFaceKinds) {
    // vertices from the original directed edges
    val ev = directedEdges.associateWith { e ->
        val t = tr * e.midPointFraction(edgesMidPointDefault)
        vertex(t.atSegment(e.a, e.b), VertexKind(directedEdgeKindsIndex[e.kind]!!))
    }
    // faces from the original faces
    for (f in fs) {
        val fvs = f.directedEdges.flatMap {
            listOf(ev[it]!!, ev[it.reversed]!!)
        }
        face(fvs, f.kind)
    }
    // faces from the original vertices
    val kindOfs = faceKinds.size
    for (v in vs) {
        face(v.directedEdges.map { ev[it]!! }, FaceKind(kindOfs + v.kind.id))
    }
    for (vk in vertexKinds.keys) faceKindSource(FaceKind(kindOfs + vk.id), vk)
    mergeIndistinguishableKinds()
}

data class RegularFaceGeometry(
    val ea: Double, // PI / face_size
    val da: Double  // dihedral angle
)

fun Polyhedron.regularFaceGeometry(edgeKind: EdgeKind? = null): RegularFaceGeometry {
    val ek = edgeKind ?: edgeKinds.keys.first() // min edge kind by default
    val e = edgeKinds[ek]!!.first() // representative edge
    val f = e.r // primary face
    val g = e.l // secondary face
    val n = f.size // primary face size
    val ea = PI / n
    val da = PI - acos(f * g) // dihedral angle
    return RegularFaceGeometry(ea, da)
}

fun Polyhedron.regularCantellationRatio(edgeKind: EdgeKind? = null): Double {
    val (ea, da) = regularFaceGeometry(edgeKind)
    return 1 / (1 + sin(da / 2) / tan(ea))
}

fun Polyhedron.cantellated(
    cr: Double = regularCantellationRatio(),
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null
): Polyhedron = transformedPolyhedron(Transform.Cantellated, cr, scale, forceFaceKinds) {
    val rr = dualReciprocationRadius
    // vertices from the directed edges
    val ev = directedEdges.associateWith { e ->
        val a = e.a // vertex for cantellation
        val f = e.r // primary face for cantellation
        val c = f.dualPoint(rr) // for regular polygons -- face center
        vertex(cr.atSegment(a, c), VertexKind(directedEdgeKindsIndex[e.kind]!!))
    }
    val fvv = ev.directedEdgeToFaceVertexMap()
    // faces from the original faces
    for (f in fs) {
        val fvs = f.directedEdges.map { ev[it]!! }
        face(fvs, f.kind)
    }
    // faces from the original vertices
    var kindOfs = faceKinds.size
    for (v in vs) {
        face(v.directedEdges.map { ev[it]!! }, FaceKind(kindOfs + v.kind.id))
    }
    for (vk in vertexKinds.keys) faceKindSource(FaceKind(kindOfs + vk.id), vk)
    // 4-faces from the original edges
    kindOfs += vertexKinds.size
    for (e in es) {
        val fvs = listOf(
            fvv[e.r]!![e.a]!!,
            fvv[e.l]!![e.a]!!,
            fvv[e.l]!![e.b]!!,
            fvv[e.r]!![e.b]!!
        )
        face(fvs, FaceKind(kindOfs + edgeKindsIndex[e.kind]!!))
    }
    for ((ek, id) in edgeKindsIndex) faceKindSource(FaceKind(kindOfs + id), ek)
    mergeIndistinguishableKinds()
}

fun Polyhedron.dual(): Polyhedron = transformedPolyhedron(Transform.Dual) {
    val rr = dualReciprocationRadius
    // vertices from the original faces
    val fv = fs.associateWith { f ->
        vertex(f.dualPoint(rr), VertexKind(f.kind.id))
    }
    // faces from the original vertices
    for (v in vs) {
        face(v.directedEdges.map { fv[it.r]!! }, FaceKind(v.kind.id))
    }
    for (vk in vertexKinds.keys) faceKindSource(FaceKind(vk.id), vk)
}

data class BevellingRatio(val cr: Double, val tr: Double) {
    override fun toString(): String = "(cr=${cr.fmt}, tr=${tr.fmt})"
}

fun Polyhedron.regularBevellingRatio(edgeKind: EdgeKind? = null): BevellingRatio {
    val (ea, da) = regularFaceGeometry(edgeKind)
    val tr = regularTruncationRatio(ea)
    val cr = (1 - tr) / (1 + sin(da / 2) / tan(ea) - tr)
    return BevellingRatio(cr, tr)
}

fun Polyhedron.bevelled(
    br: BevellingRatio = regularBevellingRatio(),
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null
): Polyhedron = transformedPolyhedron(Transform.Bevelled, br, scale, forceFaceKinds) {
    val (cr, tr) = br
    val rr = dualReciprocationRadius
    // vertices from the face-directed edges
    val fev = fs.associateWith { f ->
        val c = f.dualPoint(rr) // for regular polygons -- face center
        f.directedEdges.flatMap { e ->
            val kind = directedEdgeKindsIndex[e.kind]!!
            val a = e.a
            val b = e.b
            val ac = cr.atSegment(a, c)
            val bc = cr.atSegment(b, c)
            val mf = e.midPointFraction(edgesMidPointDefault)
            val t1 = tr * mf
            val t2 = tr * (1 - mf)
            listOf(
                e to vertex(t1.atSegment(ac, bc), VertexKind(2 * kind)),
                e.reversed to vertex(t2.atSegment(bc, ac), VertexKind(2 * kind + 1))
            )
        }.associate { it }
    }
    // faces from the original faces
    for (f in fs) {
        face(fev[f]!!.values, f.kind)
    }
    // faces from the original vertices
    var kindOfs = faceKinds.size
    for (v in vs) {
        val fvs = v.directedEdges.flatMap { e ->
            listOf(fev[e.l]!![e]!!, fev[e.r]!![e]!!)
        }
        face(fvs, FaceKind(kindOfs + v.kind.id))
    }
    for (vk in vertexKinds.keys) faceKindSource(FaceKind(kindOfs + vk.id), vk)
    // 4-faces from the original edges
    kindOfs += vertexKinds.size
    for (e in es) {
        val er = e.reversed
        val fvs = listOf(
            fev[e.r]!![e]!!,
            fev[e.l]!![e]!!,
            fev[e.l]!![er]!!,
            fev[e.r]!![er]!!
        )
        face(fvs, FaceKind(kindOfs + edgeKindsIndex[e.kind]!!))
    }
    for ((ek, id) in edgeKindsIndex) faceKindSource(FaceKind(kindOfs + id), ek)
    mergeIndistinguishableKinds()
}

// a * x^2 + b * x + c = 0
private fun solve3(a: Double, b: Double, c: Double) =
    (-b + sqrt(sqr(b) - 4 * a * c)) / (2 * a)

private fun snubComputeSA(ea: Double, da: Double, cr: Double): Double {
    val rf = 1 - cr
    val cm = (1 - cos(da)) / 2
    val cp = (1 + cos(da)) / 2
    val cosGA = solve3(cp * sqr(rf), 2 * cm * rf * cos(ea), -sqr(cos(ea)) * (cm + sqr(rf)))
    return ea - acos(cosGA)
}

private fun snubComputeA(ea: Double, da: Double, cr: Double, sa: Double): Double {
    val h = 1 / (2 * tan(ea))
    val ga = ea - sa
    val rf = 1 - cr
    val t = rf / (2 * sin(ea))
    return Vec3(
        2 * t * sin(ga),
        (h - t * cos(ga)) * (cos(da) - 1),
        (h - t * cos(ga)) * sin(da)
    ).norm
}

private fun snubComputeB(ea: Double, da: Double, cr: Double, sa: Double): Double {
    val h = 1 / (2 * tan(ea))
    val ga = ea - sa
    val ha = ea + sa
    val rf = 1 - cr
    val t = rf / (2 * sin(ea))
    return Vec3(
        t * (sin(ga) - sin(ha)),
        (h - t * cos(ga)) * cos(da) - (h - t * cos(ha)),
        (h - t * cos(ga)) * sin(da)
    ).norm
}

private fun snubComputeCR(ea: Double, da: Double): Double {
    var crL = 0.0
    var crR = 1.0
    while (true) {
        val cr = (crL + crR) / 2
        if (cr <= crL || cr >= crR) return cr // result precision is an ULP
        val sa = snubComputeSA(ea, da, cr)
        // error goes from positive to negative to NaN as cr goes from 0 to 1
        val rf = 1 - cr
        val err = snubComputeB(ea, da, cr, sa) - rf
        if (err <= 0)
            crL = cr else
            crR = cr
    }
}

data class SnubbingRatio(val cr: Double, val sa: Double) {
    override fun toString(): String = "(cr=${cr.fmt}, sa=${sa.fmt})"
}

fun Polyhedron.regularSnubbingRatio(edgeKind: EdgeKind? = null): SnubbingRatio {
    val (ea, da) = regularFaceGeometry(edgeKind)
    val cr = snubComputeCR(ea, da)
    val sa = snubComputeSA(ea, da, cr)
    return SnubbingRatio(cr, sa)
}

fun Polyhedron.snub(
    sr: SnubbingRatio = regularSnubbingRatio(),
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null
) = transformedPolyhedron(Transform.Snub, sr, scale, forceFaceKinds) {
    val (cr, sa) = sr
    val rr = dualReciprocationRadius
    // vertices from the face-vertices (directed edges)
    val fvv = fs.associateWith { f ->
        val c = f.dualPoint(rr) // for regular polygons -- face center
        val r = f.toRotationAroundQuat(-sa)
        f.directedEdges.associateBy({ it.a }, { e ->
            vertex(c + ((1 - cr) * (e.a - c)).rotated(r), VertexKind(directedEdgeKindsIndex[e.kind]!!))
        })
    }
    // faces from the original faces
    for (f in fs) {
        face(fvv[f]!!.values, f.kind)
    }
    // faces from the original vertices
    var kindOfs = faceKinds.size
    for (v in vs) {
        val fvs = v.directedEdges.map { fvv[it.r]!![v]!! }
        face(fvs, FaceKind(kindOfs + v.kind.id))
    }
    for (vk in vertexKinds.keys) faceKindSource(FaceKind(kindOfs + vk.id), vk)
    // 3-faces from the directed edges
    kindOfs += vertexKinds.size
    for (e in directedEdges) {
        val fvs = listOf(
            fvv[e.l]!![e.a]!!,
            fvv[e.l]!![e.b]!!,
            fvv[e.r]!![e.a]!!
        )
        face(fvs, FaceKind(kindOfs + directedEdgeKindsIndex[e.kind]!!))
    }
    for ((ek, id) in directedEdgeKindsIndex) faceKindSource(FaceKind(kindOfs + id), ek)
    mergeIndistinguishableKinds()
}

