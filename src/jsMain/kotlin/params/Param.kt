/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.params

import polyhedra.common.util.*
import kotlin.math.*

private const val DEBUG_PARAMS = false

abstract class Param(val tag: String) {
    private val dependencies = ArrayList<Dependency>(2)
    private var updated: UpdateType = TargetValue

    /**
     * Loads params. [update] is called from leaf to the root of all affected params.
     */
    abstract fun loadFrom(parsed: ParsedParam, update: (Param) -> Unit)
    abstract fun isDefault(): Boolean
    abstract fun valueToString(): String

    override fun toString(): String =
        when {
            isDefault() -> ""
            tag.isEmpty() -> valueToString()
            else -> "$tag(${valueToString()})"
        }

    /**
     * Notifies about update:
     * * [TargetValue] update is propagated to all dependencies, recomputes derived values.
     * * All other updates are noted, will recompute all dependencies during the next animation frame
     *   from [performUpdate] call.
     */
    fun notifyUpdated(update: UpdateType) {
        val newUpdated = updated + update
        // Always propagate TargetValue updates to all dependencies
        val targetValueUpdate = update.intersect(TargetValue)
        val delta = (newUpdated - updated) + targetValueUpdate
        if (delta == None) return
        if (DEBUG_PARAMS) {
            println("${this::class.simpleName}[$tag].notifyUpdated: $updated + $delta -> $newUpdated")
        }
        updated = newUpdated
        if (targetValueUpdate != None) {
            computeDerivedTargetValues()
        }
        for (dependency in dependencies) {
            dependency.notifyUpdated(delta)
        }
    }

    // params can perform additional computation on TargetValue updates
    open fun computeDerivedTargetValues() {}

    open fun performUpdate(source: Any?, dt: Double) {
        // Update itself (if needed)
        val curUpdated = updated
        if (curUpdated == None) return
        if (DEBUG_PARAMS) {
            println("${this::class.simpleName}[$tag].performUpdate(dt=${dt.fmt}): $curUpdated -> None")
        }
        updated = None
        update(curUpdated, dt)
        // Update dependencies
        for (d in dependencies) if (d != source) {
            d.performUpdate(this, dt)
        }
    }

    protected open fun update(update: UpdateType, dt: Double) {}

    // Adds ad-hock listener to immediately react to update notification
    fun onNotifyUpdated(type: UpdateType, listener: () -> Unit): Dependency =
        object : Dependency {
            override fun notifyUpdated(update: UpdateType) {
                if (type.intersect(update) != None) listener()
            }

            override fun performUpdate(source: Any?, dt: Double) {}

            init {
                dependencies += this
            }

            override fun destroy() {
                dependencies -= this
            }
        }

    // todo: does not really work for params yet
    open fun destroy() {}

    // ----------- Nested classes and interfaces -----------

    inline class UpdateType(private val mask: Int) {
        fun intersect(other: UpdateType) = UpdateType(mask and other.mask)
        operator fun plus(other: UpdateType) = UpdateType(mask or other.mask)
        operator fun minus(other: UpdateType) = UpdateType(mask and other.mask.inv())
        override fun toString(): String = buildList {
            if (intersect(TargetValue) != None) add("TargetValue")
            if (intersect(AnimatedValue) != None) add("AnimatedValue")
            if (intersect(ActiveAnimation) != None) add("ActiveAnimation")
        }.joinToString("+")
    }

    companion object {
        val None = UpdateType(0)

        /**
         * IMPORTANT:
         * * Updates for [TargetValue] are distributed EAGERLY to all dependencies, derived values are
         *   computed immediately during [Param.notifyUpdated] processing.
         * * All other kinds of updates are conflated during propagation. Derived values are computed during the
         *   next animation frame.
         */
        val TargetValue = UpdateType(1) // param target value changed, save state, update controls, update deps, redraw
        val LoadedValue = UpdateType(2) // param was (re)loaded, need to recompute everything
        val AnimatedValue = UpdateType(4) // param value changed due to animation, update deps, redraw
        val ActiveAnimation = UpdateType(8) // param has an active animation, keep updating it
        val Progress = UpdateType(16) // computation progress was updated

        val AnyUpdate = UpdateType(31)
    }

    interface Dependency {
        fun notifyUpdated(update: UpdateType)
        fun performUpdate(source: Any?, dt: Double)
        fun destroy()
    }

    abstract class Context(private val tracksUpdateType: UpdateType = TargetValue + AnimatedValue) : Dependency {
        abstract val params: Param
        private var updated: UpdateType = tracksUpdateType // needs update on the first opportunity

