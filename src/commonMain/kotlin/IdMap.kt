/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common

interface Id { val id: Int }

inline fun <T, K : Id, V : Any> Iterable<T>.associateById(keyTransform: (T) -> K, valueTransform: (T) -> V): IdMap<K, V> {
    val result = ArrayIdMap<K, V>()
    for (e in this) {
        result[keyTransform(e)] = valueTransform(e)
    }
    return result
}

inline fun <T : Any, K : Id> Iterable<T>.associateById(keyTransform: (T) -> K): IdMap<K, T> =
    associateById(keyTransform, { it })

inline fun <T, K : Id, V> Iterable<T>.groupById(keyTransform: (T) -> K, valueTransform: (T) -> V): IdMap<K, List<V>> {
    val result = ArrayIdMap<K, ArrayList<V>>()
    for (e in this) {
        val k = keyTransform(e)
        val l = result.getOrPut(k) { ArrayList() }
        l.add(valueTransform(e))
    }
    return result
}

inline fun <T, K : Id> Iterable<T>.groupById(keyTransform: (T) -> K): IdMap<K, List<T>> =
    groupById(keyTransform, { it })

public inline fun <K : Id, V : Any, R : Any> IdMap<out K, V>.mapValues(transform: (Map.Entry<K, V>) -> R): IdMap<K, R> {
    val result = ArrayIdMap<K, R>()
    for (e in entries) {
        result[e.key] = transform(e)
    }
    return result
}

interface IdMap<K : Id, out V : Any> : Map<K, V>

@Suppress("UNCHECKED_CAST")
class ArrayIdMap<K : Id, V : Any>(capacity: Int = 8) : AbstractMutableMap<K, V>(), IdMap<K, V> {
    public override var size: Int = 0
        private set
    private var ks = arrayOfNulls<Any?>(capacity)
    private var vs = arrayOfNulls<Any?>(capacity)

    override val keys: MutableSet<K>
        get() = ViewSet { ks[it] as K }

    override val values: MutableCollection<V>
        get() = ViewCollection { vs[it] as V }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = ViewSet { Entry(it) }

    override fun put(key: K, value: V): V? {
        if (key.id >= ks.size) ensureCapacity(key.id + 1)
        if (ks[key.id] == null) size++
        val old = vs[key.id] as V?
        ks[key.id] = key
        vs[key.id] = value
        return old
    }

    override fun get(key: K): V? = vs.getOrNull(key.id) as V?

    fun ensureCapacity(capacity: Int) {
        val size = capacity.coerceAtLeast(ks.size * 2)
        ks = ks.copyOf(size)
        vs = vs.copyOf(size)
    }

    private inner class ViewIterator<T>(val view: (Int) -> T) : MutableIterator<T> {
        private var i = -1
        private fun moveToNext(): Int {
            if (i >= ks.size) return i
            val prev = i++
            while (i < ks.size && ks[i] == null) i++
            return prev
        }
        init {
            moveToNext()
        }

        override fun hasNext(): Boolean = i < ks.size
        override fun next(): T = view(moveToNext())
        override fun remove() = throw UnsupportedOperationException()
    }

    private inner class ViewCollection<T>(val view: (Int) -> T) : AbstractMutableCollection<T>() {
        override val size: Int
            get() = this@ArrayIdMap.size
        override fun iterator(): MutableIterator<T> = ViewIterator(view)
        override fun add(element: T): Boolean = throw UnsupportedOperationException()
    }

    private inner class ViewSet<T>(val view: (Int) -> T) : AbstractMutableSet<T>() {
        override val size: Int
            get() = this@ArrayIdMap.size
        override fun iterator(): MutableIterator<T> = ViewIterator(view)
        override fun add(element: T): Boolean = throw UnsupportedOperationException()
    }

    private inner class Entry(val index: Int) : MutableMap.MutableEntry<K, V> {
        override val key: K
            get() = ks[index] as K
        override val value: V
            get() = vs[index] as V
        override fun setValue(newValue: V): V =
            (vs[index] as V).also { vs[index] = newValue }
    }
}

