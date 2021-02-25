package polyhedra.js.params

import polyhedra.common.*
import polyhedra.common.util.*

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
        Value(1),
        Animation(2),
        Both(3);

        fun intersects(other: UpdateType) = mask and other.mask != 0
    }

    protected fun visitAffectedContexts(update: UpdateType, visitor: (Context) -> Unit) {
        // Notify contexts in the reverse order.
        // This is important for composites: local context will get notified & updated first, and the
        // outer (containing) context will be notified last, when all local ones were already updated.
        for (i in contexts.size - 1 downTo 0) {
            contexts[i].visitAffectedContexts(update, visitor)
        }
    }

    protected fun notifyUpdate(update: UpdateType, newAnimation: Animation? = null) {
        visitAffectedContexts(update) {
            if (newAnimation != null) it.newAnimation(newAnimation)
            it.update()
        }
    }

    fun onUpdate(type: UpdateType, listener: () -> Unit): Context =
        object : Context(type) {
            override val params: Param get() = this@Param
            override fun update() { listener() }
            init { setup() }
        }

    abstract class Context(private val type: UpdateType = UpdateType.Both) {
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

        open fun newAnimation(newAnimation: Animation) {}

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
    val defaultValue: T = value

    abstract var value: T
        protected set

    abstract fun updateValue(value: T)

    override fun isDefault(): Boolean = value == defaultValue

    override fun valueToString(): String = value.toString()

    override fun loadFrom(parsed: ParsedParam) {
        if (parsed !is ParsedParam.Value) return
        parseValue(parsed.value)?.let { value = it }
    }

    abstract fun parseValue(value: String): T?
}

abstract class ImmutableValueParam<T : Any>(tag: String, value: T) : ValueParam<T>(tag, value) {
    override var value: T = value
        protected set

    override fun updateValue(value: T) {
        if (this.value == value) return
        this.value = value
        notifyUpdate(UpdateType.Value)
    }
}

abstract class AnimatedValueParam<T : Any, P : AnimatedValueParam<T, P>>(
    tag: String,
    value: T,
    val animationParams: AnimationParams?
) : ValueParam<T>(tag, value) {
    var animation: ValueAnimation<T, P>? = null
        private set

    val animatedValue: T
        get() = animation?.animatedValue ?: value

    fun resetAnimation() {
        animation = null
    }

    fun notifyAnimationUpdate() {
        // :todo: move efficient impl for multiple animations
        notifyUpdate(UpdateType.Animation)
    }

    override fun updateValue(value: T) {
        if (this.value == value) return
        val oldValue = animatedValue
        this.value = value
        val newAnimation = animationParams
            ?.takeIf { it.animateUpdates.value }
            ?.let { createAnimation(it.animationDuration.value, oldValue) }
            ?.also { animation = it }
        notifyUpdate(UpdateType.Value, newAnimation)
    }

    abstract fun createAnimation(duration: Double, oldValue: T): ValueAnimation<T, P>
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
    animationParams: AnimationParams? = null
) : AnimatedValueParam<Double, DoubleParam>(tag, value, animationParams) {
    override var value: Double = value
        protected set
    override fun createAnimation(duration: Double, oldValue: Double): DoubleAnimation =
        DoubleAnimation(this, duration, oldValue)
    override fun valueToString(): String =
        value.fmt
    override fun parseValue(value: String): Double? =
        value.toDoubleOrNull()
}

class RotationParam(
    tag: String,
    value: Quat,
    animationParams: AnimationParams? = null
) : AnimatedValueParam<Quat, RotationParam>(tag, value, animationParams) {
    private val _quat = MutableQuat()

    override var value: Quat
        get() = _quat.copy()
        set(value) { _quat by value }

    override fun createAnimation(duration: Double, oldValue: Quat): RotationAnimation =
        RotationAnimation(this, duration, oldValue)
    override fun valueToString(): String =
        value.toAngles().toList().joinToString(",")
    override fun parseValue(value: String): Quat? =
        value.split(",").mapNotNull { it.toDoubleOrNull() }.toVec3OrNull()?.anglesToQuat()
}

private fun Vec3.toList() = listOf(x, y, z)
private fun List<Double>.toVec3OrNull() = if (size < 3) null else Vec3(get(0), get(1), get(2))
