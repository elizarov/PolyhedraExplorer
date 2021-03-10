package polyhedra.common.transform

import polyhedra.common.*

private const val POW2 = 7
private const val SHIFT = 32 - POW2
private const val HASH_CAPACITY = 1 shl POW2
private const val HASH_MASK = (1 shl POW2) - 1
private const val LRU_CAPACITY = 1 shl (POW2 - 1) // 64
private const val MAGIC = 2654435769L.toInt() // golden ratio

@Suppress("RESULT_CLASS_IN_RETURN_TYPE")
object TransformCache {
    // first and last elements in the array are sentinels
    private val lru = arrayOfNulls<Entry>(LRU_CAPACITY + 2)
    private val hash = IntArray(HASH_CAPACITY)
    private var lruSize = 0 // numbered from 1 (sic!)

    private class Entry(
        var lruIndex: Int,
        var hashIndex: Int,
        val poly: Polyhedron,
        val key: Any,
        val param: Any?,
        var result: Result<Polyhedron>
    )

    private fun hashIndex0(poly: Polyhedron, key: Any, param: Any?): Int =
        (((poly.hashCode() * 31 + key.hashCode()) * 31 + param.hashCode()) * MAGIC) ushr SHIFT

    private fun putAtLruIndex(entry: Entry, lruIndex: Int) {
        lru[lruIndex] = entry
        entry.lruIndex = lruIndex
        hash[entry.hashIndex] = lruIndex
    }

    private fun pullUpLru(entry: Entry) {
        val lruIndex = entry.lruIndex
        val next = lru[lruIndex + 1] ?: return // already last
        putAtLruIndex(entry, lruIndex + 1)
        putAtLruIndex(next, lruIndex)
    }

    private fun dropOldest(k: Int) {
        // remove from LRU
        for (i in 1..k) {
            hashRemove(lru[i]!!.hashIndex)
        }
        for (i in k + 1..lruSize) {
            putAtLruIndex(lru[i]!!, i - k)
        }
        for (i in lruSize - k + 1..lruSize) {
            lru[i] = null
        }
        lruSize -= k
    }

    private fun hashRemove(removeAt: Int) {
        hash[removeAt] = 0
        var hole = removeAt
        var i = hole
        while (true) {
            if (i == 0) i = hash.size
            i--
            val e = lru[hash[i]] ?: return
            val j = hashIndex0(e.poly, e.key, e.param)
            val entryDist = (j - i) and HASH_MASK
            val holeDist = (hole - i) and HASH_MASK
            if (holeDist <= entryDist) {
                e.hashIndex = hole
                hash[hole] = e.lruIndex
                hash[i] = 0
                hole = i
            }
        }
    }

    operator fun get(poly: Polyhedron, key: Any, param: Any? = null): Polyhedron? {
        var i = hashIndex0(poly, key, param)
        while(true) {
            val e = lru[hash[i]] ?: return null
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
            val e = lru[hash[i]] ?: break
            if (e.poly == poly && e.key == key && e.param == param) {
                e.result = result
                pullUpLru(e)
                return
            }
            if (i == 0) i = hash.size
            i--
        }
        if (lruSize >= LRU_CAPACITY) dropOldest(lruSize / 4) // drop a quarter
        lruSize++
        val e = Entry(lruSize, i, poly, key, param, result)
        lru[lruSize] = e
        hash[i] = lruSize
    }
}

fun Polyhedron.transformedPolyhedron(key: Any, param: Any? = null, block: PolyhedronBuilder.() -> Unit): Polyhedron {
    TransformCache[this, key, param]?.let { return it }
    val result = runCatching { polyhedron(block) }
    TransformCache[this, key, param] = result
    return result.getOrThrow()
}
