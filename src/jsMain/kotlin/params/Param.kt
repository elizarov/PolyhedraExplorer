package polyhedra.js.params

import polyhedra.common.*
import polyhedra.common.util.*
import kotlin.math.*

abstract class Param(val tag: String) {
    private val contexts = ArrayList<Context>(2)

    abstract fun loadFrom(parsed: ParsedParam)
    abstract fun isDefault(): Boolean
    abstract fun valueToString(): String
    
    override fun toString(): String =
        when {
            isDefault() -> ""
            tag.isEmpty() -> valueToString()
            else -> "$tag(${valueToString()})"
        }

    enum class UpdateType(private val mask: Int) {
        None(0),
        ValueUpdate(1),
        ValueAnimation(2),
        ValueUpdateOrAnimation(3),
        AnimationEffects(4),
        ValueUpdateAndAnimationEffects(5);

        fun intersects(other: UpdateType) = mask and other.mask != 0
    }

    open fun visitActiveAnimations(visitor: (Param, Animation) -> Unit) {}

    fun visitAffectedContexts(update: UpdateType, visitor: (Context) -> Unit) {
        // Notify contexts in the reverse order.
        // This is important for composites: local context will get notified & updated first, and the
        // outer (containing) context will be notified last, when all local ones were already updated.
        for (i in contexts.size - 1 downTo 0) {
            contexts[i].visitAffectedContexts(update, visitor)
        }
    }

    protected fun notifyUpdate(update: UpdateType) {
        visitAffectedContexts(update) {
            it.update()
        }
    }

    fun onUpdate(type: UpdateType, listener: () -> Unit): Context =
        object : Context(type) {
            override val params: Param get() = this@Param
            override fun update() { listener() }
            init { setup() }
        }

    abstract class Context(private val type: UpdateType = UpdateType.ValueUpdateOrAnimation) {
        abstract val params: Param

        open fun visitAffectedContexts(update: UpdateType, visitor: (Context) -> Unit) {
            if (type.intersects(update)) visitor(this)
        }

        protected fun setupAndUpdate() {
            setup()
            update()
        }

        protected fun setup() {
            params.contexts += this
        }

        open fun destroy() {
            params.contexts -= this
        }

        open fun update() {}
    }

    abstract class Composite(tag: String) : Param(tag) {
        private val params = ArrayList<Param>(2)
        private val tagMap by lazy { params.associateBy { it.tag } }

        private val context = object : Param.Context(UpdateType.None) {
            override val params: Param
                get() = this@Composite

            override fun visitAffectedContexts(update: UpdateType, visitor: (Context) -> Unit) {
                this@Composite.visitAffectedContexts(update, visitor)
            }

            override fun update() { error("Should not be called") }

            override fun destroy() {
                super.destroy()
                for (param in this@Composite.params) param.contexts -= this
            }
        }

        fun <T : Param> using(param: T): T {
            params += param
            param.contexts += context
            return param
        }

        override fun visitActiveAnimations(visitor: (Param, Animation) -> Unit) {
            params.forEach { it.visitActiveAnimations(visitor) }
        }

        override fun loadFrom(parsed: ParsedParam) {
            if (parsed !is ParsedParam.Composite) return
            val defaultParam = tagMap[""]
            for ((k, v) in parsed.map) {
                val param = tagMap[k]
                if (param == null) {
                    defaultParam?.loadFrom(parsed)
                    continue
                }
                param.loadFrom(v)
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

    override fun loadFrom(parsed: ParsedParam) {
        if (parsed !is ParsedParam.Value) return
        parseValue(parsed.value)?.let { targetValue = it }
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

    override fun visitActiveAnimations(visitor: (Param, Animation) -> Unit) {
        valueUpdateAnimation?.let {
            if (it.isOver)
                valueUpdateAnimation = null else    
                visitor(this, it)
        }
    }

    fun notifyAnimationUpdate() {
        // :todo: move efficient impl for multiple animations
        notifyUpdate(UpdateType.ValueAnimation)
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

    // todo: destroy it when dynamic params are supported
    private val context = rotationAnimationParams?.animatedRotation?.onUpdate(type = UpdateType.ValueUpdate) {
        if (updateAnimation(rotationAnimationParams)) {
            notifyUpdate(UpdateType.AnimationEffects)
        }
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

    override fun visitActiveAnimations(visitor: (Param, Animation) -> Unit) {
        rotationAnimationParams?.let { updateAnimation(it) }
        super.visitActiveAnimations(visitor)
        rotationAnimation?.let { visitor(this, it) }
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
