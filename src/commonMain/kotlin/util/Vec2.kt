/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.util

import kotlin.math.*

interface Vec2 {
    val x: Double
    val y: Double
}

open class MutableVec2(
    override var x: Double = 0.0,
    override var y: Double = 0.0
) : Vec2 {
    constructor(v: Vec2) : this(v.x, v.y)
    override fun toString(): String = "[${x.fmt}, ${y.fmt}]"
}

fun norm(x: Double, y: Double): Double =
    sqrt(sqr(x) + sqr(y))

val Vec2.norm: Double
    get() = norm(x, y)


