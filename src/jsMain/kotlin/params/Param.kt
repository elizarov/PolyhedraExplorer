package polyhedra.js.params

import polyhedra.common.*
import polyhedra.common.util.*
import kotlin.math.*

abstract class Param(val tag: String) {
    private val dependencies = ArrayList<Dependency>(2)

    abstract fun loadFrom(parsed: ParsedParam, update: (Param) -> Unit)
    abstract fun isDefault(): Boolean
    abstract fun valueToString(): String
    
    override fun toString(): String =
        when {
            isDefault() -> ""
            tag.isEmpty() -> valueToString()
            else -> "$tag(${valueToString()})"
        }

    open fun visitActiveAnimations(visitor: (Animation) -> Unit) {}

    open fun visitAffectedDependencies(update: UpdateType, visitor: (Dependency) -> Unit) {
        // Notify dependencies in the reverse order.
        // This is important for composites: local context will get notified & updated first, and the
        // outer (containing) context will be notified last, when all local ones were already updated.
        for (i in dependencies.size - 1 downTo 0) {
            dependencies[i].visitAffectedDependencies(update, visitor)
        }
    }

    protected fun notifyUpdate(update: UpdateType) {
        visitAffectedDependencies(update) {
            it.update()
        }
    }

    fun onUpdate(type: UpdateType, listener: () -> Unit): Dependency =
        object : Context(type) {
            override val params: Param get() = this@Param
            override fun update() { listener() }
            init { setup() }
        }

    // todo: does not really work for params
    open fun destroy() {}

    // ----------- Nested classes and interfaces -----------

    enum class UpdateType(private val mask: Int) {
        None(0),
        ValueUpdate(1),
        ValueAnimation(2),
        ValueUpdateOrAnimation(3),
        AnimationEffects(4),
        ValueUpdateAndAnimationEffects(5);

        fun intersects(other: UpdateType) = mask and other.mask != 0
    }

    interface Dependency {
        fun visitAffectedDependencies(update: UpdateType, visitor: (Dependency) -> Unit)
        fun update()
        fun destroy()
    }

    abstract class Context(private val tracksUpdateType: UpdateType = UpdateType.ValueUpdateOrAnimation) : Dependency {
        abstract val params: Param

        override fun visitAffectedDependencies(update: UpdateType, visitor: (Dependency) -> Unit) {
            if (tracksUpdateType.intersects(update)) visitor(this)
        }

        protected fun setupAndUpdate() {
            setup()
            update()
        }

        protected fun setup() {
            params.dependencies += this
        }

        override fun destroy() {
            params.dependencies -= this
        }

        override fun update() {}
    }

    abstract class Composite(
        tag: String,
        private val tracksUpdateType: UpdateType = UpdateType.None
    ) : Param(tag), Dependency {
        private val params = ArrayList<Param>(2)
        private val tagMap by lazy { params.associateBy { it.tag } }

        override fun visitAffectedDependencies(update: UpdateType, visitor: (Dependency) -> Unit) {
            super.visitAffectedDependencies(update, visitor)
            if (tracksUpdateType.intersects(update)) visitor(this)
        }

        override fun update() {}

        override fun destroy() {
            for (param in params) param.dependencies -= this
        }

        fun <T : Param> using(param: T): T {
            params += param
            param.dependencies += this
            return param
        }

        override fun visitActiveAnimations(visitor: (Animation) -> Unit) {
            params.forEach { it.visitActiveAnimations(visitor) }
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

    abstract fun updateValue(value: T)

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

    override fun updateValue(value: T) {
        if (targetValue == value) return
        targetValue = value
        notifyUpdate(UpdateType.ValueUpdate)
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

    override fun visitActiveAnimations(visitor: (Animation) -> Unit) {
        valueUpdateAnimation?.let {
            if (it.isOver)
                valueUpdateAnimation = null else    
                visitor(it)
        }
    }

    override fun updateValue(value: T) {
        if (targetValue == value) return
        val oldValue = this.value
        targetValue = value
        val newAnimation = valueAnimationParams?.animateValueUpdatesDuration
            ?.let { createValueUpdateAnimation(it, oldValue) }
            ?.also { valueUpdateAnimation = it }
        notifyUpdate(
            if (newAnimation != null)
                UpdateType.ValueUpdateAndAnimationEffects else
                UpdateType.ValueUpdate
        )
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

    override fun parseValue(value: String): Boolean? = when(value) {
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

    private val rotationDep = rotationAnimationParams?.animatedRotation?.onUpdate(type = UpdateType.ValueUpdate) {
        if (updateAnimation(rotationAnimationParams)) {
            notifyUpdate(UpdateType.AnimationEffects)
        }
    }

    override fun destroy() {
        rotationDep?.destroy()
    }

    private fun updateAnimation(rotationAnimationParams: RotationAnimationParams): Boolean {
        val rotate = rotationAnimationParams.animatedRotation.value
        val rotationAnimation = rotationAnimation
        return when {
            rotate && rotationAnimation == null -> {
                this.rotationAnimation = RotationAnimation(this, rotationAnimationParams)
                resetValueUpdateAnimation()
                true
            }
            !rotate && rotationAnimation != null -> {
                rotationAnimation.isOver = true
                this.rotationAnimation = null
                false
            }
            else -> false
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
        if (updateType != UpdateType.None) notifyUpdate(updateType)
    }

    override fun visitActiveAnimations(visitor: (Animation) -> Unit) {
        rotationAnimationParams?.let { updateAnimation(it) }
        super.visitActiveAnimations(visitor)
        rotationAnimation?.let { visitor(it) }
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
