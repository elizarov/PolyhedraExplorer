/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.common.transform

import kotlinx.serialization.*
import polyhedra.common.poly.*
import polyhedra.common.util.*
import kotlin.reflect.*

val Transforms: List<Transform>
    get() = Transform.Transforms

fun String.toTransformOrNull(): Transform? {
    Transforms.find { it.tag == this }?.let { return it }
    if (startsWith("$DROP_TAG[") && endsWith("]")) {
        val kind = substring(DROP_TAG.length + 1, length - 1).toAnyKindOrNull() ?: return null
        return Drop(kind)
    }
    return null
}

typealias AsyncTransform = suspend (poly: Polyhedron, progress: OperationProgressContext) -> Polyhedron

@Serializable
sealed class Transform : Tagged {
    abstract fun transform(poly: Polyhedron): Polyhedron
    open fun isApplicable(poly: Polyhedron): Boolean = true // todo: not defined usefully now
    open fun truncationRatio(poly: Polyhedron): Double? = null
    open fun cantellationRatio(poly: Polyhedron): Double? = null
    open fun bevellingRatio(poly: Polyhedron): BevellingRatio? {
        val cr = cantellationRatio(poly)
        val tr = truncationRatio(poly)
        return if (cr == null && tr == null) null else BevellingRatio(cr ?: 0.0, tr ?: 0.0)
    }
    open fun snubbingRatio(poly: Polyhedron): SnubbingRatio? =
        cantellationRatio(poly)?.let { cr -> SnubbingRatio(cr, 0.0) }
    open fun chamferingRatio(poly: Polyhedron): Double? = null
    open fun isIdentityTransform(poly: Polyhedron): Boolean = false

    @Transient
    open val fev: TransformFEV? = null

    @Transient
    open val asyncTransform: AsyncTransform? = null

    companion object {
        @Suppress("ObjectPropertyName")
        private val _transforms = ArrayList<Transform>()

        val Transforms: List<Transform>
            get() = _transforms

        val None: Transform by None()
        val Truncated: Transform by Truncated()
        val Rectified: Transform by Rectified()
        val Cantellated: Transform by Cantellated()
        val Dual: Transform by Dual()
        val Bevelled: Transform by Bevelled()
        val Snub: Transform by Snub()
        val Chamfered: Transform by Chamfered()
        val Canonical: Transform by Canonical()

        private operator fun Transform.provideDelegate(thisRef: Any?, prop: KProperty<*>): Transform {
            _transforms += this
            return this
        }

        private operator fun Transform.getValue(thisRef: Any?, prop: KProperty<*>): Transform = this
    }

    override fun toString(): String = this::class.simpleName!!
}

@Serializable
class None : Transform() {
    @Transient
    override val tag: String = "n"
    override fun transform(poly: Polyhedron): Polyhedron = poly
    override fun truncationRatio(poly: Polyhedron) = 0.0
    override fun cantellationRatio(poly: Polyhedron) = 0.0
    override fun chamferingRatio(poly: Polyhedron) = 0.0
    override fun isIdentityTransform(poly: Polyhedron) = true
    @Transient
    override val fev = TransformFEV.ID
}

@Serializable
class Truncated : Transform() {
    @Transient
    override val tag: String = "t"
    override fun transform(poly: Polyhedron): Polyhedron = poly.truncated()
    override fun truncationRatio(poly: Polyhedron) = poly.regularTruncationRatio()
    @Transient
    override val fev = TransformFEV(
        1, 0, 1,
        0, 3, 0,
        0, 2, 0
    )
}

@Serializable
class Rectified : Transform() {
    @Transient
    override val tag: String = "a"
    override fun transform(poly: Polyhedron): Polyhedron = poly.rectified()
    override fun truncationRatio(poly: Polyhedron) = 1.0
    @Transient
    override val fev = TransformFEV(
        1, 0, 1,
        0, 2, 0,
        0, 1, 0
    )
}

@Serializable
class Cantellated : Transform() { // ~= Rectified, Rectified
    @Transient
    override val tag: String = "e"
    override fun transform(poly: Polyhedron): Polyhedron = poly.cantellated()
    override fun cantellationRatio(poly: Polyhedron) = poly.regularCantellationRatio()
    @Transient
    override val fev = TransformFEV(
        1, 1, 1,
        0, 4, 0,
        0, 2, 0
    )
}

@Serializable
class Dual : Transform() {
    @Transient
    override val tag: String = "d"
    override fun transform(poly: Polyhedron): Polyhedron = poly.dual()
    override fun cantellationRatio(poly: Polyhedron) = 1.0
    @Transient
    override val fev = TransformFEV(
        0, 0, 1,
        0, 1, 0,
        1, 0, 0
    )
}

@Serializable
class Bevelled : Transform() { // ~= Rectified, Truncated
    @Transient
    override val tag: String = "b"
    override fun transform(poly: Polyhedron): Polyhedron = poly.bevelled()
    override fun bevellingRatio(poly: Polyhedron) = poly.regularBevellingRatio()
    @Transient
    override val fev = TransformFEV(
        1, 1, 1,
        0, 6, 0,
        0, 4, 0
    )
}

@Serializable
class Snub : Transform() {
    @Transient
    override val tag: String = "s"
    override fun transform(poly: Polyhedron): Polyhedron = poly.snub()
    override fun snubbingRatio(poly: Polyhedron) = poly.regularSnubbingRatio()
    @Transient
    override val fev = TransformFEV(
        1, 2, 1,
        0, 5, 0,
        0, 2, 0
    )
}

@Serializable
class Chamfered : Transform() {
    @Transient
    override val tag: String = "c"
    override fun transform(poly: Polyhedron): Polyhedron = poly.chamfered()
    override fun chamferingRatio(poly: Polyhedron) = poly.chamferingRatio()
    @Transient
    override val fev = TransformFEV(
        1, 2, 0,
        0, 4, 0,
        0, 1, 1
    )
}

@Serializable
class Canonical : Transform() {
    @Transient
    override val tag: String = "o"
    override fun transform(poly: Polyhedron): Polyhedron = poly.canonical()
    override fun isIdentityTransform(poly: Polyhedron): Boolean = poly.isCanonical()
    @Transient
    override val asyncTransform: AsyncTransform = Polyhedron::canonical
    @Transient
    override val fev = TransformFEV.ID
}

fun Polyhedron.transformed(transform: Transform) = transform.transform(this)

fun Polyhedron.transformed(transforms: List<Transform>) =
    transforms.fold(this) { poly, transform -> poly.transformed(transform) }

fun Polyhedron.transformed(vararg transforms: Transform) =
    transformed(transforms.toList())

