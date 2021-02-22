package polyhedra.js.components

import polyhedra.js.params.*
import react.*

external interface PValueComponentProps<V : ValueParam<*>> : PComponentProps<V> {
    var disabled: Boolean
}

external interface PValueComponentState<T> : RState {
    var value: T
}

abstract class PValueComponent<T, V : ValueParam<T>, P : PValueComponentProps<V>, S : PValueComponentState<T>>(
    props: P
) : PComponent<V, P, S>(props) {
    override fun S.init(props: P) {
        value = props.param.value
    }
}

