package polyhedra.js

import kotlinx.html.*
import kotlinx.html.js.*
import polyhedra.common.*
import polyhedra.common.util.*
import polyhedra.js.components.*
import polyhedra.js.params.*
import polyhedra.js.poly.*
import react.*
import react.dom.*

external interface RootPaneProps : RProps {
    var params: RootParams
}

external interface RootPaneState : RState {
    var seed: Seed
    var transforms: List<Transform>
    var baseScale: Scale
    var display: Display
    var rotate: Boolean
}

fun RootPaneState.poly(): Polyhedron =
    seed.poly.transformed(transforms).scaled(baseScale)

fun RootPaneState.polyName(): String =
    transforms.reversed().joinToString("") { "$it " } + seed

private inline fun ifValidSeedTransforms(seed: Seed, transforms: List<Transform>, block: () -> Unit) {
    try {
        seed.poly.transformed(transforms).validateGeometry()
        block()
    } catch (e: Exception) {
        println("$seed $transforms is not valid: $e")
        e.printStackTrace()
    }
}

private fun RootPaneState.safeSeedUpdate(newSeed: Seed) {
    ifValidSeedTransforms(newSeed, transforms) {
        seed = newSeed
    }
}

private fun RootPaneState.safeTransformsUpdate(update: (List<Transform>) -> List<Transform>) {
    val newTransforms = update(transforms)
    ifValidSeedTransforms(seed, newTransforms) {
        transforms = newTransforms
    }
}

@Suppress("NON_EXPORTABLE_TYPE")
@JsExport
class RootPane(props: RootPaneProps) : RComponent<RootPaneProps, RootPaneState>(props) {
    private lateinit var context: Param.Context

    override fun RootPaneState.init(props: RootPaneProps) {
        seed = Seed.Tetrahedron
        transforms = emptyList()
        setStateFromProps(props)
    }

    private fun RootPaneState.setStateFromProps(props: RootPaneProps) {
        baseScale = props.params.baseScale.value
        display = props.params.poly.view.display.value
        rotate = props.params.poly.animation.rotate.value
    }

    override fun componentDidMount() {
        context = props.params.onUpdate {
            setState { setStateFromProps(props) }
        }
    }

    override fun componentWillUnmount() {
        context.destroy()
    }

    override fun RBuilder.render() {
        div("main-layout") {
            div("control-column card") {
                renderControls()
            }
            div("canvas-column card") {
                // Canvas & Info
                val curPoly = state.poly()
                header(state.polyName())
                polyCanvas("poly") {
                    params = props.params.poly
                    poly = curPoly
                }
                polyInfoPane {
                    poly = curPoly
                }
            }
        }
    }

    private fun RBuilder.header(text: String) {
        div("header-container") {
            div("header") { +text }
        }
    }

    private fun RBuilder.renderControls() {
        header("Polyhedron")
        div("row control") {
            label { +"Seed" }
            dropdown<Seed> {
                value = state.seed
                options = Seeds
                onChange = { setState { safeSeedUpdate(it) } }
            }
        }

        header("Transforms")
        tableBody {
            for ((i, transform) in state.transforms.withIndex()) {
                tr("control") {
                    td { +"${i + 1}:" }
                    td {
                        dropdown<Transform> {
                            value = transform
                            options = Transforms
                            onChange = { value ->
                                setState {
                                    if (value != Transform.None) {
                                        safeTransformsUpdate { it.updatedAt(i, value) }
                                    } else {
                                        safeTransformsUpdate { it.removedAt(i) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            tr("control") {
                td { +"${state.transforms.size + 1}:" }
                td {
                    dropdown<Transform> {
                        value = Transform.None
                        options = Transforms
                        onChange = { value ->
                            setState {
                                if (value != Transform.None) {
                                    safeTransformsUpdate { it + value }
                                }
                            }
                        }
                    }
                }
            }
            for (i in state.transforms.size + 1..8) {
                tr("control") {
                    td { +"${i + 1}:" }
                    td {}
                }
            }
            tr {
                td {}
                td {
                    button {
                        attrs {
                            onClickFunction = { setState { transforms = emptyList() } }
                        }
                        +"Clear"
                    }
                }
            }
        }

        header("View")
        tableBody {
            tr("control") {
                td { +"Base scale" }
                td {
                    pDropdown<Scale> {
                        param = props.params.baseScale
                    }
                }
            }
            tr("control") {
                td { +"View scale" }
                td {
                    pSlider {
                        param = props.params.poly.view.scale
                    }
                }
            }
            tr("control") {
                td { +"Expand" }
                td {
                    pSlider {
                        param = props.params.poly.view.expand
                    }
                }
            }
            tr("control") {
                td { +"Transparent" }
                td {
                    pSlider {
                        disabled = !state.display.hasFaces()
                        param = props.params.poly.view.transparent
                    }
                }
            }
            tr("control") {
                td { +"Display" }
                td {
                    pDropdown<Display> {
                        param = props.params.poly.view.display
                    }
                }
            }
            tr("control") {
                td { +"Rotate" }
                td {
                    pSlider {
                        disabled = !state.rotate
                        param = props.params.poly.animation.rotationAngle
                    }
                    pCheckbox {
                        param = props.params.poly.animation.rotate
                    }
                }
            }
        }

        header("Lighting")
        tableBody {
            tr("control") {
                td { +"Ambient" }
                td {
                    pSlider {
                        disabled = !state.display.hasFaces()
                        param = props.params.poly.lighting.ambientLight
                    }
                }
            }
            tr("control") {
                td { +"Point" }
                td {
                    pSlider {
                        disabled = !state.display.hasFaces()
                        param = props.params.poly.lighting.pointLight
                    }
                }
            }
            tr("control") {
                td { +"Specular" }
                td {
                    pSlider {
                        disabled = !state.display.hasFaces()
                        param = props.params.poly.lighting.specularLight
                    }
                }
            }
            tr("control") {
                td { +"Shininess" }
                td {
                    pSlider {
                        disabled = !state.display.hasFaces()
                        param = props.params.poly.lighting.specularPower
                    }
                }
            }
        }
    }
}

fun RBuilder.tableBody(block: RDOMBuilder<TBODY>.() -> Unit) {
    table {
        tbody(block = block)
    }
}
