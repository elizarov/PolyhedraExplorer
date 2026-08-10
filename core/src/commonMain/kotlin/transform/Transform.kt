/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.core.transform

import kotlinx.serialization.*
import polyhedra.core.util.OperationProgressContext
import polyhedra.model.api.*
import polyhedra.model.poly.*
import polyhedra.model.util.*
import kotlin.reflect.*

val Transforms: List<Transform>
    get() = Transform.Transforms

fun String.toTransformOrNull(): Transform? = parseTransformTag()?.toTransformOrNull()

internal fun TransformSpec.toTransformOrNull(): Transform? {
    Transforms.find { it.id == id }?.let { transform ->
        if (!transform.supportsTweaks(tweaks.keys)) return null
        return transform.withTweaks(tweaks)
    }
    return toOrbitTargetedTransformOrNull()
}

typealias AsyncTransform = suspend (poly: Polyhedron, progress: OperationProgressContext) -> Polyhedron

@Serializable
sealed class Transform : Tagged {
    abstract val id: TransformId
    open val tweaks: Map<TransformTweak, Double> get() = emptyMap()

    override val tag: String get() = TransformSpec(id, tweaks).tag

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
        private val registeredTransforms = mutableListOf<Transform>()

        val None: Transform by None()
        val Truncated: Transform by Truncated()
        val Rectified: Transform by Rectified()
        val Cantellated: Transform by Cantellated()
        val Dual: Transform by Dual()
        val Bevelled: Transform by Bevelled()
        val Snub: Transform by Snub()
        val SnubFlipped: Transform by Snub(Chirality.Flipped)
        val Propeller: Transform by Propeller()
        val PropellerFlipped: Transform by Propeller(Chirality.Flipped)
        val Whirl: Transform by Whirl()
        val WhirlFlipped: Transform by Whirl(Chirality.Flipped)
        val Quinto: Transform by Quinto()
        val Chamfered: Transform by Chamfered()
        val Canonical: Transform by Canonical()

        val Transforms: List<Transform> = registeredTransforms.toList()

        private operator fun Transform.provideDelegate(thisRef: Any?, prop: KProperty<*>): Transform {
            registeredTransforms += this
            return this
        }

        private operator fun Transform.getValue(thisRef: Any?, prop: KProperty<*>): Transform = this
    }

    override fun toString(): String = this::class.simpleName!!
}

@Serializable
class None : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.None)
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
    override val id = TransformId(TransformOperation.Truncated)
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
    override val id = TransformId(TransformOperation.Rectified)
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
    override val id = TransformId(TransformOperation.Cantellated)
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
    override val id = TransformId(TransformOperation.Dual)
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
    override val id = TransformId(TransformOperation.Bevelled)
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
class Snub(
    @Transient val chirality: Chirality = Chirality.Default,
) : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Snub, chirality)
    override fun transform(poly: Polyhedron): Polyhedron = poly.snub(requireNotNull(snubbingRatio(poly)))
    override fun snubbingRatio(poly: Polyhedron) = poly.regularSnubbingRatio().let { ratio ->
        if (chirality == Chirality.Flipped) ratio.copy(sa = -ratio.sa) else ratio
    }
    @Transient
    override val fev = TransformFEV(
        1, 2, 1,
        0, 5, 0,
        0, 2, 0
    )

    override fun toString(): String = "Snub${chirality.suffix}"
}

internal fun Transform.withTweaks(tweaks: Map<TransformTweak, Double>): Transform =
    if (tweaks.isEmpty()) this else TweakedTransform(this, tweaks)

private fun Transform.supportsTweaks(tweaks: Set<TransformTweak>): Boolean {
    val supported = when (this) {
        is Truncated -> setOf(TransformTweak.Depth)
        is Cantellated -> setOf(TransformTweak.Distance)
        is Bevelled -> setOf(TransformTweak.Distance, TransformTweak.Depth)
        is Snub -> setOf(TransformTweak.Inset, TransformTweak.Twist)
        is Chamfered -> setOf(TransformTweak.Width)
        else -> emptySet()
    }
    return tweaks.all { it in supported }
}

