package polyhedra.js

import react.dom.render
import kotlinx.browser.document
import kotlinx.browser.window
import polyhedra.js.util.*

fun main() {
//    kotlinext.js.require("./css/style.css")
    window.onload = {
        val rootParams = RootParams()
        rootParams.onUpdate { pushRootParams(rootParams) }
        render(document.getElementById("root")) {
            child(RootPane::class) {
                attrs {
                    param = rootParams
                }
            }
        }
    }
}

private val history = createHashHistory()

//fun loadRootState(): RootPaneState {
//    val str = history.location.pathname.substringAfter('/', "")
//    return URLDecoder(str).decodeSerializableValue(RootPaneData.serializer())
//}

private fun pushRootParams(state: RootParams) {
    // todo: throttle
    history.push("/$state")
}


