/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.util

import kotlin.math.*

const val EPS = 1e-10

infix fun Double.approx(x: Double): Boolean = abs(this - x) < EPS

object DoubleApproxComparator : Comparator<Double> {
    override fun compare(a: Double, b: Double): Int {
        val d = a - b
        return when {
            abs(d) < EPS -> 0
            d < 0 -> -1
            else -> 1
        }
    }
}

fun sqr(x: Double): Double = x * x

fun frac(x: Double) = x - floor(x)

infix fun Double.mod(m: Double): Double {
    val x = this / m
    return frac(x) * m
}

fun det(
    m11: Double, m12: Double,
    m21: Double, m22: Double
): Double =
    m11 * m22 - m12 * m21

fun det(
    m11: Double, m12: Double, m13: Double,
    m21: Double, m22: Double, m23: Double,
    m31: Double, m32: Double, m33: Double
): Double =
    m11 * det(m22, m23, m32, m33) -
    m21 * det(m12, m13, m32, m33) +
    m31 * det(m12, m13, m22, m23)

fun Double.toDegrees() =
    this * 180 / PI