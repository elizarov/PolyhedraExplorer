package polyhedra.js.components

import polyhedra.js.params.*
import react.*

external interface PComponentProps<V> : RProps {
    var disabled: Boolean
    var param: V
}

external interface PComponentState<T> : RState {
    var value: T
}

abstract class PComponent<T, V : ValueParam<T>, P : PComponentProps<V>, S : PComponentState<T>>(props: P) : RComponent<P, S>(props) {
    private lateinit var context: Param.Context

    override fun S.init(props: P) {
        value = props.param.value
    }

    override fun componentDidMount() {
        context = props.param.onUpdate { setState { value = props.param.value } }
    }

    override fun componentWillUnmount() {
        context.destroy()
    }
}

