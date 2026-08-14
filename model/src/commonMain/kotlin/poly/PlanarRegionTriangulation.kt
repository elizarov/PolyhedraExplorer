/*
 * Linked-list triangulation is adapted from Mapbox Earcut.
 * Copyright (c) 2016 Vladimir Agafonkin. Used under the ISC License:
 * Permission to use, copy, modify, and/or distribute this software for any purpose with or without
 * fee is hereby granted, provided that the above copyright notice and this permission notice appear
 * in all copies. THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES.
 */
package polyhedra.model.poly

import polyhedra.model.util.Vec3
import kotlin.math.abs

/** A tessellation of one planar outer cycle with zero or more inner holes. */
data class PlanarRegionTriangulation(
    val vertices: List<Vec3>,
    val triangles: List<FaceTriangle>,
)

/**
 * Triangulates a planar polygonal region without filling its holes.
 *
 * This is a compact Kotlin adaptation of Mapbox Earcut's linked-list algorithm. Keeping it in the
 * model makes rendering and printable-shell construction share the same topology instead of
 * representing a hole as overlapping, oppositely oriented disks.
 */
fun triangulatePlanarRegion(
    outer: List<Vec3>,
    holes: List<List<Vec3>>,
    normal: Vec3,
): PlanarRegionTriangulation {
    require(outer.size >= 3) { "A planar region needs an outer cycle" }
    require(holes.all { hole -> hole.size >= 3 }) { "A planar-region hole needs three vertices" }
    val vertices = outer + holes.flatten()
    val projected = vertices.map { point -> point.projectForRegion(normal) }
    val holeStarts = buildList {
        var offset = outer.size
        for (hole in holes) {
            add(offset)
            offset += hole.size
        }
    }
    val indices = EarClipRegion(projected, holeStarts).triangulate()
    require(indices.size % 3 == 0) { "Planar-region triangulation is incomplete" }
    return PlanarRegionTriangulation(
        vertices,
        indices.chunked(3).map { (a, b, c) -> FaceTriangle(a, b, c) },
    )
}

private data class RegionPoint(val x: Double, val y: Double)

private fun Vec3.projectForRegion(normal: Vec3): RegionPoint {
    val ax = abs(normal.x)
    val ay = abs(normal.y)
    val az = abs(normal.z)
    return when {
        ax >= ay && ax >= az -> RegionPoint(y, z)
        ay >= az -> RegionPoint(x, z)
        else -> RegionPoint(x, y)
    }
}

