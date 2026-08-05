/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.transform

import polyhedra.common.poly.*
import polyhedra.common.util.*

private const val POW2 = 7
private const val SHIFT = 32 - POW2
private const val HASH_CAPACITY = 1 shl POW2
private const val CACHE_SIZE_LIMIT = HASH_CAPACITY / 2
private const val HASH_MASK = (1 shl POW2) - 1
private const val MAGIC = 2654435769L.toInt() // golden ratio

@Suppress("RESULT_CLASS_IN_RETURN_TYPE")
object TransformCache {
    private val hash = arrayOfNulls<Entry?>(HASH_CAPACITY)
    private var hashSize = 0
    private var lruFirst: Entry? = null
    private var lruLast: Entry? = null

    private class Entry(
        var hashIndex: Int,
        val poly: Polyhedron,
        val key: Any,
        val param: Any?,
        var result: Result<Polyhedron>
    ) {
        var lruPrev: Entry? = null
        var lruNext: Entry? = null
    }

    private fun hashIndex0(poly: Polyhedron, key: Any, param: Any?): Int =
        (((poly.hashCode() * 31 + key.hashCode()) * 31 + param.hashCode()) * MAGIC) ushr SHIFT

    private fun pullUpLru(entry: Entry) {
        if (entry.lruPrev == null) return // already first
        removeLru(entry)
        addLruFirst(entry)
    }

    private fun removeLru(entry: Entry) {
        val prev = entry.lruPrev
        val next = entry.lruNext
        if (prev == null) lruFirst = next else prev.lruNext = next
        if (next == null) lruLast = prev else next.lruPrev = prev
    }

    private fun addLruFirst(entry: Entry) {
        val first = lruFirst
        lruFirst = entry
        entry.lruPrev = null
        entry.lruNext = first
        if (first == null) lruLast = entry else first.lruPrev = entry
    }

    private fun dropOldest() {
        val last = lruLast ?: return
        val prev = last.lruPrev
        lruLast = prev
        prev?.lruNext = null
        hashRemove(last)
    }

    private fun hashRemove(entry: Entry) {
        hash[entry.hashIndex] = null
        hashSize--
        var hole = entry.hashIndex
        var i = hole
        while (true) {
            if (i == 0) i = hash.size
            i--
            val e = hash[i] ?: return
            val j = hashIndex0(e.poly, e.key, e.param)
            val entryDist = (j - i) and HASH_MASK
            val holeDist = (hole - i) and HASH_MASK
            if (holeDist <= entryDist) {
                e.hashIndex = hole
                hash[hole] = e
                hash[i] = null
                hole = i
            }
        }
    }

    operator fun get(poly: Polyhedron, key: Any, param: Any? = null): Polyhedron? {
        var i = hashIndex0(poly, key, param)
        while(true) {
            val e = hash[i] ?: return null
            if (e.poly == poly && e.key == key && e.param == param) {
                pullUpLru(e)
                return e.result.getOrThrow()
            }
            if (i == 0) i = hash.size
            i--
        }
    }

    operator fun set(poly: Polyhedron, key: Any, param: Any? = null, result: Result<Polyhedron>) {
        var i = hashIndex0(poly, key, param)
        while(true) {
            val e = hash[i] ?: break
            if (e.poly == poly && e.key == key && e.param == param) {
                e.result = result
                pullUpLru(e)
                return
            }
            if (i == 0) i = hash.size
            i--
        }
        if (hashSize >= CACHE_SIZE_LIMIT) dropOldest()
        val e = Entry(i, poly, key, param, result)
        hash[i] = e
        hashSize++
        addLruFirst(e)
    }
}

fun Polyhedron.transformedPolyhedron(
    key: Any,
    param: Any? = null,
    scale: Scale? = null,
    forceFaceKinds: List<FaceKindSource>? = null,
    block: PolyhedronBuilder.() -> Unit
): Polyhedron {
    TransformCache[this, key, param]?.let { cached ->
        // update cached copy as needed
        return cached.scaled(scale).forceFaceKinds(forceFaceKinds)
    }
    // Optimization: store polyhedron with the requested scale in cache
    val cache = runCatching {
        polyhedron {
            block()
            scale(scale)
        }
    }
    TransformCache[this, key, param] = cache
    return cache.getOrThrow().forceFaceKinds(forceFaceKinds)
}

private object ForceFaceKindsKey

private fun Polyhedron.forceFaceKinds(forceFaceKinds: List<FaceKindSource>?): Polyhedron {
    if (forceFaceKinds == null) return this
    return transformedPolyhedron(ForceFaceKindsKey, forceFaceKinds) {
        vertices(vs)
        faces(fs)
        faceKindSources(faceKindSources)
        forceFaceKinds(forceFaceKinds)
    }
}