private data class TweakedTransform(
    val base: Transform,
    override val tweaks: Map<TransformTweak, Double>,
) : Transform() {
    override val id: TransformId
        get() = base.id

    override val fev: TransformFEV?
        get() = base.fev

    override fun isApplicable(poly: Polyhedron): Boolean = base.isApplicable(poly)

    override fun transform(poly: Polyhedron): Polyhedron = when (base) {
        is Truncated -> poly.truncated(requireNotNull(truncationRatio(poly)))
        is Cantellated -> poly.cantellated(requireNotNull(cantellationRatio(poly)))
        is Bevelled -> poly.bevelled(requireNotNull(bevellingRatio(poly)))
        is Snub -> poly.snub(requireNotNull(snubbingRatio(poly)))
        is Chamfered -> poly.chamfered(requireNotNull(chamferingRatio(poly)))
        else -> error("Transform ${base.tag} has no continuous parameters")
    }

    override fun truncationRatio(poly: Polyhedron): Double? = when (base) {
        is Truncated -> poly.regularTruncationRatio() * factor(TransformTweak.Depth)
        else -> null
    }

    override fun cantellationRatio(poly: Polyhedron): Double? =
        if (base is Cantellated) poly.regularCantellationRatio() * factor(TransformTweak.Distance) else null

    override fun bevellingRatio(poly: Polyhedron): BevellingRatio? =
        if (base is Bevelled) poly.regularBevellingRatio().let { regular ->
            BevellingRatio(
                regular.cr * factor(TransformTweak.Distance),
                regular.tr * factor(TransformTweak.Depth),
            )
        } else null

    override fun snubbingRatio(poly: Polyhedron): SnubbingRatio? =
        if (base is Snub) base.snubbingRatio(poly).let { regular ->
            SnubbingRatio(
                regular.cr * factor(TransformTweak.Inset),
                regular.sa * factor(TransformTweak.Twist),
            )
        } else null

    override fun chamferingRatio(poly: Polyhedron): Double? =
        if (base is Chamfered) poly.chamferingRatio() * factor(TransformTweak.Width) else null

    private fun factor(tweak: TransformTweak): Double = tweaks[tweak] ?: 1.0

    override fun toString(): String = base.toString()
}

@Serializable
class Propeller(
    @Transient val chirality: Chirality = Chirality.Default,
) : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Propeller, chirality)
    override fun transform(poly: Polyhedron): Polyhedron = poly.propeller(chirality)
    @Transient
    override val asyncTransform: AsyncTransform = { poly, progress -> poly.propeller(chirality, progress) }
    @Transient
    override val fev = TransformFEV(
        1, 2, 0,
        0, 5, 0,
        0, 2, 1,
    )

    override fun toString(): String = "Propeller${chirality.suffix}"
}

@Serializable
class Whirl(
    @Transient val chirality: Chirality = Chirality.Default,
) : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Whirl, chirality)
    override fun transform(poly: Polyhedron): Polyhedron = poly.whirl(chirality)
    @Transient
    override val asyncTransform: AsyncTransform = { poly, progress -> poly.whirl(chirality, progress) }
    @Transient
    override val fev = TransformFEV(
        1, 2, 0,
        0, 7, 0,
        0, 4, 1,
    )

    override fun toString(): String = "Whirl${chirality.suffix}"
}

@Serializable
class Quinto : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Quinto)
    override fun transform(poly: Polyhedron): Polyhedron = poly.quinto()
    @Transient
    override val asyncTransform: AsyncTransform = { poly, progress -> poly.quinto(progress) }
    @Transient
    override val fev = TransformFEV(
        1, 2, 0,
        0, 6, 0,
        0, 3, 1,
    )
}

@Serializable
class Chamfered : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Chamfered)
    override fun transform(poly: Polyhedron): Polyhedron = poly.chamfered()
    override fun chamferingRatio(poly: Polyhedron) = poly.chamferingRatio()
    @Transient
    override val fev = TransformFEV(
        1, 1, 0,
        0, 4, 0,
        0, 2, 1
    )
}

@Serializable
class Canonical : Transform() {
    @Transient
    override val id = TransformId(TransformOperation.Canonical)
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

