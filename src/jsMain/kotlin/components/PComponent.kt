/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.components

import polyhedra.js.params.*
import react.*

external interface PComponentProps<V : Param> : RProps {
    var params: V
}

abstract class PComponent<V : Param, P : PComponentProps<V>, S : RState>(
    props: P,
    private val tracksUpdateType: Param.UpdateType = Param.TargetValue
) : RComponent<P, S>(props) {
    private lateinit var dependency: Param.Dependency

    abstract override fun S.init(props: P)

    final override fun componentDidMount() {
        dependency = props.params.onNotifyUpdated(tracksUpdateType) { setState { init(props) } }
    }

    final override fun componentWillUnmount() {
        dependency.destroy()
    }
}