package polyhedra.js.params

import polyhedra.common.*

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

    protected fun updated() {
        for (context in contexts) context.update()
    }

    fun onUpdate(update: () -> Unit): Context =
        object : Context() {
            override val params: Param get() = this@Param
            override fun update() { update() }
            init { setup() }
        }

    abstract class Context {
        abstract val params: Param

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

        abstract fun update()
    }

    abstract class Composite(tag: String) : Param(tag) {
        private val params = ArrayList<Param>(2)
        private val tagMap by lazy { params.associateBy { it.tag } }

        private val context = object : Param.Context() {
            override val params: Param
                get() = this@Composite

            override fun update() {
                updated()
            }

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
    
    var value: T = value
        set(value: T) {
            if (field == value) return
            field = value
            updated()
        }

    override fun loadFrom(parsed: ParsedParam) {
        if (parsed !is ParsedParam.Value) return
        parseValue(parsed.value)?.let { value = it }
    }

    override fun isDefault(): Boolean = value == defaultValue
    override fun valueToString(): String = value.toString()

    abstract fun parseValue(value: String): T?
}

class BooleanParam(
    tag: String,
    value: Boolean
) : ValueParam<Boolean>(tag, value) {
    fun toggle() {
        value = !value
    }

    override fun valueToString(): String = if (value) "y" else "n"

    override fun parseValue(value: String): Boolean? = when(value) {
        "y" -> true
        "n" -> false
        else -> null
    }
}

class DoubleParam(
    tag: String,
    value: Double,
    val min: Double,
    val max: Double,
    val step: Double
) : ValueParam<Double>(tag, value) {
    override fun valueToString(): String = value.fmt
    override fun parseValue(value: String): Double? = value.toDoubleOrNull()
}

class EnumParam<T : Tagged>(
    tag: String,
    value: T,
    val options: List<T>
) : ValueParam<T>(tag, value) {
    override fun valueToString(): String = value.tag
    override fun parseValue(value: String): T? = options.find { it.tag == value }
}

class EnumListParam<T : Tagged>(
    tag: String,
    value: List<T>,
    val options: List<T>
) : ValueParam<List<T>>(tag, value) {
    override fun valueToString(): String = value.joinToString(",") { it.tag }
    override fun parseValue(value: String): List<T> = value.split(",").mapNotNull { element ->
        options.find { it.tag == element }
    }
}

