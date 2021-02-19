package polyhedra.common

import kotlin.math.*

private const val defaultPrecision = 4

fun Double.fmt(precision: Int): String {
    val p = 10.0.pow(precision)
    return ((this * p).roundToLong() / p).toString()
}

fun Float.fmt(precision: Int): String = toDouble().fmt(precision)

val Double.fmt: String
    get() = fmt(defaultPrecision)