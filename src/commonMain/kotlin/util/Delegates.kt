package polyhedra.common.util

import kotlin.reflect.*

class DelegateProvider<R>(val factory: (name: String) -> R) {
    operator fun provideDelegate(thisRef: Any?, prop: KProperty<*>): R = factory(prop.name)
}

class ValueDelegate<R>(val value: R) {
    operator fun getValue(thisRef: Any?, prop: KProperty<*>): R = value
}
