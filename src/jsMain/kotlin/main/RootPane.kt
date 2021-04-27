/*
 * Copyright 2021 Roman Elizarov. Use of this source code is governed by the Apache 2.0 license.
 */

package polyhedra.js.main

import kotlinx.html.js.*
import polyhedra.js.components.*
import polyhedra.js.params.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

external interface RootPaneState : RState {
    var popup: Popup?
    var faces: FaceContext?
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane(props: PComponentProps<RootParams>) : RComponent<PComponentProps<RootParams>, RootPaneState>(props) {
    override fun RootPaneState.init(props: PComponentProps<RootParams>) {
        popup = null
        faces = null
    }

    private inner class Context(params: RootParams) : Param.Context(params, Param.TargetValue + Param.Progress) {
        val poly by { params.render.poly.poly }

        init { setup() }

        override fun update() {
            forceUpdate()
        }
    }

    private val ctx = Context(props.params)

    override fun RBuilder.render() {
        polyCanvas("poly") {
            params = props.params.render
            poly = ctx.poly
            faceContextSink = { setState { faces = it } }
            resetPopup = ::resetPopup
        }
        controlPane {
            params = props.params.render.poly
            popup = state.popup
            togglePopup = ::togglePopup
        }
        polyInfo {
            params = props.params.render
            popup = state.popup
            togglePopup = ::togglePopup
        }
        div("btn config") {
            button(classes = "square") {
                attrs { onClickFunction = { togglePopup(Popup.Config) } }
                i("fa fa-cog") {}
            }
        }
        if (state.popup != Popup.Config) {
            div("btn export") {
                button(classes = "square") {
                    attrs { onClickFunction = { togglePopup(Popup.Export) } }
                    i("fa fa-print") {}
                }
            }
        }
        when (state.popup) {
            Popup.Config -> {
                aside("drawer config") {
                    configPopup(props.params)
                }
            }
            Popup.Export -> {
                aside("drawer export") {
                    exportPopup(props.params, state.faces)
                }
            }
        }
    }

    private fun togglePopup(popup: Popup?) {
        setPopup(if(state.popup == popup) null else popup)
    }

    private fun resetPopup() {
        setPopup(null)
    }

    private fun setPopup(popup: Popup?) {
        if (state.popup != popup) setState { this.popup = popup }
    }
}
