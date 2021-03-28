/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.util

fun <K : Comparable<K>, V> TreeMap(): TreeMap<K, V> =
    TreeMap(naturalOrder<K>())

class TreeMap<K, V>(
    val comparator: Comparator<K>
) : AbstractMutableMap<K, V>() {
    private var root: Node<K, V>? = null

    override var size: Int = 0
        private set

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = NodeSet { it }
    override val keys: MutableSet<K>
        get() = NodeSet { it.key }

    override fun get(key: K): V? {
        var cur = root ?: return null
        while (true) {
            val dir = comparator.compare(key, cur.key)
            if (dir == 0) return cur.value
            cur = cur[dir] ?: return null
        }
    }

    override fun put(key: K, value: V): V? {
        var cur = root ?: run {
            root = Node(key, value)
            size++
            return null
        }
        var new: Node<K, V>
        while (true) {
            val dir = comparator.compare(key, cur.key)
            if (dir == 0) return cur.value.also { cur.value = value }
            val down = cur[dir]
            if (down == null) {
                new = Node(key, value)
                cur[dir] = new
                size++
                break
            }
            cur = down
        }
        // new red node was added as cur child, fix it up
        while (true) {
            // if cur is black the nothing to do
            if (cur.black) return null 
            /*
               INVARIANT:
                [cur.red]
                    |
                [new.red]
            */
            val up = cur.up ?: run {
                cur.black = true // cur is root - just flip its color to black to fix
                return null
            }
            check(up.black) // must have been black
            val dir = cur.upDir
            val uncle = up[-dir]
            /*
                          [up.black]
                         / dir   \ -dir
                   [cur.red]     [uncle.???]
                      |
                   [new.red]
            */
            if (uncle?.black != true) {
                // uncle is red or missing
                cur.black = true
                uncle?.black = true
                up.black = false
                /*
                          [up.red]
                         / dir    \ -dir
                    [cur.black]   [uncle.black]
                        |
                    [new.red]
                */
                new = up
                cur = new.up ?: return null
                continue
            }
            // uncle is black -> rotate
            // make sure that new is on the left of cur first
            if (cur[dir] !== new) {
                check(cur[-dir] === new)
                /* ---- ROTATE LEFT around cur to flip cur & new ----
                   ORIGINAL ORDER [x, cur, y, new, z]
                              [up.black]
                             / dir
                        [cur.red]
                       / dir    \ -dir
                    [x]         [new.red]
                                / dir   \ -dir
                               [y]      [z]

                   DESIRED:
                              [up.black]
                             // (1) dir
                        [new.red]
                       // (2) dir    \ -dir
                    [cur.red]        [z]
                    /     \\ (3) -dir
                  [x]     [y]
                */
                val y = new[dir]
                up[dir] = new  // (1)
                new[dir] = cur // (2)
                cur[-dir] = y // (3)
                cur = new.also { new = cur }
            }
            /* ---- ROTATE RIGHT around up to fix colors ----
               ORIGINAL ORDER [new, cur, x, up, uncle]

                            [up2]
                              |
                          [up.black]
                         / dir    \ -dir
                    [cur.red]     [uncle.black]
                   / dir    \ -dir
               [new.red]    [x.black]

               DESIRED:
                            [up2]
                             || (1)
                        [cur.black]
                       / dir      \\ (2) -dir
                  [new.red]        [up.red]
                                // (3) dir   \ -dir
                            [x.black]         [uncle.black]
            */
            val x = cur[-dir]
            val up2 = up.up
            if (up2 != null) {
                up2[up.upDir] = cur // (1)
            } else { // (1)
                cur.up = null
                root = cur
            }
            cur[-dir] = up // (2)
            up[dir] = x // (3)
            cur.black = true
            up.black = false
            return null // done
        }
    }

    private fun firstNode(): Node<K, V>? = root?.let { firstNode(it) }

    private fun firstNode(root: Node<K, V>): Node<K, V> {
        var cur = root
        while (true) {
            cur = cur.left ?: return cur
        }
    }

    private fun nextNode(node: Node<K, V>): Node<K, V>? {
        var cur = node
        val right = cur.right
        if (right != null) return firstNode(right)
        while (true) {
            val up = cur.up ?: return null
            if (cur.upDir != 1) return up
            cur = up
        }
    }

    private class Node<K, V>(
        override val key: K,
        override var value: V,
    ) : MutableMap.MutableEntry<K, V> {
        var left: Node<K, V>? = null
        var right: Node<K, V>? = null
        var black = false // new nodes are red
        var up: Node<K, V>? = null

        operator fun get(dir: Int) = if (dir > 0) right else left

        operator fun set(dir: Int, node: Node<K, V>?) {
            if (dir > 0) right = node else left = node
            if (node != null) node.up = this
        }

        val upDir: Int
            get() {
                val up = up
                check(up != null)
                if (up.right === this) return 1
                if (up.left === this) return -1
                error("Cannot happen")
            }

        override fun setValue(newValue: V): V = value.also { value = newValue }
    }

    private inner class NodeSet<R>(val result: (Node<K, V>) -> R) : AbstractMutableSet<R>() {
        override val size: Int
            get() = this@TreeMap.size

        override fun add(element: R): Boolean {
            TODO("not implemented")
        }

        override fun iterator(): MutableIterator<R> =
            NodeIterator(result)
    }

    private inner class NodeIterator<R>(val result: (Node<K, V>) -> R) : MutableIterator<R> {
        private var nextNode = firstNode()

        override fun hasNext(): Boolean =
            nextNode != null

        override fun next(): R {
            val node = nextNode ?: throw NoSuchElementException()
            nextNode = nextNode(node)
            return result(node)
        }

        override fun remove() {
            TODO("not implemented")
        }
    }
}