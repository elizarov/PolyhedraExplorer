/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.util

import kotlin.math.*

private const val defaultPrecision = 4

fun Double.fmt(precision: Int): String {
    val p = 10.0.pow(precision)
    return ((this * p).roundToLong() / p).toString()
}

fun Float.fmt(precision: Int): String = toDouble().fmt(precision)

val Double.fmt: String
    get() = fmt(defaultPrecision)

fun Double.fmtFix(precision: Int): String {
    val p = 10.0.pow(precision)
    val m = (this * p).roundToLong().toString().padStart(precision + 1, '0')
    val i = m.length - precision
    return m.substring(0, i) + "." + m.substring(i)
}

val Double.fmtFix: String
    get() = fmtFix(defaultPrecision)

