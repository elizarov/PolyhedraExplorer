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

enum class RootPanePopup { NONE, CONFIG, EXPORT }

external interface RootPaneState : RState {
    var popup: RootPanePopup
    var faces: FaceContext?
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane2(props: PComponentProps<RootParams>) : RComponent<PComponentProps<RootParams>, RootPaneState>(props) {
    override fun RootPaneState.init(props: PComponentProps<RootParams>) {
        popup = RootPanePopup.NONE
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
        }
        controlPane(props.params.render.poly)
        div("btn config") {
            button(classes = "square") {
                attrs { onClickFunction = { togglePopup(RootPanePopup.CONFIG) } }
                i("fa fa-cog") {}
            }
        }
        if (state.popup != RootPanePopup.CONFIG) {
            div("btn export") {
                button(classes = "square") {
                    attrs { onClickFunction = { togglePopup(RootPanePopup.EXPORT) } }
                    i("fa fa-print") {}
                }
            }
        }
        when (state.popup) {
            RootPanePopup.NONE -> {}
            RootPanePopup.CONFIG -> {
                aside("popup config") {
                    configPopup(props.params)
                }
            }
            RootPanePopup.EXPORT -> {
                aside("popup export") {
                    exportPopup(props.params, state.faces)
                }
            }
        }
    }

    private fun togglePopup(popup: RootPanePopup) {
        setState {
            this.popup = if (this.popup == popup) RootPanePopup.NONE else popup
        }
    }
}
