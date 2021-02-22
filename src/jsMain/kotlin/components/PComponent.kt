package polyhedra.js.components

import polyhedra.js.params.*
import react.*

external interface PComponentProps<V : Param> : RProps {
    var param: V
}

abstract class PComponent<V : Param, P : PComponentProps<V>, S : RState>(props: P) : RComponent<P, S>(props) {
    private lateinit var context: Param.Context

    abstract override fun S.init(props: P)

    final override fun componentDidMount() {
        context = props.param.onUpdate { setState { init(props) } }
    }

    final override fun componentWillUnmount() {
        context.destroy()
    }
}