private class EarClipRegion(
    private val points: List<RegionPoint>,
    private val holeStarts: List<Int>,
) {
    private class Node(val index: Int, val x: Double, val y: Double) {
        lateinit var previous: Node
        lateinit var next: Node
    }

    fun triangulate(): List<Int> {
        val outerEnd = holeStarts.firstOrNull() ?: points.size
        var outer = linkedList(0, outerEnd, clockwise = true) ?: return emptyList()
        if (outer.next === outer.previous) return emptyList()
        if (holeStarts.isNotEmpty()) outer = eliminateHoles(outer)
        val triangles = arrayListOf<Int>()
        earcutLinked(outer, triangles, 0)
        return triangles
    }

    private fun linkedList(start: Int, end: Int, clockwise: Boolean): Node? {
        var last: Node? = null
        if (clockwise == (signedArea(start, end) > 0.0)) {
            for (index in start until end) last = insert(index, last)
        } else {
            for (index in end - 1 downTo start) last = insert(index, last)
        }
        if (last != null && equals(last, last.next)) {
            remove(last)
            last = last.next
        }
        return last
    }

    private fun insert(index: Int, last: Node?): Node {
        val point = points[index]
        val node = Node(index, point.x, point.y)
        if (last == null) {
            node.previous = node
            node.next = node
        } else {
            node.next = last.next
            node.previous = last
            last.next.previous = node
            last.next = node
        }
        return node
    }

    private fun remove(node: Node) {
        node.next.previous = node.previous
        node.previous.next = node.next
    }

    private fun filterPoints(start: Node, stop: Node = start): Node {
        var end = stop
        var point = start
        var again: Boolean
        do {
            again = false
            if (point !== point.next &&
                (equals(point, point.next) || area(point.previous, point, point.next) == 0.0)
            ) {
                if (point === end) end = point.previous
                remove(point)
                point = point.previous
                again = true
            } else {
                point = point.next
            }
        } while (again || point !== end)
        return end
    }

    private fun earcutLinked(start: Node, triangles: MutableList<Int>, pass: Int) {
        var ear = start
        var stop = start
        while (ear.previous !== ear.next) {
            val previous = ear.previous
            val next = ear.next
            if (area(previous, ear, next) < 0.0 && isEar(ear)) {
                triangles += previous.index
                triangles += ear.index
                triangles += next.index
                remove(ear)
                ear = next.next
                stop = next.next
                continue
            }
            ear = next
            if (ear === stop) {
                when (pass) {
                    0 -> earcutLinked(filterPoints(ear), triangles, 1)
                    1 -> earcutLinked(cureLocalIntersections(filterPoints(ear), triangles), triangles, 2)
                    else -> splitEarcut(ear, triangles)
                }
                return
            }
        }
    }

    private fun isEar(ear: Node): Boolean {
        val a = ear.previous
        val b = ear
        val c = ear.next
        val minX = minOf(a.x, b.x, c.x)
        val minY = minOf(a.y, b.y, c.y)
        val maxX = maxOf(a.x, b.x, c.x)
        val maxY = maxOf(a.y, b.y, c.y)
        var point = c.next
        while (point !== a) {
            if (point.x in minX..maxX && point.y in minY..maxY &&
                !(point.x == a.x && point.y == a.y) &&
                pointInTriangle(a, b, c, point) &&
                area(point.previous, point, point.next) >= 0.0
            ) return false
            point = point.next
        }
        return true
    }

    private fun cureLocalIntersections(start: Node, triangles: MutableList<Int>): Node {
        var first = start
        var point = start
        do {
            val a = point.previous
            val b = point.next.next
            if (intersects(a, point, point.next, b, includeBoundary = false) &&
                locallyInside(a, b) && locallyInside(b, a)
            ) {
                triangles += a.index
                triangles += point.index
                triangles += b.index
                remove(point)
                remove(point.next)
                point = b
                first = b
            }
            point = point.next
        } while (point !== first)
        return filterPoints(point)
    }

    private fun splitEarcut(start: Node, triangles: MutableList<Int>) {
        var a = start
        do {
            var b = a.next.next
            while (b !== a.previous) {
                if (a.index != b.index && isValidDiagonal(a, b)) {
                    var other = splitPolygon(a, b)
                    a = filterPoints(a, a.next)
                    other = filterPoints(other, other.next)
                    earcutLinked(a, triangles, 0)
                    earcutLinked(other, triangles, 0)
                    return
                }
                b = b.next
            }
            a = a.next
        } while (a !== start)
        error("Planar region cannot be triangulated")
    }

    private fun eliminateHoles(outerStart: Node): Node {
        val holes = holeStarts.mapIndexedNotNull { holeIndex, start ->
            val end = holeStarts.getOrNull(holeIndex + 1) ?: points.size
            linkedList(start, end, clockwise = false)?.let(::leftmost)
        }.sortedWith(compareBy<Node>({ it.x }, { it.y }))
        var outer = outerStart
        for (hole in holes) {
            val bridge = findHoleBridge(hole, outer) ?: continue
            val reverse = splitPolygon(bridge, hole)
            filterPoints(reverse, reverse.next)
            outer = filterPoints(bridge, bridge.next)
        }
        return outer
    }

    private fun findHoleBridge(hole: Node, outer: Node): Node? {
        var point = outer
        val hx = hole.x
        val hy = hole.y
        var qx = Double.NEGATIVE_INFINITY
        var bridge: Node? = null
        do {
            if (hy <= point.y && hy >= point.next.y && point.next.y != point.y) {
                val x = point.x + (hy - point.y) * (point.next.x - point.x) /
                    (point.next.y - point.y)
                if (x <= hx && x > qx) {
                    qx = x
                    bridge = if (point.x < point.next.x) point else point.next
                    if (x == hx) return bridge
                }
            }
            point = point.next
        } while (point !== outer)
        var candidate = bridge ?: return null
        val mx = candidate.x
        val my = candidate.y
        var tangent = Double.POSITIVE_INFINITY
        point = candidate.next
        while (point !== candidate) {
            if (hx >= point.x && point.x >= mx && hx != point.x &&
                pointInTriangle(
                    if (hy < my) hx else qx,
                    hy,
                    mx,
                    my,
                    if (hy < my) qx else hx,
                    hy,
                    point.x,
                    point.y,
                )
            ) {
                val current = abs(hy - point.y) / (hx - point.x)
                if (locallyInside(point, hole) &&
                    (current < tangent ||
                        current == tangent &&
                        (point.x > candidate.x ||
                            point.x == candidate.x && sectorContainsSector(candidate, point)))
                ) {
                    candidate = point
                    tangent = current
                }
            }
            point = point.next
        }
        return candidate
    }

    private fun splitPolygon(a: Node, b: Node): Node {
        val a2 = Node(a.index, a.x, a.y)
        val b2 = Node(b.index, b.x, b.y)
        val an = a.next
        val bp = b.previous
        a.next = b
        b.previous = a
        a2.next = an
        an.previous = a2
        b2.next = a2
        a2.previous = b2
        bp.next = b2
        b2.previous = bp
        return b2
    }

    private fun isValidDiagonal(a: Node, b: Node): Boolean {
        val zeroLength = equals(a, b) &&
            area(a.previous, a, a.next) > 0.0 && area(b.previous, b, b.next) > 0.0
        return a.next.index != b.index &&
            (zeroLength || locallyInside(a, b) && locallyInside(b, a) &&
                (area(a.previous, a, b.previous) != 0.0 || area(a, b.previous, b) != 0.0)) &&
            !intersectsPolygon(a, b) && (zeroLength || middleInside(a, b))
    }

    private fun intersectsPolygon(a: Node, b: Node): Boolean {
        var point = a
        do {
            if (point.index != a.index && point.next.index != a.index &&
                point.index != b.index && point.next.index != b.index &&
                intersects(point, point.next, a, b)
            ) return true
            point = point.next
        } while (point !== a)
        return false
    }

    private fun locallyInside(a: Node, b: Node): Boolean =
        if (area(a.previous, a, a.next) < 0.0) {
            area(a, b, a.next) >= 0.0 && area(a, a.previous, b) >= 0.0
        } else {
            area(a, b, a.previous) < 0.0 || area(a, a.next, b) < 0.0
        }

    private fun middleInside(a: Node, b: Node): Boolean {
        var point = a
        var inside = false
        val x = (a.x + b.x) / 2.0
        val y = (a.y + b.y) / 2.0
        do {
            val next = point.next
            if ((point.y > y) != (next.y > y) &&
                x < (next.x - point.x) * (y - point.y) / (next.y - point.y) + point.x
            ) inside = !inside
            point = next
        } while (point !== a)
        return inside
    }

    private fun leftmost(start: Node): Node {
        var point = start
        var result = start
        do {
            if (point.x < result.x || point.x == result.x && point.y < result.y) result = point
            point = point.next
        } while (point !== start)
        return result
    }

    private fun signedArea(start: Int, end: Int): Double {
        var sum = 0.0
        var previous = end - 1
        for (index in start until end) {
            sum += (points[previous].x - points[index].x) *
                (points[index].y + points[previous].y)
            previous = index
        }
        return sum
    }

    private fun sectorContainsSector(m: Node, p: Node): Boolean =
        area(m.previous, m, p.previous) < 0.0 && area(p.next, m, m.next) < 0.0

    private fun area(p: Node, q: Node, r: Node): Double =
        (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)

    private fun equals(a: Node, b: Node): Boolean = a.x == b.x && a.y == b.y

    private fun intersects(
        p1: Node,
        q1: Node,
        p2: Node,
        q2: Node,
        includeBoundary: Boolean = true,
    ): Boolean {
        val o1 = area(p1, q1, p2)
        val o2 = area(p1, q1, q2)
        val o3 = area(p2, q2, p1)
        val o4 = area(p2, q2, q1)
        if (((o1 > 0.0 && o2 < 0.0) || (o1 < 0.0 && o2 > 0.0)) &&
            ((o3 > 0.0 && o4 < 0.0) || (o3 < 0.0 && o4 > 0.0))
        ) return true
        if (!includeBoundary) return false
        return o1 == 0.0 && onSegment(p1, p2, q1) ||
            o2 == 0.0 && onSegment(p1, q2, q1) ||
            o3 == 0.0 && onSegment(p2, p1, q2) ||
            o4 == 0.0 && onSegment(p2, q1, q2)
    }

    private fun onSegment(a: Node, point: Node, b: Node): Boolean =
        point.x in minOf(a.x, b.x)..maxOf(a.x, b.x) &&
            point.y in minOf(a.y, b.y)..maxOf(a.y, b.y)

    private fun pointInTriangle(a: Node, b: Node, c: Node, p: Node): Boolean =
        pointInTriangle(a.x, a.y, b.x, b.y, c.x, c.y, p.x, p.y)

    private fun pointInTriangle(
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
        cx: Double,
        cy: Double,
        px: Double,
        py: Double,
    ): Boolean =
        (cx - px) * (ay - py) >= (ax - px) * (cy - py) &&
            (ax - px) * (by - py) >= (bx - px) * (ay - py) &&
            (bx - px) * (cy - py) >= (cx - px) * (by - py)
}