        override fun notifyUpdated(update: UpdateType) {
            val newUpdated = (updated + update).intersect(tracksUpdateType)
            if (DEBUG_PARAMS) {
                println("${this::class.simpleName}.notifyUpdated: $updated -> $newUpdated")
            }
            updated = newUpdated
        }

        override fun performUpdate(source: Any?, dt: Double) {
            if (updated == None) return
            if (DEBUG_PARAMS) {
                println("${this::class.simpleName}.performUpdate: $updated -> None")
            }
            updated = None
            update()
        }

        abstract fun update()

        protected fun setup() {
            params.dependencies += this
        }

        override fun destroy() {
            params.dependencies -= this
        }
    }

    abstract class Composite(
        tag: String,
    ) : Param(tag), Dependency {
        private val params = ArrayList<Param>(2)
        private val tagMap by lazy { params.associateBy { it.tag } }

        override fun performUpdate(source: Any?, dt: Double) {
            // update all children params first
            for (p in params) if (p != source) {
                p.performUpdate(this, dt)
            }
            // then update self and other dependencies
            super.performUpdate(source, dt)
        }

        override fun destroy() {
            for (param in params) param.dependencies -= this
        }

        fun <T : Param> using(param: T): T {
            params += param
            param.dependencies += this
            return param
        }

        override fun loadFrom(parsed: ParsedParam, update: (Param) -> Unit) {
            if (parsed !is ParsedParam.Composite) return
            val defaultParam = tagMap[""]
            for ((k, v) in parsed.map) {
                val param = tagMap[k]
                if (param == null) {
                    defaultParam?.loadFrom(parsed, update)
                    continue
                }
                param.loadFrom(v, update)
            }
            update(this)
        }

        override fun isDefault(): Boolean = params.all { it.isDefault() }
        override fun valueToString(): String = params.joinToString("")
    }
}

abstract class ValueParam<T : Any>(tag: String, value: T) : Param(tag) {
    private val defaultValue: T = value

    open val value: T
        get() = targetValue

    abstract var targetValue: T
        protected set

    abstract fun updateValue(value: T, updateType: UpdateType? = null)

    override fun isDefault(): Boolean = value == defaultValue

    override fun valueToString(): String = value.toString()

    override fun loadFrom(parsed: ParsedParam, update: (Param) -> Unit) {
        if (parsed !is ParsedParam.Value) return
        parseValue(parsed.value)?.let { targetValue = it }
        update(this)
    }

    abstract fun parseValue(value: String): T?
}

abstract class ImmutableValueParam<T : Any>(tag: String, value: T) : ValueParam<T>(tag, value) {
    override var targetValue: T = value

    override fun updateValue(value: T, updateType: UpdateType?) {
        if (targetValue == value) return
        targetValue = value
        notifyUpdated(updateType ?: TargetValue)
    }
}

interface ValueAnimationParams {
    val animateValueUpdatesDuration: Double?
}

abstract class AnimatedValueParam<T : Any, P : AnimatedValueParam<T, P>>(
    tag: String,
    value: T,
    private val valueAnimationParams: ValueAnimationParams?
) : ValueParam<T>(tag, value) {
    private var valueUpdateAnimation: ValueUpdateAnimation<T, P>? = null

    override val value: T
        get() = valueUpdateAnimation?.value ?: targetValue

    fun resetValueUpdateAnimation() {
        valueUpdateAnimation = null
    }

    override fun updateValue(value: T, updateType: UpdateType?) {
        if (targetValue == value) return
        val oldValue = this.value
        targetValue = value
        if (updateType != null) {
            // explicit update type turns off animation
            resetValueUpdateAnimation()
            notifyUpdated(updateType)
            return
        }
        val newAnimation = valueAnimationParams?.animateValueUpdatesDuration
            ?.let { createValueUpdateAnimation(it, oldValue) }
            ?.also { valueUpdateAnimation = it }
        notifyUpdated(if (newAnimation != null) TargetValue + ActiveAnimation else TargetValue)
    }

    override fun update(update: UpdateType, dt: Double) {
        valueUpdateAnimation?.let { animation ->
            animation.update(dt)
            if (animation.isOver) {
                valueUpdateAnimation = null
                notifyUpdated(AnimatedValue)
            } else {
                notifyUpdated(AnimatedValue + ActiveAnimation)
            }
        }
    }

    abstract fun createValueUpdateAnimation(duration: Double, oldValue: T): ValueUpdateAnimation<T, P>
}

