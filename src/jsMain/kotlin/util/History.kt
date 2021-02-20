@file:JsModule("history")
@file:JsNonModule
package polyhedra.js.util

external interface HashHistory {
    val location: Location
    fun push(path: String)
}

external interface Location {
    val pathname: String
}

external fun createHashHistory(): HashHistory