class BooleanParam(
    tag: String,
    value: Boolean
) : ImmutableValueParam<Boolean>(tag, value) {
    fun toggle() {
        updateValue(!value)
    }

    override fun valueToString(): String = if (value) "y" else "n"

    override fun parseValue(value: String): Boolean? = when (value) {
        "y" -> true
        "n" -> false
        else -> null
    }
}

class EnumParam<T : Tagged>(
    tag: String,
    value: T,
    val options: List<T>
) : ImmutableValueParam<T>(tag, value) {
    override fun valueToString(): String = value.tag
    override fun parseValue(value: String): T? = options.find { it.tag == value }
}

class EnumListParam<T : Tagged>(
    tag: String,
    value: List<T>,
    val options: List<T>
) : ImmutableValueParam<List<T>>(tag, value) {
    override fun valueToString(): String = value.joinToString(",") { it.tag }
    override fun parseValue(value: String): List<T> = value.split(",").mapNotNull { element ->
        options.find { it.tag == element }
    }
}

class DoubleParam(
    tag: String,
    value: Double,
    val min: Double,
    val max: Double,
    val step: Double,
    valueAnimationParams: ValueAnimationParams? = null
) : AnimatedValueParam<Double, DoubleParam>(tag, value, valueAnimationParams) {
    override var targetValue: Double = value
    override fun updateValue(value: Double, updateType: UpdateType?) {
        // round the value to step and then coerce into range
        val r = floor(value / step + 0.5) * step
        super.updateValue(r.coerceIn(min, max), updateType)
    }

    override fun createValueUpdateAnimation(duration: Double, oldValue: Double): DoubleUpdateAnimation =
        DoubleUpdateAnimation(this, duration, oldValue)

    override fun valueToString(): String =
        value.fmt

    override fun parseValue(value: String): Double? =
        value.toDoubleOrNull()
}

interface RotationAnimationParams {
    val animatedRotation: BooleanParam
    val animatedRotationAngles: Vec3
}

class RotationParam(
    tag: String,
    value: Quat,
    valueAnimationParams: ValueAnimationParams? = null,
    private val rotationAnimationParams: RotationAnimationParams? = null
) : AnimatedValueParam<Quat, RotationParam>(tag, value, valueAnimationParams) {
    private val _quat = MutableQuat()
    private var rotationAnimation: RotationAnimation? = null

    private val rotationDep = rotationAnimationParams?.animatedRotation?.apply {
        updateAnimation(rotationAnimationParams)
        onNotifyUpdated(TargetValue + LoadedValue) {
            updateAnimation(rotationAnimationParams)
        }
    }

    override fun destroy() {
        rotationDep?.destroy()
    }

    private fun updateAnimation(rotationAnimationParams: RotationAnimationParams) {
        val rotate = rotationAnimationParams.animatedRotation.value
        val rotationAnimation = rotationAnimation
        when {
            rotate && rotationAnimation == null -> {
                this.rotationAnimation = RotationAnimation(this, rotationAnimationParams)
                resetValueUpdateAnimation()
                notifyUpdated(ActiveAnimation)
            }
            !rotate && rotationAnimation != null -> {
                this.rotationAnimation = null
            }
        }
    }

    override fun update(update: UpdateType, dt: Double) {
        super.update(update, dt)
        rotationAnimation?.let { animation ->
            animation.update(dt)
            notifyUpdated(AnimatedValue + ActiveAnimation)
        }
    }

    override var targetValue: Quat
        get() = _quat.copy()
        set(value) { _quat by value }

    fun rotate(x: Double, y: Double, z: Double, updateType: UpdateType) {
        rotate(anglesToQuat(x, y, z), updateType)
    }

    fun rotate(q: Quat, updateType: UpdateType) {
        _quat.multiplyFront(q)
        notifyUpdated(updateType)
    }

    override fun createValueUpdateAnimation(duration: Double, oldValue: Quat): RotationUpdateAnimation =
        RotationUpdateAnimation(this, duration, oldValue)

    override fun valueToString(): String =
        value.toAngles().toList().joinToString(",") {
            (180 * it / PI).fmt(1)
        }

    override fun parseValue(value: String): Quat? =
        value.split(",").mapNotNull { s ->
            s.toDoubleOrNull()?.let { (PI * it) / 180 }
        }.toVec3OrNull()?.anglesToQuat()
}

private fun Vec3.toList() = listOf(x, y, z)
private fun List<Double>.toVec3OrNull() = if (size < 3) null else Vec3(get(0), get(1), get(2))
